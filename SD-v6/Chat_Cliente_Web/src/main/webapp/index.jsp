<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%
    String middlewarePublic = application.getInitParameter("middleware.publicBaseUrl");
    if (middlewarePublic == null || middlewarePublic.isBlank()) {
        middlewarePublic = "http://127.0.0.1:8081";
    }
    middlewarePublic = middlewarePublic.replaceAll("/+$", "");
%>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Chat Distribuído | Entrar</title>
    <style>
        :root {
            color-scheme: light;
            --ink: #182235;
            --muted: #718096;
            --line: #e7ecf3;
            --primary: #5b67e8;
            --primary-dark: #4652d5;
            --surface: #ffffff;
            --soft: #f5f7ff;
        }
        * { box-sizing: border-box; }
        body {
            margin: 0;
            min-height: 100vh;
            display: grid;
            place-items: center;
            padding: 28px 16px;
            color: var(--ink);
            font-family: Inter, "Segoe UI", Arial, sans-serif;
            background:
                radial-gradient(circle at 15% 15%, rgba(113, 126, 255, .28), transparent 32%),
                radial-gradient(circle at 85% 80%, rgba(67, 194, 178, .19), transparent 28%),
                linear-gradient(135deg, #10182d, #202b4a 58%, #18233b);
        }
        .login-shell {
            width: min(100%, 920px);
            display: grid;
            grid-template-columns: .9fr 1.1fr;
            overflow: hidden;
            border: 1px solid rgba(255,255,255,.28);
            border-radius: 26px;
            background: rgba(255,255,255,.97);
            box-shadow: 0 28px 90px rgba(0,0,0,.28);
        }
        .intro {
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            min-height: 600px;
            padding: 42px 38px;
            color: white;
            background: linear-gradient(155deg, #5967ed, #3946b8 68%, #28358e);
        }
        .brand-mark {
            display: inline-grid;
            place-items: center;
            width: 48px;
            height: 48px;
            border: 1px solid rgba(255,255,255,.35);
            border-radius: 16px;
            background: rgba(255,255,255,.16);
            font-size: 24px;
        }
        .intro h1 {
            max-width: 360px;
            margin: 26px 0 14px;
            font-size: clamp(2rem, 4vw, 3rem);
            line-height: 1.05;
            letter-spacing: -.04em;
        }
        .intro p { max-width: 330px; color: rgba(255,255,255,.78); line-height: 1.65; }
        .intro-note { color: rgba(255,255,255,.66); font-size: .82rem; }
        .form-panel { padding: 38px clamp(24px, 4vw, 48px); }
        .eyebrow { color: var(--primary); font-size: .75rem; font-weight: 800; letter-spacing: .13em; text-transform: uppercase; }
        .form-panel h2 { margin: 8px 0 6px; font-size: 1.7rem; letter-spacing: -.03em; }
        .description { margin: 0 0 26px; color: var(--muted); font-size: .92rem; line-height: 1.5; }
        .field-label { display: block; margin: 20px 0 9px; font-size: .86rem; font-weight: 750; }
        .nickname {
            width: 100%; height: 48px; padding: 0 14px;
            border: 1px solid var(--line); border-radius: 13px;
            outline: none; font: inherit; color: var(--ink); background: #fbfcff;
            transition: border-color .18s, box-shadow .18s;
        }
        .nickname:focus { border-color: var(--primary); box-shadow: 0 0 0 4px rgba(91,103,232,.12); }
        .color-options { display: flex; flex-wrap: wrap; gap: 9px; }
        .color-option { position: relative; }
        .color-option input { position: absolute; opacity: 0; pointer-events: none; }
        .color-option span {
            display: inline-flex; align-items: center; gap: 8px;
            padding: 9px 12px; border: 1px solid var(--line);
            border-radius: 999px; color: #465168; background: white;
            font-size: .82rem; cursor: pointer;
        }
        .color-dot { width: 11px; height: 11px; border-radius: 50%; }
        .color-option input:checked + span { border-color: var(--primary); background: #f1f2ff; color: var(--primary-dark); box-shadow: 0 0 0 2px rgba(91,103,232,.09); }
        .avatar-heading { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; }
        .avatar-heading small { color: var(--muted); font-size: .75rem; }
        .avatar-picker {
            display: grid; grid-template-columns: repeat(auto-fill, minmax(82px, 1fr));
            gap: 10px; max-height: 190px; overflow: auto; padding: 3px 3px 8px;
        }
        .avatar-card {
            min-width: 0; display: flex; flex-direction: column; align-items: center; gap: 7px;
            padding: 10px 6px; border: 1px solid var(--line); border-radius: 15px;
            background: white; color: #596579; cursor: pointer;
            transition: transform .16s, border-color .16s, background .16s, box-shadow .16s;
        }
        .avatar-card:hover { transform: translateY(-2px); border-color: #b8befb; }
        .avatar-card[aria-pressed="true"] { border-color: var(--primary); background: #f3f4ff; box-shadow: 0 0 0 2px rgba(91,103,232,.12); color: var(--primary-dark); }
        .avatar-card img { width: 48px; height: 48px; object-fit: contain; image-rendering: auto; }
        .avatar-card span { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: .72rem; font-weight: 700; text-transform: capitalize; }
        .avatar-preview {
            display: flex; align-items: center; gap: 13px; margin-top: 12px;
            padding: 11px 13px; border: 1px solid #e9ebfa; border-radius: 15px; background: var(--soft);
        }
        .avatar-preview img { width: 54px; height: 54px; object-fit: contain; border-radius: 13px; background: white; }
        .avatar-preview strong { display: block; font-size: .86rem; }
        .avatar-preview small { color: var(--muted); }
        .submit {
            width: 100%; height: 49px; margin-top: 24px; border: 0; border-radius: 14px;
            color: white; background: linear-gradient(110deg, var(--primary), #7882f7);
            font: inherit; font-weight: 800; cursor: pointer;
            box-shadow: 0 10px 22px rgba(91,103,232,.23);
            transition: transform .16s, box-shadow .16s;
        }
        .submit:hover { transform: translateY(-1px); box-shadow: 0 13px 26px rgba(91,103,232,.3); }
        .submit:disabled { opacity: .65; cursor: wait; }
        .load-note { min-height: 17px; margin-top: 7px; color: var(--muted); font-size: .74rem; }
        @media (max-width: 720px) {
            .login-shell { grid-template-columns: 1fr; }
            .intro { min-height: auto; padding: 25px 26px; }
            .intro h1 { max-width: none; margin: 18px 0 8px; font-size: 2rem; }
            .intro p { margin-bottom: 4px; }
            .intro-note { display: none; }
            .form-panel { padding: 28px 24px; }
        }
    </style>
</head>
<body>
    <main class="login-shell">
        <section class="intro">
            <div>
                <div class="brand-mark">✦</div>
                <h1>Converse, compartilhe e fique por perto.</h1>
                <p>Entre no Chat Distribuído e converse com pessoas conectadas pelo Desktop ou pelo navegador.</p>
            </div>
            <div class="intro-note">Uma conversa, diferentes clientes, um histórico compartilhado.</div>
        </section>

        <section class="form-panel">
            <div class="eyebrow">Bem-vindo</div>
            <h2>Entrar no chat</h2>
            <p class="description">Escolha como você quer aparecer para os outros participantes.</p>

            <form name="FrmLogin" action="chat.jsp" method="post" id="loginForm">
                <label class="field-label" for="TxtNickname">Seu nickname</label>
                <input class="nickname" type="text" id="TxtNickname" name="TxtNickname"
                       maxlength="20" minlength="1" autocomplete="nickname" placeholder="Como podemos chamar você?" required>

                <span class="field-label">Cor do nome</span>
                <div class="color-options">
                    <label class="color-option">
                        <input type="radio" name="RadCor" value="Navy" checked>
                        <span><i class="color-dot" style="background:#243f9b"></i>Azul</span>
                    </label>
                    <label class="color-option">
                        <input type="radio" name="RadCor" value="Tomato">
                        <span><i class="color-dot" style="background:#ed6658"></i>Vermelho</span>
                    </label>
                    <label class="color-option">
                        <input type="radio" name="RadCor" value="MediumSeaGreen">
                        <span><i class="color-dot" style="background:#35a873"></i>Verde</span>
                    </label>
                </div>

                <div class="avatar-heading">
                    <label class="field-label" for="RadAvatar">Escolha seu avatar</label>
                    <small>As imagens vêm do servidor</small>
                </div>
                <input type="hidden" id="RadAvatar" name="RadAvatar" value="menino.png">
                <div id="AvatarGallery" class="avatar-picker" aria-live="polite">
                    <div class="load-note">Carregando avatares...</div>
                </div>
                <div class="avatar-preview">
                    <img id="AvatarPreview" src="<%= middlewarePublic %>/figurinhas/avatars/menino.png" alt="Prévia do avatar">
                    <div>
                        <strong id="AvatarName">menino</strong>
                        <small>Este será seu avatar na conversa</small>
                    </div>
                </div>
                <div id="AvatarStatus" class="load-note" role="status"></div>

                <button class="submit" type="submit">Entrar na conversa <span aria-hidden="true">→</span></button>
            </form>
        </section>
    </main>

    <script>
        const middlewarePublic = "<%= middlewarePublic %>";
        const gallery = document.getElementById("AvatarGallery");
        const avatarField = document.getElementById("RadAvatar");
        const preview = document.getElementById("AvatarPreview");
        const avatarName = document.getElementById("AvatarName");
        const avatarStatus = document.getElementById("AvatarStatus");

        const fallbackAvatars = ["menino.png", "menina.png", "indefinido.png"];

        function avatarUrl(nome) {
            return middlewarePublic + "/figurinhas/avatars/" + encodeURIComponent(nome);
        }

        function selecionarAvatar(nome, botao) {
            avatarField.value = nome;
            preview.src = avatarUrl(nome);
            avatarName.textContent = nome.replace(/\.[^.]+$/, "").replace(/[-_]/g, " ");
            gallery.querySelectorAll(".avatar-card").forEach(card => {
                card.setAttribute("aria-pressed", String(card === botao));
            });
        }

        function renderizarAvatares(nomes) {
            gallery.replaceChildren();
            nomes.forEach((nome, indice) => {
                const botao = document.createElement("button");
                botao.type = "button";
                botao.className = "avatar-card";
                botao.setAttribute("aria-pressed", "false");
                const imagem = document.createElement("img");
                imagem.src = avatarUrl(nome);
                imagem.alt = "Avatar " + nome;
                imagem.loading = "lazy";
                imagem.onerror = () => { imagem.style.opacity = ".25"; };
                const legenda = document.createElement("span");
                legenda.textContent = nome.replace(/\.[^.]+$/, "").replace(/[-_]/g, " ");
                botao.append(imagem, legenda);
                botao.addEventListener("click", () => selecionarAvatar(nome, botao));
                gallery.appendChild(botao);
                if (indice === 0) selecionarAvatar(nome, botao);
            });
        }

        async function carregarAvatares() {
            try {
                const resposta = await fetch(middlewarePublic + "/api/avatars", { cache: "no-store" });
                if (!resposta.ok) throw new Error("HTTP " + resposta.status);
                const nomes = (await resposta.text()).split(/\r?\n/).map(n => n.trim())
                    .filter(n => /^[a-zA-Z0-9._-]+\.(png|gif|jpg|jpeg|webp)$/i.test(n))
                    .filter(n => !n.includes(".."));
                if (!nomes.length) throw new Error("O servidor não retornou avatares.");
                renderizarAvatares(nomes);
                avatarStatus.textContent = nomes.length + " avatares disponíveis.";
            } catch (erro) {
                renderizarAvatares(fallbackAvatars);
                avatarStatus.textContent = "Não foi possível consultar o catálogo; opções padrão exibidas.";
                console.warn("Falha ao carregar avatares:", erro);
            }
        }

        carregarAvatares();
    </script>
</body>
</html>
