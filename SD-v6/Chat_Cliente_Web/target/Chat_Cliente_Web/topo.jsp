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
    String avatarUrl = baseUrl + "/figurinhas/avatars/" + java.net.URLEncoder.encode(avatar, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
%>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
        * { box-sizing: border-box; }
        html, body { height: 100%; margin: 0; }
        body { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 22px; font-family: Inter, "Segoe UI", Arial, sans-serif; background: linear-gradient(110deg, #fff, #f7f8ff); color: #1a2438; }
        .identity { display: flex; align-items: center; gap: 13px; min-width: 0; }
        .avatar { width: 52px; height: 52px; object-fit: contain; border-radius: 16px; border: 1px solid #e8ebf4; background: white; padding: 4px; }
        .welcome { color: #8892a5; font-size: .73rem; font-weight: 700; letter-spacing: .1em; text-transform: uppercase; }
        .nickname { margin-top: 3px; color: <%= cor %>; font-size: 1.15rem; font-weight: 800; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .room { padding: 8px 12px; border-radius: 999px; color: #4e5bd5; background: #eef0ff; font-size: .76rem; font-weight: 750; white-space: nowrap; }
        @media (max-width: 480px) { body { padding: 9px 12px; } .room { display: none; } }
    </style>
</head>
<body>
    <div class="identity">
        <img class="avatar" src="<%= avatarUrl %>" alt="Avatar de <%= nickname.replace("\"", "&quot;") %>">
        <div style="min-width:0">
            <div class="welcome">Você está conectado</div>
            <div class="nickname"><%= nickname.replace("<", "&lt;").replace(">", "&gt;") %></div>
        </div>
    </div>
    <div class="room">● Sala compartilhada</div>
</body>
</html>
