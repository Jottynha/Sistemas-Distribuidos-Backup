<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%!
    private String escaparHtmlConversa(String valor) {
        if (valor == null) return "";
        return valor.replace("&", "&amp;")
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

    if (nickname == null || nickname.isBlank()) {
        response.sendRedirect("index.jsp");
        return;
    }

    if (!"Navy".equals(cor) && !"Tomato".equals(cor)
            && !"MediumSeaGreen".equals(cor) && !"Blue".equals(cor)) {
        cor = "Navy";
    }
    if (avatar == null
            || !avatar.matches("[a-zA-Z0-9._-]+\\.(png|gif|jpg|jpeg|webp)")
            || avatar.contains("..")) {
        avatar = "menino.png";
    }

    String baseUrl = application.getInitParameter("middleware.publicBaseUrl");
    if (baseUrl == null || baseUrl.isBlank()) {
        baseUrl = "http://127.0.0.1:8081";
    }
    baseUrl = baseUrl.replaceAll("/+$", "");
%>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <title>Enviar mensagem</title>
</head>
<body>
    <form id="FrmConversa"
          data-nickname="<%= escaparHtmlConversa(nickname) %>"
          data-cor="<%= escaparHtmlConversa(cor) %>"
          data-avatar="<%= escaparHtmlConversa(avatar) %>"
          data-middleware="<%= escaparHtmlConversa(baseUrl) %>">

        <label for="TxtMsg">Mensagem:</label>
        <input type="text" id="TxtMsg" name="TxtMsg"
               size="55" maxlength="150" autocomplete="off" required>

        <label for="EmojiPicker">Emoji:</label>
        <select id="EmojiPicker">
            <option value="">Selecionar</option>
        </select>

        <label for="CBEmoji">Figurinha:</label>
        <select id="CBEmoji">
            <option value="">Selecionar</option>
        </select>

        <button type="submit" id="BtnEnviar">Enviar</button>
    </form>
    <p id="StatusEnvio" aria-live="polite"></p>

    <script>
        const formulario = document.getElementById("FrmConversa");
        const campoMensagem = document.getElementById("TxtMsg");
        const seletorEmoji = document.getElementById("EmojiPicker");
        const seletorSticker = document.getElementById("CBEmoji");
        const botaoEnviar = document.getElementById("BtnEnviar");
        const statusEnvio = document.getElementById("StatusEnvio");

        const middleware = formulario.dataset.middleware;
        const nickname = formulario.dataset.nickname;
        const cor = formulario.dataset.cor;
        const avatar = formulario.dataset.avatar;

        function escaparTexto(texto) {
            return texto.replace(/&/g, "&amp;")
                        .replace(/</g, "&lt;")
                        .replace(/>/g, "&gt;")
                        .replace(/\"/g, "&quot;")
                        .replace(/'/g, "&#39;");
        }

        function reportarErro(metodo, erro) {
            if (window.parent && typeof window.parent.reportarErroWeb === "function") {
                window.parent.reportarErroWeb(metodo, erro && erro.message ? erro.message : erro);
            }
        }

        async function carregarCatalogo(endpoint, seletor, textoInicial) {
            const resposta = await fetch(middleware + endpoint, { cache: "no-store" });
            if (!resposta.ok) throw new Error("HTTP " + resposta.status + " em " + endpoint);
            const itens = (await resposta.text()).split(/\r?\n/)
                    .map(item => item.trim())
                    .filter(item => item.length > 0);

            seletor.innerHTML = "";
            const inicial = document.createElement("option");
            inicial.value = "";
            inicial.textContent = textoInicial;
            seletor.appendChild(inicial);

            itens.forEach(item => {
                const opcao = document.createElement("option");
                opcao.value = item;
                opcao.textContent = item;
                seletor.appendChild(opcao);
            });
        }

        async function carregarRecursos() {
            try {
                const resultados = await Promise.all([
                    carregarCatalogo("/api/emojis", seletorEmoji, "Selecionar emoji"),
                    carregarCatalogo("/api/stickers", seletorSticker, "Selecionar figurinha")
                ]);
            } catch (erro) {
                statusEnvio.textContent = "Não foi possível carregar todos os recursos do servidor.";
                reportarErro("conversa.jsp:carregarRecursos", erro);
            }
        }

        seletorEmoji.addEventListener("change", function () {
            const emoji = seletorEmoji.value;
            if (!emoji) return;

            const inicio = campoMensagem.selectionStart;
            const fim = campoMensagem.selectionEnd;
            campoMensagem.setRangeText(emoji, inicio, fim, "end");
            campoMensagem.focus();
            seletorEmoji.value = "";
        });

        formulario.addEventListener("submit", async function (evento) {
            evento.preventDefault();
            const texto = campoMensagem.value.trim();
            if (!texto) return;

            botaoEnviar.disabled = true;
            statusEnvio.textContent = "Enviando...";

            try {
                const avatarUrl = middleware + "/figurinhas/avatars/"
                        + encodeURIComponent(avatar);

                let mensagem = "<img src='" + avatarUrl + "' width='30' height='30'>";
                mensagem += " <font color='" + cor + "'><b>"
                        + escaparTexto(nickname) + "</b></font>: ";
                mensagem += escaparTexto(texto);

                const sticker = seletorSticker.value;
                if (sticker) {
                    const stickerUrl = middleware + "/figurinhas/"
                            + encodeURIComponent(sticker);
                    mensagem += " <img src='" + stickerUrl + "' width='60' height='60'>";
                }
                mensagem += "<br>";

                const resposta = await fetch(middleware + "/api/messages", {
                    method: "POST",
                    headers: { "Content-Type": "text/plain;charset=UTF-8" },
                    body: mensagem
                });
                const confirmacao = (await resposta.text()).trim();
                if (!resposta.ok || confirmacao !== "OK") {
                    throw new Error(confirmacao || "HTTP " + resposta.status);
                }

                campoMensagem.value = "";
                seletorSticker.value = "";
                statusEnvio.textContent = "Mensagem enviada.";
            } catch (erro) {
                statusEnvio.textContent = "Falha ao enviar a mensagem.";
                reportarErro("conversa.jsp:enviarMensagem", erro);
            } finally {
                botaoEnviar.disabled = false;
                campoMensagem.focus();
            }
        });

        carregarRecursos();
    </script>
</body>
</html>
