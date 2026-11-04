
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

    // Valida o avatar.
    if (!"menino.png".equals(avatar)
            && !"menina.png".equals(avatar)
            && !"indefinido.png".equals(avatar)) {

        avatar = "menino.png";
    }

    // Valida a cor.
    if (!"Navy".equals(cor)
            && !"Tomato".equals(cor)
            && !"MediumSeaGreen".equals(cor)) {

        cor = "Navy";
    }

    String baseUrl = application.getInitParameter(
            "middleware.publicBaseUrl"
    );

    if (baseUrl == null || baseUrl.isBlank()) {
        baseUrl = "http://127.0.0.1:8081";
    }

    baseUrl = baseUrl.replaceAll("/+$", "");

    String avatarUrl = baseUrl
            + "/figurinhas/avatars/"
            + avatar;

%>

<!DOCTYPE html>
<html>

    <head>

        <meta charset="UTF-8">

        <title>Identificacao</title>

    </head>

    <body>

        <img src="<%= escaparHtml(avatarUrl) %>"
             width="50"
             height="50"
             alt="Avatar">

        <font size="+2"
              color="<%= cor %>">

            <b><%= escaparHtml(nickname) %></b>

        </font>

    </body>

</html>
