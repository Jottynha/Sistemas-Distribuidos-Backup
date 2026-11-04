<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%!
    private String escaparHtmlIndex(String valor) {
        if (valor == null) return "";
        return valor.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
%>
<%
    String middlewarePublic = application.getInitParameter("middleware.publicBaseUrl");
    if (middlewarePublic == null || middlewarePublic.isBlank()) {
        middlewarePublic = "http://127.0.0.1:8081";
    }
    middlewarePublic = middlewarePublic.replaceAll("/+$", "");
%>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Chat Distribuído Web 1.0</title>
</head>
<body>
    <br><br><br>
    <center>
        <form name="FrmLogin" action="chat.jsp" method="post">
            <table border="1">
                <tr>
                    <td colspan="2" bgcolor="MediumSeaGreen" align="center">
                        <font color="white" size="+3">Chat Distribuído 1.0</font>
                    </td>
                </tr>
                <tr>
                    <td align="right"><b>Nickname:</b></td>
                    <td>
                        <input type="text" name="TxtNickname" maxlength="20"
                               size="40" required autocomplete="nickname">
                    </td>
                </tr>
                <tr>
                    <td align="right"><b>Cor:</b></td>
                    <td>
                        <label><input type="radio" name="RadCor" value="Navy" checked>
                            <font color="Navy"><b>Azul</b></font></label>
                        <label><input type="radio" name="RadCor" value="Tomato">
                            <font color="Tomato"><b>Vermelho</b></font></label>
                        <label><input type="radio" name="RadCor" value="MediumSeaGreen">
                            <font color="MediumSeaGreen"><b>Verde</b></font></label>
                    </td>
                </tr>
                <tr>
                    <td align="right"><b>Avatar:</b></td>
                    <td>
                        <select id="RadAvatar" name="RadAvatar" required>
                            <option value="menino.png">menino</option>
                        </select>
                        <img id="AvatarPreview"
                             src="<%= escaparHtmlIndex(middlewarePublic + "/figurinhas/avatars/menino.png") %>"
                             width="48" height="48" alt="Prévia do avatar">
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

    <div id="configuracao" style="display:none"
         data-middleware="<%= escaparHtmlIndex(middlewarePublic) %>"></div>

    <script>
        const middlewarePublic = document
                .getElementById("configuracao")
                .dataset.middleware;
        const seletorAvatar = document.getElementById("RadAvatar");
        const previewAvatar = document.getElementById("AvatarPreview");

        function atualizarPreviewAvatar() {
            const nome = seletorAvatar.value;
            previewAvatar.src = middlewarePublic
                    + "/figurinhas/avatars/" + encodeURIComponent(nome);
        }

        async function carregarAvatares() {
            try {
                const resposta = await fetch(
                        middlewarePublic + "/api/avatars",
                        { cache: "no-store" }
                );
                if (!resposta.ok) {
                    throw new Error("HTTP " + resposta.status);
                }

                const nomes = (await resposta.text())
                        .split(/\r?\n/)
                        .map(nome => nome.trim())
                        .filter(nome => /^[a-zA-Z0-9._-]+\.(png|gif|jpg|jpeg|webp)$/i.test(nome))
                        .filter(nome => !nome.includes(".."));

                if (nomes.length === 0) {
                    throw new Error("O servidor não retornou avatares.");
                }

                seletorAvatar.innerHTML = "";
                nomes.forEach(nome => {
                    const opcao = document.createElement("option");
                    opcao.value = nome;
                    opcao.textContent = nome.replace(/\.[^.]+$/, "").replace(/[-_]/g, " ");
                    seletorAvatar.appendChild(opcao);
                });
                atualizarPreviewAvatar();
            } catch (erro) {
                console.error("Falha ao carregar avatares do middleware:", erro);
                // Mantém a opção menino.png como alternativa.
            }
        }

        seletorAvatar.addEventListener("change", atualizarPreviewAvatar);
        carregarAvatares();
    </script>
</body>
</html>
