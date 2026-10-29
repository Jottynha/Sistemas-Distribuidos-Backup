<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>JSP Page</title>
    </head>
    <body>
        <!-- <<img src="images/menino.png" width="50" height="50"/>  -->
        <% //ScriptLet
           /*out.print("<img src=\"images/" + session.getAttribute("Avatar") +"\" "
                   + "width=\"50\" height=\"50\">");*/
        %>
        <img src="images/<%=session.getAttribute("Avatar")%>" width="50" height="50">
        <font size="+2" color="<%=session.getAttribute("Cor")%>">
        <b><%=session.getAttribute("Nickname")%></b>                
        </font>
    </body>
</html>
