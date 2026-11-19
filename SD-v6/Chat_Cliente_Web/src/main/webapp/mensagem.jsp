
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%!
    private String escaparAtributo(String valor) {
        if (valor == null) return "";

        return valor
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "&#39;");
    }
%>
<%
    String nickname = (String) session.getAttribute("Nickname");

    if (nickname == null || nickname.isBlank()) {
        response.sendRedirect("index.jsp");
        return;
    }

    String baseUrl = application.getInitParameter(
            "middleware.publicBaseUrl"
    );

    if (baseUrl == null || baseUrl.isBlank()) {
        baseUrl = "http://127.0.0.1:8081";
    }

    baseUrl = baseUrl.replaceAll("/+$", "");

    String middlewareSeguro = escaparAtributo(baseUrl);
    String nicknameSeguro = escaparAtributo(nickname);
%>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">

    <style>
        * { box-sizing: border-box; }

        html, body {
            min-height: 100%;
            margin: 0;
        }

        body {
            padding: 17px 21px;
            color: #283349;
            font-family: Inter, "Segoe UI", Arial, sans-serif;
            background: #fff;
        }

        .history-head {
            display: flex;
            justify-content: space-between;
            align-items: center;
            gap: 12px;
            margin: 0 0 13px;
        }

        .history-head strong {
            font-size: .9rem;
        }

        .history-head span {
            color: #8993a6;
            font-size: .72rem;
        }

        #conversa {
            min-height: 120px;
            line-height: 1.85;
            overflow-wrap: anywhere;
            font-size: .92rem;
        }

        #conversa img {
            max-width: 84px;
            max-height: 84px;
            object-fit: contain;
            vertical-align: middle;
            margin: 2px 5px;
            border-radius: 10px;
        }

        #conversa br {
            display: block;
            content: "";
            margin-bottom: 9px;
        }

        .connection {
            color: #9aa3b3;
            font-size: .84rem;
            padding: 10px 0;
        }
    </style>
</head>

<body>

    <div id="historyConfig"
         hidden
         data-middleware="<%= middlewareSeguro %>"
         data-nickname="<%= nicknameSeguro %>">
    </div>

    <div class="history-head">
        <strong>Conversa</strong>
        <span id="connectionStatus">Atualizando em tempo real</span>
    </div>

    <div id="conversa" class="connection">
        Carregando mensagens...
    </div>

    <script>
        const configuracao = document.getElementById("historyConfig");

        const middleware = configuracao.dataset.middleware;
        const nickname = configuracao.dataset.nickname;

        const conversa = document.getElementById("conversa");
        const status = document.getElementById("connectionStatus");

        let consultando = false;
        let ultimoHistorico = null;

        function gerarIdMensagem() {

            if (window.crypto
                    && typeof window.crypto.randomUUID === "function") {
                return window.crypto.randomUUID();
            }

            return "web-history-" + Date.now()
                    + "-" + Math.random().toString(16).slice(2);
        }

        function ajustarUrlsImagens() {

            const imagens = conversa.querySelectorAll(
                    'img[src^="/figurinhas/"]'
            );

            imagens.forEach(imagem => {

                const caminho = imagem.getAttribute("src");

                imagem.src = middleware + caminho;
            });
        }

        async function atualizarConversa() {

            if (consultando) return;

            consultando = true;

            const requisicao = {
                protocolVersion: 1,
                id: gerarIdMensagem(),
                correlationId: null,
                type: "HISTORY_REQUEST",
                sender: nickname,
                recipient: "Todos",
                content: "",
                mode: "",
                color: "",
                avatar: "",
                sticker: "",
                sentAt: new Date().toISOString(),
                metadata: {
                    clientType: "web"
                }
            };

            try {

                const resposta = await fetch(
                        middleware + "/api/messages",
                        {
                            method: "POST",
                            headers: {
                                "Content-Type": "application/json; charset=UTF-8",
                                "Accept": "application/json"
                            },
                            body: JSON.stringify(requisicao),
                            cache: "no-store"
                        }
                );

                const dados = await resposta.json();

                const statusResposta = dados.metadata
                        ? dados.metadata.status
                        : null;

                if (!resposta.ok
                        || dados.type !== "HISTORY_RESPONSE"
                        || dados.correlationId !== requisicao.id
                        || statusResposta !== "SUCCESS") {

                    throw new Error(
                            dados.content
                            || "Nao foi possivel consultar o historico."
                    );
                }

                const historico = dados.content || "";

                if (historico !== ultimoHistorico) {

                    conversa.innerHTML = historico;

                    ajustarUrlsImagens();

                    ultimoHistorico = historico;

                    conversa.scrollTop = conversa.scrollHeight;
                }

                conversa.classList.remove("connection");

                status.textContent =
                        "Conectado · atualização automática";

            } catch (erro) {

                status.textContent = "Reconectando…";

                if (!ultimoHistorico) {
                    conversa.textContent =
                            "Não foi possível carregar as mensagens. "
                            + "Verifique se o Middleware está ativo.";
                }

                if (window.parent
                        && window.parent.reportarErroWeb) {

                    window.parent.reportarErroWeb(
                            "mensagem.jsp:atualizarConversa",
                            erro.message
                    );
                }

            } finally {

                consultando = false;
            }
        }

        atualizarConversa();

        setInterval(atualizarConversa, 1800);
    </script>

</body>
</html>
