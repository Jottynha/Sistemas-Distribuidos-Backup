<%@page import="java.io.ObjectOutputStream"%>
<%@page import="java.net.Socket"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>JSP Page</title>
    </head>
    <body>
        <% //ScriptLet
            /*out.print("<br><br>Nickname Sessão: "+ session.getAttribute("Nickname")
            +"<br>Cor Sessão: " + session.getAttribute("Cor") + "<br>Avatar Sessão: "
            + session.getAttribute("Avatar"));*/
            if(request.getParameter("BtnEnviar")!=null){
                String msg = "";
                msg += "<img src=\"" + session.getAttribute("Avatar") + "\" width=\"30\" height=\"30\" >";
                msg += "<font size=\"+1\" color\"" + session.getAttribute("Cor") + "\"><b>" + session.getAttribute("Nickname") + "</b></font>";
                msg += "  Diz:  " + request.getParameter("TxtMsg");
                if(!request.getParameter("CBEmoji").equals("")){
                    msg += "<img src=\"" + request.getParameter("CBEmoji") + "\" width=\"30\" height=\"30\" >";
                }
                msg += "<br>";
                Socket client = new Socket("127.0.0.1",6662);
                ObjectOutputStream writer = new ObjectOutputStream(client.getOutputStream());
                writer.flush();
                writer.writeUTF(msg);
                writer.close();
                client.close();
            }
        
        %>
        <form name="FrmConversa" method="post" action="conversa.jsp">
            <label for="TxtMsg"> Mensagem: </label>
            <input type="text" name="TxtMsg" size="200" maxlength="150">
            <label for="CBEmoji"> Emoji: </label>
            <select name="CBEmoji">
                <option value="">Selecione</option>
                <option value="/images/sorriso.png">Sorriso</option>
                <option value="/images/risada.png">Risada</option>
                <option value="/images/coracao.png">Coração</option>
                <input type="submit" name="BtnEnviar" value="Enviar">
                
                
            </select>
            
        </form>
    </body>
</html>
