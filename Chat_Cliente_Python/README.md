# Chat Cliente Python

Cliente gráfico em Tkinter para o middleware Java, com protocolo JSON sobre TLS e renderização do histórico HTML compartilhado pelos clientes Desktop e Web.

## Requisitos

- Python 3.10 ou superior.
- Tkinter (normalmente incluído na instalação do Python para Windows).
- Pillow para exibição de PNG, JPEG, GIF e WebP.
- Middleware Java iniciado com o botão **Serviço Terceiros** do painel.
- Certificado público `chat-root-ca.cer` em `SD-v4/certificados/`.

## Instalação

No terminal do VS Code, dentro desta pasta:

```powershell
py --version
py -m pip install -r requirements.txt
```

## Configuração

Confira `config.json`:

```json
{
  "server": { "host": "127.0.0.1", "port": 6665 },
  "tls": { "ca_file": "../SD-v4/certificados/chat-root-ca.cer" },
  "web": { "port": 8443 },
  "timeout_seconds": 15,
  "history_interval_seconds": 3
}
```

O campo `server.host` deve corresponder a um nome ou IP presente no SAN do certificado do middleware. O mesmo host será utilizado para baixar as imagens do serviço HTTPS. O cliente aceita recursos de imagem apenas por HTTPS, usando a mesma CA e verificando o certificado e o nome do servidor. Não há desativação da validação TLS.

O cliente procura automaticamente `SD-v4/certificados/chat-root-ca.cer` em layouts comuns do repositório. Também é possível definir um caminho absoluto pela variável `CHAT_CA_FILE`.

## Execução e testes

```powershell
py -m unittest discover -s tests -v
py -m compileall -q .
py diagnostico.py
py main.py
```

Inicie o middleware, ative **Serviço Terceiros** e execute os comandos acima. O teste de diagnóstico verifica o TCP, o handshake TLS e uma consulta JSON `HISTORY_REQUEST`.

## Funcionalidades

- Envio de mensagens de texto com destinatário opcional.
- Histórico compartilhado com os clientes Java Desktop e Web.
- Atualização automática do histórico e botão para atualização manual.
- Renderização de tags HTML comuns do middleware, incluindo parágrafos, títulos, negrito, itálico, sublinhado, cores e alinhamento.
- Exibição de imagens referenciadas no HTML, obtidas do endpoint HTTPS do middleware.
- Composição de mensagens com múltiplas linhas; `Ctrl + Enter` envia.
- TLS 1.2/1.3 com validação da CA e do hostname.

## Protocolo de rede

- TCP com TLS 1.2/1.3 na porta padrão `6665`.
- Uma requisição JSON por conexão, terminada por `LF` (`\n`).
- `protocolVersion`: `1`.
- Envio: `SEND_MESSAGE`.
- Consulta do histórico: `HISTORY_REQUEST`.
- Confirmação: `ACK`.
- Histórico: `HISTORY_RESPONSE` com conteúdo HTML.
- Erro: `ERROR`.

A renderização de imagens depende de o middleware fornecer tags HTML `<img src="...">` com URLs ou caminhos relativos válidos nos endpoints HTTPS do projeto. Recursos externos ou HTTP sem TLS são recusados deliberadamente.
