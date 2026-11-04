
<%@page contentType="text/html" pageEncoding="UTF-8"%>

<%

    String baseUrl = application.getInitParameter(
            "middleware.publicBaseUrl"
    );

    if (baseUrl == null || baseUrl.isBlank()) {
        baseUrl = "http://127.0.0.1:8081";
    }

    baseUrl = baseUrl.replaceAll("/+$", "");

    String urlHistorico = baseUrl + "/api/messages";

%>

<!DOCTYPE html>
<html>

    <head>

        <meta charset="UTF-8">

        <title>Historico da Conversa</title>

    </head>

    <body>

        <div id="conversa">
            Carregando historico...
        </div>

        <script>

            const urlHistorico =
                    "<%= urlHistorico %>";

            let consultando = false;

            async function atualizarConversa() {

                if (consultando) {
                    return;
                }

                consultando = true;

                try {

                    const resposta = await fetch(
                            urlHistorico,
                            {
                                method: "GET",
                                cache: "no-store"
                            }
                    );

                    if (!resposta.ok) {

                        throw new Error(
                                "HTTP " + resposta.status
                        );
                    }

                    const historico =
                            await resposta.text();

                    const conversa =
                            document.getElementById("conversa");

                    conversa.innerHTML = historico;

                    // Mantem a conversa posicionada
                    // nas mensagens mais recentes.
                    window.scrollTo(
                            0,
                            document.body.scrollHeight
                    );

                } catch (erro) {

                    document.getElementById(
                            "conversa"
                    ).innerHTML =
                            "<p>Servidor temporariamente indisponivel.</p>";

                    if (window.parent
                            && window.parent.reportarErroWeb) {

                        window.parent.reportarErroWeb(
                                "mensagem.jsp:atualizarConversa",
                                erro.message
                        );
                    }

                } finally {

                    consultando = false;
                }
            }

            // Primeira consulta.
            atualizarConversa();

            // Atualizacao periodica sem recarregar o iframe.
            setInterval(
                    atualizarConversa,
                    2000
            );

        </script>

    </body>

</html>
