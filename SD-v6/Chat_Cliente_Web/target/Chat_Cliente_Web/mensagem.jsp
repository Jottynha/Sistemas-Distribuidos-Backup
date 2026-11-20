
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@ include file="/WEB-INF/jspf/middleware-config.jspf" %>
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

    String nicknameSeguro = escaparAtributo(nickname);
    String websocketPath = request.getContextPath() + "/ws/publicidade";
    String websocketSeguro = escaparAtributo(websocketPath);
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
        .advertisement {
            width: 100%;
            min-height: 86px;
            margin: 0 0 13px;
            padding: 7px;
            display: flex;
            align-items: center;
            justify-content: center;
            border: 1px solid #e8edf5;
            border-radius: 12px;
            background: #f8faff;
            overflow: hidden;
        }
        .advertisement img {
            display: block;
            max-width: 100%;
            max-height: 190px;
            object-fit: contain;
            border-radius: 8px;
        }
        .advertisement span { color: #8993a6; font-size: .75rem; }
    </style>
</head>

<body>

    <div id="middlewareConfig" hidden
         data-primary="<%= middlewarePrimarySafe %>"
         data-backup="<%= middlewareBackupSafe %>"
         data-websocket="<%= websocketSeguro %>"
         data-failover-wait="<%= failoverWaitMs %>"></div>
    <div id="historyConfig" hidden data-nickname="<%= nicknameSeguro %>"></div>

    <section class="advertisement" aria-label="Publicidade sincronizada">
        <span id="advertisementStatus">Aguardando publicidade sincronizada...</span>
        <img id="advertisementImage" alt="Publicidade" hidden>
    </section>

    <div class="history-head">
        <strong>Conversa</strong>
        <span id="connectionStatus">Atualizando em tempo real</span>
    </div>

    <div id="conversa" class="connection">
        Carregando mensagens...
    </div>

    <script src="<%= request.getContextPath() %>/js/middleware-client.js"></script>
    <script>
        const configuracao = document.getElementById("historyConfig");
        const middlewareConfig = document.getElementById("middlewareConfig");
        const middlewareClient = window.createMiddlewareClient(middlewareConfig);
        const nickname = configuracao.dataset.nickname;

        const conversa = document.getElementById("conversa");
        const status = document.getElementById("connectionStatus");

        let consultando = false;
        let ultimoHistorico = null;
        let urlPublicidadeAtual = null;

        function gerarIdMensagem() {

            if (window.crypto
                    && typeof window.crypto.randomUUID === "function") {
                return window.crypto.randomUUID();
            }

            return "web-history-" + Date.now()
                    + "-" + Math.random().toString(16).slice(2);
        }

        function ajustarUrlsImagens() {
            // Mantem a URL relativa original para poder trocar de middleware
            // quando o failover atualizar middlewareClient.base().
            conversa.querySelectorAll("img").forEach(imagem => {
                let caminho = imagem.dataset.middlewareResource || "";
                const origem = imagem.getAttribute("src") || "";

                if (!caminho && origem.startsWith("/figurinhas/")) {
                    caminho = origem;
                    imagem.dataset.middlewareResource = caminho;
                }

                if (caminho.startsWith("/figurinhas/")) {
                    const destino = middlewareClient.base() + caminho;
                    if (imagem.src !== new URL(destino, window.location.href).href) {
                        imagem.src = destino;
                    }
                }
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

                const resposta = await middlewareClient.fetch(
                        "/api/messages",
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
                    ultimoHistorico = historico;
                    conversa.scrollTop = conversa.scrollHeight;
                }

                // Tambem atualiza os recursos quando o historico nao mudou,
                // pois o endpoint ativo pode ter mudado durante o failover.
                ajustarUrlsImagens();
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

        function iniciarPublicidadeWebSocket() {
            const caminho = middlewareConfig.dataset.websocket;
            const endereco = new URL(caminho, window.location.href);
            endereco.protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
            let reconexao = null;
            let socket = null;

            function conectar() {
                try {
                    socket = new WebSocket(endereco.toString());
                    socket.onopen = () => {
                        document.getElementById("advertisementStatus").textContent = "Publicidade sincronizada";
                    };
                    socket.onmessage = async evento => {
                        try {
                            const aviso = JSON.parse(evento.data);
                            if (aviso.type !== "ADVERTISEMENT_UPDATE") return;
                            const nome = aviso.content || "";
                            if (!/^[A-Za-z0-9._-]{1,180}\.(png|gif|jpg|jpeg|webp)$/i.test(nome) || nome.includes("..")) return;
                            const imagem = document.getElementById("advertisementImage");
                            const resposta = await middlewareClient.fetch(
                                    "/publicidade/" + encodeURIComponent(nome) + "?v=" + Date.now(),
                                    { method: "GET" }
                            );
                            if (!resposta.ok) {
                                throw new Error("HTTP " + resposta.status + " ao baixar a publicidade.");
                            }

                            const arquivo = await resposta.blob();
                            const novaUrl = URL.createObjectURL(arquivo);
                            const urlAnterior = urlPublicidadeAtual;
                            urlPublicidadeAtual = novaUrl;
                            imagem.hidden = false;
                            imagem.onerror = () => {
                                document.getElementById("advertisementStatus").textContent = "Nao foi possivel exibir a publicidade.";
                                if (urlPublicidadeAtual === novaUrl) {
                                    URL.revokeObjectURL(novaUrl);
                                    urlPublicidadeAtual = null;
                                }
                            };
                            imagem.onload = () => {
                                document.getElementById("advertisementStatus").textContent = "Publicidade: " + nome;
                                if (urlAnterior) URL.revokeObjectURL(urlAnterior);
                            };
                            imagem.src = novaUrl;
                        } catch (erro) {
                            console.warn("Aviso de publicidade invalido:", erro);
                        }
                    };
                    socket.onclose = () => {
                        document.getElementById("advertisementStatus").textContent = "Reconectando ao gateway de publicidade...";
                        clearTimeout(reconexao);
                        reconexao = setTimeout(conectar, 3000);
                    };
                    socket.onerror = () => {
                        try { socket.close(); } catch (erro) { }
                    };
                } catch (erro) {
                    clearTimeout(reconexao);
                    reconexao = setTimeout(conectar, 3000);
                }
            }
            conectar();
            window.addEventListener("beforeunload", () => {
                clearTimeout(reconexao);
                if (socket && socket.readyState <= WebSocket.OPEN) socket.close();
                if (urlPublicidadeAtual) URL.revokeObjectURL(urlPublicidadeAtual);
            });
        }

        iniciarPublicidadeWebSocket();
        atualizarConversa();
        setInterval(atualizarConversa, 1800);
    </script>

</body>
</html>
