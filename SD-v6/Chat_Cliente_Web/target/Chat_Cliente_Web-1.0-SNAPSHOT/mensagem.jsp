<%@page import="java.io.ObjectInputStream"%>
<%@page import="java.net.Socket"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <meta http-equiv="refresh" content="5;mensagem.jsp">
        <title>JSP Page</title>
    </head>
    <body>
        <%
          Socket client = new Socket("127.0.0.1",6661);
          ObjectInputStream reader = new ObjectInputStream(client.getInputStream());
          String conversa = reader.readUTF();
          reader.close();
          client.close();
          out.print(conversa);
        %>
    </body>
</html>
