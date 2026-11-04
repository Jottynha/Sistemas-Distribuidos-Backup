
<%@page contentType="text/html" pageEncoding="UTF-8"%>

<%!

    private String escaparHtml(String valor) {

        if (valor == null) {
            return "";
        }

        return valor
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

%>

<%

    String nickname = (String) session.getAttribute("Nickname");

    String cor = (String) session.getAttribute("Cor");

    String avatar = (String) session.getAttribute("Avatar");

    if (nickname == null) {
        response.sendRedirect("index.jsp");
        return;
    }

    if (!"Navy".equals(cor)
            && !"Tomato".equals(cor)
            && !"MediumSeaGreen".equals(cor)) {

        cor = "Navy";
    }

    if (!"menino.png".equals(avatar)
            && !"menina.png".equals(avatar)
            && !"indefinido.png".equals(avatar)) {

        avatar = "menino.png";
    }

    String baseUrl = application.getInitParameter(
            "middleware.publicBaseUrl"
    );

    if (baseUrl == null || baseUrl.isBlank()) {
        baseUrl = "http://127.0.0.1:8081";
    }

    baseUrl = baseUrl.replaceAll("/+$", "");

%>

<!DOCTYPE html>
<html>

    <head>

        <meta charset="UTF-8">

        <title>Enviar Mensagem</title>

    </head>

    <body>

        <form id="FrmConversa"
        data-nickname="<%= escaparHtml(nickname) %>"
        data-cor="<%= cor %>"
        data-avatar="<%= avatar %>"
        data-middleware="<%= escaparHtml(baseUrl) %>"
        onsubmit="enviarMensagem(event)">

            <label for="TxtMsg">
                Mensagem:
            </label>

            <input type="text"
                   id="TxtMsg"
                   name="TxtMsg"
                   size="100"
                   maxlength="150"
                   autocomplete="off"
                   required>

            <label for="CBEmoji">
                Figurinha:
            </label>

            <select id="CBEmoji" name="CBEmoji">

                <option value="">
                    Selecione
                </option>

                <option value="sorriso.png">
                    Sorriso
                </option>

                <option value="risada.png">
                    Risada
                </option>

                <option value="coracao.png">
                    Coracao
                </option>

            </select>

            <input type="submit"
                   id="BtnEnviar"
                   value="Enviar">

        </form>

        <p id="StatusEnvio"></p>

        <script>

            const formulario =
                    document.getElementById("FrmConversa");

            const campoMensagem =
                    document.getElementById("TxtMsg");

            const campoEmoji =
                    document.getElementById("CBEmoji");

            const botaoEnviar =
                    document.getElementById("BtnEnviar");

            const statusEnvio =
                    document.getElementById("StatusEnvio");

            const middleware = formulario.dataset.middleware;
            const nickname = formulario.dataset.nickname;
            const cor = formulario.dataset.cor;
            const avatar = formulario.dataset.avatar;

            /**
             * Escapa o texto digitado antes de inserir no HTML.
             */
            function escaparTexto(texto) {

                return texto
                        .replace(/&/g, "&amp;")
                        .replace(/</g, "&lt;")
                        .replace(/>/g, "&gt;")
                        .replace(/"/g, "&quot;")
                        .replace(/'/g, "&#39;");
            }

            /**
             * Envia a mensagem para o middleware.
             */
            async function enviarMensagem(evento) {

                evento.preventDefault();

                const texto = campoMensagem.value.trim();

                if (!texto) {
                    return;
                }

                botaoEnviar.disabled = true;

                statusEnvio.textContent = "Enviando...";

                try {

                    let mensagem = "";

                    const avatarUrl =
                            middleware
                            + "/figurinhas/avatars/"
                            + encodeURIComponent(avatar);

                    mensagem +=
                            "<img src='"
                            + avatarUrl
                            + "' width='30' height='30'>";

                    mensagem +=
                            "<font size='+1' color='"
                            + cor
                            + "'><b>"
                            + escaparTexto(nickname)
                            + "</b></font>";

                    mensagem +=
                            " Diz: "
                            + escaparTexto(texto);

                    // Adiciona a figurinha selecionada.
                    if (campoEmoji.value !== "") {

                        const stickerUrl =
                                middleware
                                + "/figurinhas/"
                                + encodeURIComponent(
                                        campoEmoji.value
                                );

                        mensagem +=
                                "<img src='"
                                + stickerUrl
                                + "' width='30' height='30'>";
                    }

                    mensagem += "<br>";

                    const resposta = await fetch(
                            middleware + "/api/messages",
                            {
                                method: "POST",

                                headers: {
                                    "Content-Type":
                                        "text/plain;charset=UTF-8"
                                },

                                body: mensagem
                            }
                    );

                    const confirmacao =
                            (await resposta.text()).trim();

                    // O servidor so responde OK depois
                    // que a mensagem e persistida.
                    if (!resposta.ok || confirmacao !== "OK") {

                        throw new Error(
                                confirmacao || "Erro HTTP "
                                + resposta.status
                        );
                    }

                    // Limpa somente depois do ACK positivo.
                    campoMensagem.value = "";

                    campoEmoji.value = "";

                    statusEnvio.textContent =
                            "Mensagem enviada com sucesso.";

                } catch (erro) {

                    statusEnvio.textContent =
                            "Falha no envio da mensagem.";

                    if (window.parent
                            && window.parent.reportarErroWeb) {

                        window.parent.reportarErroWeb(
                                "conversa.jsp:enviarMensagem",
                                erro.message
                        );
                    }

                } finally {

                    botaoEnviar.disabled = false;

                    campoMensagem.focus();
                }
            }
        
        
        /**
         * Carrega automaticamente as figurinhas
         * cadastradas na pasta central do middleware.
         */
        async function carregarFigurinhas() {

            try {

                const resposta = await fetch(
                        middleware + "/api/stickers",
                        { cache: "no-store" }
                );

                if (!resposta.ok) {
                    throw new Error("Erro ao carregar figurinhas");
                }

                const conteudo = await resposta.text();

                const nomes = conteudo
                        .split(/\r?\n/)
                        .map(nome => nome.trim())
                        .filter(nome => nome.length > 0)
                        .filter(nome =>
                            /\.(png|gif|jpg|jpeg|webp)$/i.test(nome)
                        )
                        .filter(nome =>
                            !nome.includes("..")
                            && !nome.includes("/")
                            && !nome.includes("\\")
                        );

                const selecionado = campoEmoji.value;

                campoEmoji.innerHTML = "";

                const opcaoInicial = document.createElement("option");

                opcaoInicial.value = "";
                opcaoInicial.textContent = "Selecione";

                campoEmoji.appendChild(opcaoInicial);

                nomes.forEach(nome => {

                    const opcao = document.createElement("option");

                    opcao.value = nome;
                    opcao.textContent = nome;

                    campoEmoji.appendChild(opcao);
                });

                campoEmoji.value = nomes.includes(selecionado)
                        ? selecionado
                        : "";

            } catch (erro) {

                if (window.parent
                        && window.parent.reportarErroWeb) {

                    window.parent.reportarErroWeb(
                            "conversa.jsp:carregarFigurinhas",
                            erro.message
                    );
                }
            }
        }

        carregarFigurinhas();


        </script>

    </body>

</html>
