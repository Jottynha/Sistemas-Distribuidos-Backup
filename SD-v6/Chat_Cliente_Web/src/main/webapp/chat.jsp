<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Sala de Conversa</title>
    </head>
    <body>
        <% //ScriptLet
            /*out.print("<b>Nickname:</b>"+request.getParameter("TxtNickname")+"<br><b>Cor:</b>"
                    + request.getParameter("RadCor") + "<br><b>Avatar:</b>" 
                    + request.getParameter("RadAvatar"));*/
            session.setAttribute("Nickname", request.getParameter("TxtNickname"));
            session.setAttribute("Cor", request.getParameter("RadCor"));
            session.setAttribute("Avatar", request.getParameter("RadAvatar"));
            /*out.print("<br><br>Nickname Sessão: "+ session.getAttribute("Nickname")
            +"<br>Cor Sessão: " + session.getAttribute("Cor") + "<br>Avatar Sessão: "
            + session.getAttribute("Avatar"));*/
            
        %>
        <iframe src="topo.jsp" width="100%" height="150"></iframe>
        <iframe src="mensagem.jsp" width="100%" height="350"></iframe>
        <iframe src="conversa.jsp" width="100%" height="150"></iframe>
    </body>
</html>
