<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%!
    private String escaparHtmlTopo(String valor) {
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

    // Aceita qualquer nome seguro retornado por /api/avatars.
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

    String avatarUrl = baseUrl + "/figurinhas/avatars/"
            + java.net.URLEncoder.encode(avatar, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");
%>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <title>Identificação</title>
</head>
<body>
    <img src="<%= escaparHtmlTopo(avatarUrl) %>"
         width="50" height="50" alt="Avatar">
    <font size="+2" color="<%= escaparHtmlTopo(cor) %>">
        <b><%= escaparHtmlTopo(nickname) %></b>
    </font>
</body>
</html>
