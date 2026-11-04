<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.net.URLEncoder"%>
<%! 
    private String escaparHtml(String valor) {
        if (valor == null) return "";
        return valor.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
%>
<%
    String nickname = request.getParameter("TxtNickname");
    String cor = request.getParameter("RadCor");
    String avatar = request.getParameter("RadAvatar");

    if (nickname == null || nickname.isBlank() || nickname.trim().length() > 20) {
        response.sendRedirect("index.jsp");
        return;
    }
    nickname = nickname.trim();

    if (!"Navy".equals(cor) && !"Tomato".equals(cor) && !"MediumSeaGreen".equals(cor)) {
        cor = "Navy";
    }
    if (avatar == null || !avatar.matches("[a-zA-Z0-9._-]+\\.(png|gif|jpg|jpeg|webp)") || avatar.contains("..")) {
        avatar = "menino.png";
    }

    session.setAttribute("Nickname", nickname);
    session.setAttribute("Cor", cor);
    session.setAttribute("Avatar", avatar);

    String coletor = application.getInitParameter("log.collector.publicUrl");
    if (coletor == null || coletor.isBlank()) coletor = "http://127.0.0.1:8082/api/logs";
%>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Chat Distribuído</title>
    <style>
        * { box-sizing: border-box; }
        html, body { width: 100%; height: 100%; margin: 0; }
        body {
            padding: 14px;
            overflow: hidden;
            color: #1a2438;
            font-family: Inter, "Segoe UI", Arial, sans-serif;
            background: #eef1f8;
        }
        .chat-layout {
            width: min(1180px, 100%);
            height: calc(100vh - 28px);
            margin: 0 auto;
            display: grid;
            grid-template-rows: 82px minmax(180px, 1fr) 330px;
            gap: 12px;
        }
        iframe {
            display: block;
            width: 100%;
            border: 1px solid #e4e9f2;
            border-radius: 18px;
            background: white;
            box-shadow: 0 8px 24px rgba(28, 39, 72, .06);
        }
        #frameTopo { height: 82px; }
        #frameMensagens { height: 100%; min-height: 180px; }
        #frameConversa { height: 330px; }
        @media (max-width: 700px) {
            body { padding: 8px; overflow: auto; }
            .chat-layout { height: auto; min-height: calc(100vh - 16px); grid-template-rows: 76px minmax(300px, 1fr) 350px; }
            #frameTopo { height: 76px; }
            #frameMensagens { height: 100%; }
            #frameConversa { height: 350px; }
        }
    </style>
</head>
<body data-log-collector="<%= escaparHtml(coletor) %>">
    <main class="chat-layout">
        <iframe id="frameTopo" title="Identificação do usuário" src="topo.jsp"></iframe>
        <iframe id="frameMensagens" title="Histórico da conversa" src="mensagem.jsp"></iframe>
        <iframe id="frameConversa" title="Enviar mensagem" src="conversa.jsp"></iframe>
    </main>
    <script>
        const coletor = document.body.dataset.logCollector;
        function reportarErroWeb(classeMetodo, mensagem) {
            const registro = ["web", navigator.userAgent || "Navegador Web", classeMetodo, String(mensagem)].join("\t");
            fetch(coletor, {
                method: "POST", mode: "cors", keepalive: true,
                headers: { "Content-Type": "text/plain;charset=UTF-8" },
                body: registro
            }).catch(() => {});
        }
        window.addEventListener("error", evento => reportarErroWeb("chat.jsp:window.error", evento.message));
        window.addEventListener("unhandledrejection", evento => reportarErroWeb("chat.jsp:unhandledrejection", evento.reason));
        ["frameTopo", "frameMensagens", "frameConversa"].forEach(id => {
            const frame = document.getElementById(id);
            frame.addEventListener("load", () => {
                try {
                    frame.contentWindow.addEventListener("error", evento => reportarErroWeb(id + ":window.error", evento.message));
                    frame.contentWindow.addEventListener("unhandledrejection", evento => reportarErroWeb(id + ":unhandledrejection", evento.reason));
                } catch (erro) {
                    reportarErroWeb("chat.jsp:monitorarFrame", erro.message);
                }
            });
        });
    </script>
</body>
</html>
