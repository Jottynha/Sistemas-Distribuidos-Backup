
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

    // Recupera os dados enviados pelo formulario.
    String nickname = request.getParameter("TxtNickname");

    String cor = request.getParameter("RadCor");

    String avatar = request.getParameter("RadAvatar");

    // Valida o nickname.
    if (nickname == null || nickname.isBlank()
            || nickname.trim().length() > 20) {

        response.sendRedirect("index.jsp");
        return;
    }

    nickname = nickname.trim();

    // Aceita somente as cores disponibilizadas no login.
    if (!"Navy".equals(cor)
            && !"Tomato".equals(cor)
            && !"MediumSeaGreen".equals(cor)) {

        cor = "Navy";
    }

    // Aceita somente os avatares existentes no servidor.
    if (!"menino.png".equals(avatar)
            && !"menina.png".equals(avatar)
            && !"indefinido.png".equals(avatar)) {

        avatar = "menino.png";
    }

    // Armazena os dados na sessao.
    session.setAttribute("Nickname", nickname);
    session.setAttribute("Cor", cor);
    session.setAttribute("Avatar", avatar);

    String coletor = application.getInitParameter(
            "log.collector.publicUrl"
    );

    if (coletor == null || coletor.isBlank()) {
        coletor = "http://127.0.0.1:8082/api/logs";
    }

%>

<!DOCTYPE html>
<html>

    <head>

        <meta charset="UTF-8">

        <title>Sala de Conversa</title>

    </head>

    <body>

        <!-- Configuracao utilizada pelo JavaScript. -->
        <div id="config"
             data-coletor="<%= escaparHtml(coletor) %>"
             style="display:none;">
        </div>

        <iframe id="frameTopo"
                src="topo.jsp"
                width="100%"
                height="150">
        </iframe>

        <iframe id="frameMensagens"
                src="mensagem.jsp"
                width="100%"
                height="350">
        </iframe>

        <iframe id="frameConversa"
                src="conversa.jsp"
                width="100%"
                height="150">
        </iframe>

        <script>

            const coletor = document
                    .getElementById("config")
                    .dataset.coletor;

            /**
             * Encaminha erros JavaScript ao coletor.
             */
            function reportarErroWeb(classeMetodo, mensagem) {

                const registro = [
                    "web",
                    navigator.userAgent || "Navegador Web",
                    classeMetodo,
                    String(mensagem)
                ].join("\t");

                fetch(coletor, {
                    method: "POST",
                    mode: "cors",
                    keepalive: true,
                    headers: {
                        "Content-Type": "text/plain;charset=UTF-8"
                    },
                    body: registro
                }).catch(function () {
                    // Evita gerar outro erro caso o coletor esteja offline.
                });
            }

            /**
             * Registra erros da propria pagina.
             */
            window.addEventListener("error", function(evento) {

                reportarErroWeb(
                    "chat.jsp:window.error",
                    evento.message
                );

            });

            window.addEventListener(
                    "unhandledrejection",
                    function(evento) {

                        reportarErroWeb(
                            "chat.jsp:unhandledrejection",
                            evento.reason
                        );

                    }
            );

            /**
             * Registra erros ocorridos dentro dos iframes.
             */
            function monitorarFrame(frame) {

                try {

                    frame.contentWindow.addEventListener(
                        "error",
                        function(evento) {

                            reportarErroWeb(
                                frame.id + ":window.error",
                                evento.message
                            );

                        }
                    );

                    frame.contentWindow.addEventListener(
                        "unhandledrejection",
                        function(evento) {

                            reportarErroWeb(
                                frame.id + ":unhandledrejection",
                                evento.reason
                            );

                        }
                    );

                } catch (erro) {

                    reportarErroWeb(
                        "chat.jsp:monitorarFrame",
                        erro
                    );
                }
            }

            document.querySelectorAll("iframe").forEach(
                function(frame) {

                    frame.addEventListener("load", function() {
                        monitorarFrame(frame);
                    });

                }
            );

        </script>

    </body>

</html>
