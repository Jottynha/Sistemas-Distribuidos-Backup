<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Chat Distribuído Web 1.0</title>
    </head>
    <body><br><br><br>
        <center>
            <form name="FrmLogin" action="chat.jsp" method="post">
                <table border="1">
                    <tr>
                        <td colspan="2" bgcolor="MediumSeaGreen" align="center"><font color = "white" size = "+3">Chat Distribuído 1.0</font></td>
                    </tr>
                    <tr>
                        <td align="right"><b> Nickname:</b></td>
                        <td><input type="text" name="TxtNickname" maxlength="20" size="100"></td>
                    </tr>
                    <tr>
                        <td align="right"><b> Cor:</b></td>
                        <td>
                            <input type="radio" name="RadCor" value="Navy" checked="1">
                            <font color="Navy"><b>Azul</b></font>
                            <input type="radio" name="RadCor" value="Tomato">
                            <font color="Tomato"><b>Vermelho</b></font>
                            <input type="radio" name="RadCor" value="MediumSeaGreen">
                            <font color="MediumSeaGreen"><b>Verde</b></font>
                        </td>
                    </tr>
                    <tr>
                        <td align="right"><b> Avatar:</b></td>
                        <td>
                            <input type="radio" name="RadAvatar" value="menino.png" checked="1">
                            <img src="images/menino.png" width="30" height="30">
                            <input type="radio" name="RadAvatar" value="menina.png">
                            <img src="images/menina.png" width="30" height="30">
                            <input type="radio" name="RadAvatar" value="indefinido.png">
                            <img src="images/indefinido.png" width="30" height="30">
                        </td>
                    </tr>
                    <tr>
                        <td colspan="2" align="center">
                            <input type="submit" name="BtnEntrar" value="Entrar">
                        </td>
                    </tr>
                </table>
            </form>
        </center>
    </body>
</html>
