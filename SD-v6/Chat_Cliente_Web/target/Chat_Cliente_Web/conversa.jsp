<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%
    String nickname = (String) session.getAttribute("Nickname");
    String cor = (String) session.getAttribute("Cor");
    String avatar = (String) session.getAttribute("Avatar");
    if (nickname == null) { response.sendRedirect("index.jsp"); return; }
    if (!"Navy".equals(cor) && !"Tomato".equals(cor) && !"MediumSeaGreen".equals(cor)) cor = "Navy";
    if (avatar == null || !avatar.matches("[a-zA-Z0-9._-]+\\.(png|gif|jpg|jpeg|webp)") || avatar.contains("..")) avatar = "menino.png";
    String baseUrl = application.getInitParameter("middleware.publicBaseUrl");
    if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://127.0.0.1:8081";
    baseUrl = baseUrl.replaceAll("/+$", "");
    String nicknameSeguro = nickname.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
%>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Enviar mensagem</title>
    <style>
        * { box-sizing: border-box; }
        html, body { margin: 0; min-height: 100%; }
        body { padding: 12px 16px; color: #202b40; font-family: Inter, "Segoe UI", Arial, sans-serif; background: linear-gradient(135deg, #fff, #f8f9ff); }
        .composer { max-width: 1120px; margin: 0 auto; }
        .composer-top { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 8px; }
        .composer-title { font-size: .88rem; font-weight: 800; }
        .composer-subtitle { color: #8993a6; font-size: .72rem; }
        .message-row { display: flex; gap: 9px; align-items: stretch; }
        #TxtMsg { flex: 1; min-width: 0; min-height: 44px; max-height: 64px; resize: none; padding: 12px 14px; border: 1px solid #e3e8f1; border-radius: 13px; outline: none; color: #26334b; background: #fbfcff; font: inherit; font-size: .88rem; }
        #TxtMsg:focus { border-color: #7780ed; box-shadow: 0 0 0 3px rgba(91,103,232,.1); }
        #BtnEnviar { min-width: 112px; padding: 0 17px; border: 0; border-radius: 13px; color: white; background: linear-gradient(110deg, #5b67e8, #7882f7); font: inherit; font-size: .85rem; font-weight: 800; cursor: pointer; }
        #BtnEnviar:hover { filter: brightness(1.04); }
        #BtnEnviar:disabled { opacity: .58; cursor: wait; }
        .tools-row { display: grid; grid-template-columns: 1fr; gap: 7px; margin-top: 9px; }
        .tool-label { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 5px; }
        .tool-label strong { color: #465168; font-size: .72rem; }
        .tool-label small { color: #9aa3b3; font-size: .68rem; }
        .emoji-list { display: flex; gap: 5px; align-items: center; overflow-x: auto; padding: 2px 1px 5px; }
        .emoji-button { flex: 0 0 auto; width: 32px; height: 30px; border: 1px solid #e9edf5; border-radius: 9px; background: white; font-size: 17px; cursor: pointer; }
        .emoji-button:hover { border-color: #9ca4f4; background: #f4f5ff; }
        .sticker-gallery { display: flex; gap: 8px; overflow-x: auto; padding: 2px 1px 7px; min-height: 84px; }
        .sticker-card { flex: 0 0 76px; display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 6px 5px; border: 1px solid #e7ebf3; border-radius: 12px; background: white; color: #6b768b; cursor: pointer; transition: border-color .15s, background .15s, transform .15s; }
        .sticker-card:hover { transform: translateY(-2px); border-color: #aab0f6; }
        .sticker-card[aria-pressed="true"] { border-color: #626ee8; background: #f2f3ff; box-shadow: 0 0 0 2px rgba(91,103,232,.1); }
        .sticker-card img { width: 48px; height: 44px; object-fit: contain; }
        .sticker-card span { max-width: 100%; font-size: .63rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .sticker-bottom { display: flex; align-items: center; justify-content: space-between; gap: 8px; min-height: 54px; }
        .selected-sticker { display: flex; align-items: center; gap: 7px; color: #68748a; font-size: .7rem; }
        .selected-sticker img { width: 52px; height: 52px; object-fit: contain; padding: 2px; border: 1px solid #e9edf5; border-radius: 8px; background: white; }
        .clear-sticker { border: 0; padding: 5px 8px; border-radius: 8px; color: #69758a; background: #f1f3f8; font-size: .68rem; cursor: pointer; }
        #StatusEnvio { min-height: 15px; margin: 4px 0 0; color: #7d879a; font-size: .69rem; }
        .empty-catalog { color: #9aa3b3; font-size: .72rem; padding: 12px 4px; }
        @media (max-width: 560px) { body { padding: 9px; } .message-row { flex-wrap: wrap; } #BtnEnviar { height: 42px; width: 100%; } .composer-subtitle { display: none; } }
    </style>
</head>
<body>
    <main class="composer" id="FrmConversa"
          data-nickname="<%= nicknameSeguro %>"
          data-cor="<%= cor %>"
          data-avatar="<%= avatar %>"
          data-middleware="<%= baseUrl %>">
        <div class="composer-top">
            <div class="composer-title">Sua mensagem</div>
            <div class="composer-subtitle">Emojis e figurinhas são compartilhados com o cliente Desktop</div>
        </div>

        <form id="messageForm">
            <div class="message-row">
                <textarea id="TxtMsg" maxlength="500" rows="2" placeholder="Escreva uma mensagem… (Enter para enviar)" required></textarea>
                <button type="submit" id="BtnEnviar">Enviar <span aria-hidden="true">➤</span></button>
            </div>
        </form>

        <div class="tools-row">
            <section>
                <div class="tool-label"><strong>Emojis</strong><small>Toque para inserir na mensagem</small></div>
                <div id="EmojiGallery" class="emoji-list"><span class="empty-catalog">Carregando emojis…</span></div>
            </section>
            <section>
                <div class="tool-label"><strong>Figurinhas</strong><small>Selecione uma para ver a prévia e anexar</small></div>
                <div id="StickerGallery" class="sticker-gallery"><span class="empty-catalog">Carregando figurinhas…</span></div>
                <div class="sticker-bottom">
                    <div id="SelectedSticker" class="selected-sticker">Nenhuma figurinha selecionada</div>
                    <button type="button" id="ClearSticker" class="clear-sticker" hidden>Remover</button>
                </div>
            </section>
        </div>
        <p id="StatusEnvio" role="status"></p>
    </main>

    <script>
        const composer = document.getElementById("FrmConversa");
        const middleware = composer.dataset.middleware;
        const nickname = composer.dataset.nickname;
        const cor = composer.dataset.cor;
        const avatar = composer.dataset.avatar;
        const form = document.getElementById("messageForm");
        const campoMensagem = document.getElementById("TxtMsg");
        const botaoEnviar = document.getElementById("BtnEnviar");
        const statusEnvio = document.getElementById("StatusEnvio");
        const stickerGallery = document.getElementById("StickerGallery");
        const emojiGallery = document.getElementById("EmojiGallery");
        const selectedStickerBox = document.getElementById("SelectedSticker");
        const clearStickerButton = document.getElementById("ClearSticker");
        let stickerSelecionada = null;

        function escaparTexto(texto) {
            return String(texto).replace(/&/g, "&amp;").replace(/</g, "&lt;")
                    .replace(/>/g, "&gt;").replace(/\"/g, "&quot;").replace(/'/g, "&#39;");
        }

        function urlFigurinha(nome) {
            return middleware + "/figurinhas/" + encodeURIComponent(nome);
        }

        function selecionarFigurinha(nome, botao) {
            stickerSelecionada = nome;
            stickerGallery.querySelectorAll(".sticker-card").forEach(card => {
                card.setAttribute("aria-pressed", String(card === botao));
            });
            selectedStickerBox.replaceChildren();
            const imagem = document.createElement("img");
            imagem.src = urlFigurinha(nome);
            imagem.alt = "Prévia de " + nome;
            const legenda = document.createElement("span");
            legenda.textContent = "Anexada: " + nome;
            selectedStickerBox.append(imagem, legenda);
            clearStickerButton.hidden = false;
        }

        function limparFigurinha() {
            stickerSelecionada = null;
            stickerGallery.querySelectorAll(".sticker-card").forEach(card => card.setAttribute("aria-pressed", "false"));
            selectedStickerBox.textContent = "Nenhuma figurinha selecionada";
            clearStickerButton.hidden = true;
        }
        clearStickerButton.addEventListener("click", limparFigurinha);

        async function carregarFigurinhas() {
            try {
                const resposta = await fetch(middleware + "/api/stickers", { cache: "no-store" });
                if (!resposta.ok) throw new Error("HTTP " + resposta.status);
                const nomes = (await resposta.text()).split(/\r?\n/).map(n => n.trim())
                        .filter(n => /^[a-zA-Z0-9._-]+\.(png|gif|jpg|jpeg|webp)$/i.test(n))
                        .filter(n => !n.includes(".."));
                stickerGallery.replaceChildren();
                if (!nomes.length) {
                    stickerGallery.innerHTML = '<span class="empty-catalog">Nenhuma figurinha disponível.</span>';
                    return;
                }
                nomes.forEach(nome => {
                    const botao = document.createElement("button");
                    botao.type = "button";
                    botao.className = "sticker-card";
                    botao.setAttribute("aria-pressed", "false");
                    const imagem = document.createElement("img");
                    imagem.src = urlFigurinha(nome);
                    imagem.alt = nome;
                    imagem.loading = "lazy";
                    imagem.onerror = () => { imagem.alt = "Imagem indisponível"; imagem.style.opacity = ".25"; };
                    const legenda = document.createElement("span");
                    legenda.textContent = nome.replace(/\.[^.]+$/, "").replace(/[-_]/g, " ");
                    botao.append(imagem, legenda);
                    botao.addEventListener("click", () => selecionarFigurinha(nome, botao));
                    stickerGallery.appendChild(botao);
                });
            } catch (erro) {
                stickerGallery.innerHTML = '<span class="empty-catalog">Não foi possível carregar o catálogo de figurinhas.</span>';
                reportarErro("conversa.jsp:carregarFigurinhas", erro);
            }
        }

        function inserirNoCursor(texto) {
            const inicio = campoMensagem.selectionStart;
            const fim = campoMensagem.selectionEnd;
            campoMensagem.value = campoMensagem.value.substring(0, inicio) + texto + campoMensagem.value.substring(fim);
            campoMensagem.focus();
            const posicao = inicio + texto.length;
            campoMensagem.setSelectionRange(posicao, posicao);
        }

        async function carregarEmojis() {
            try {
                const resposta = await fetch(middleware + "/api/emojis", { cache: "no-store" });
                if (!resposta.ok) throw new Error("HTTP " + resposta.status);
                const emojis = (await resposta.text()).split(/\r?\n/).map(e => e.trim()).filter(Boolean);
                emojiGallery.replaceChildren();
                emojis.forEach(emoji => {
                    const botao = document.createElement("button");
                    botao.type = "button";
                    botao.className = "emoji-button";
                    botao.textContent = emoji;
                    botao.title = "Inserir " + emoji;
                    botao.addEventListener("click", () => inserirNoCursor(emoji));
                    emojiGallery.appendChild(botao);
                });
            } catch (erro) {
                emojiGallery.innerHTML = '<span class="empty-catalog">Emojis indisponíveis.</span>';
                reportarErro("conversa.jsp:carregarEmojis", erro);
            }
        }

        function reportarErro(local, erro) {
            if (window.parent && window.parent.reportarErroWeb) {
                window.parent.reportarErroWeb(local, erro && erro.message ? erro.message : String(erro));
            }
        }

        async function enviarMensagem(evento) {
            evento.preventDefault();
            const texto = campoMensagem.value.trim();
            if (!texto && !stickerSelecionada) return;

            botaoEnviar.disabled = true;
            statusEnvio.textContent = "Enviando…";
            try {
                const avatarUrl = middleware + "/figurinhas/avatars/" + encodeURIComponent(avatar);
                let mensagem = "<img src='" + avatarUrl + "' width='30' height='30' alt='Avatar'> ";
                mensagem += "<font size='+1' color='" + cor + "'><b>" + escaparTexto(nickname) + "</b></font>: ";
                mensagem += escaparTexto(texto);
                if (stickerSelecionada) {
                    mensagem += " <img src='" + urlFigurinha(stickerSelecionada) + "' width='72' height='72' alt='Figurinha'>";
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
                limparFigurinha();
                statusEnvio.textContent = "Mensagem enviada.";
            } catch (erro) {
                statusEnvio.textContent = "Não foi possível enviar. Sua mensagem foi mantida.";
                reportarErro("conversa.jsp:enviarMensagem", erro);
            } finally {
                botaoEnviar.disabled = false;
                campoMensagem.focus();
            }
        }

        form.addEventListener("submit", enviarMensagem);
        campoMensagem.addEventListener("keydown", evento => {
            if (evento.key === "Enter" && !evento.shiftKey) {
                evento.preventDefault();
                form.requestSubmit();
            }
        });

        carregarFigurinhas();
        carregarEmojis();
    </script>
</body>
</html>
