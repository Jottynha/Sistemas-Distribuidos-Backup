
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta charset="UTF-8">
        <title>Chat Distribuido Web 1.0</title>
    </head>

    <body>

        <%
            String baseUrl = application.getInitParameter(
                    "middleware.publicBaseUrl"
            );

            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "http://127.0.0.1:8081";
            }

            baseUrl = baseUrl.replaceAll("/+$", "");

            String pastaAvatares =
                    baseUrl + "/figurinhas/avatars/";
        %>

        <br><br><br>

        <center>

            <form name="FrmLogin"
                  action="chat.jsp"
                  method="post">

                <table border="1">

                    <tr>
                        <td colspan="2"
                            bgcolor="MediumSeaGreen"
                            align="center">

                            <font color="white" size="+3">
                                Chat Distribuido 1.0
                            </font>

                        </td>
                    </tr>

                    <tr>

                        <td align="right">
                            <b>Nickname:</b>
                        </td>

                        <td>
                            <input type="text"
                                   name="TxtNickname"
                                   maxlength="20"
                                   size="40"
                                   required>
                        </td>

                    </tr>

                    <tr>

                        <td align="right">
                            <b>Cor:</b>
                        </td>

                        <td>

                            <input type="radio"
                                   name="RadCor"
                                   value="Navy"
                                   checked>

                            <font color="Navy">
                                <b>Azul</b>
                            </font>

                            <input type="radio"
                                   name="RadCor"
                                   value="Tomato">

                            <font color="Tomato">
                                <b>Vermelho</b>
                            </font>

                            <input type="radio"
                                   name="RadCor"
                                   value="MediumSeaGreen">

                            <font color="MediumSeaGreen">
                                <b>Verde</b>
                            </font>

                        </td>

                    </tr>

                    <tr>

                        <td align="right">
                            <b>Avatar:</b>
                        </td>

                        <td>

                            <label>

                                <input type="radio"
                                       name="RadAvatar"
                                       value="menino.png"
                                       checked>

                                <img src="<%= pastaAvatares %>menino.png"
                                     width="30"
                                     height="30"
                                     alt="Menino">

                            </label>

                            <label>

                                <input type="radio"
                                       name="RadAvatar"
                                       value="menina.png">

                                <img src="<%= pastaAvatares %>menina.png"
                                     width="30"
                                     height="30"
                                     alt="Menina">

                            </label>

                            <label>

                                <input type="radio"
                                       name="RadAvatar"
                                       value="indefinido.png">

                                <img src="<%= pastaAvatares %>indefinido.png"
                                     width="30"
                                     height="30"
                                     alt="Indefinido">

                            </label>

                        </td>

                    </tr>

                    <tr>

                        <td colspan="2" align="center">

                            <input type="submit"
                                   name="BtnEntrar"
                                   value="Entrar">

                        </td>

                    </tr>

                </table>

            </form>

        </center>

    </body>
</html>
