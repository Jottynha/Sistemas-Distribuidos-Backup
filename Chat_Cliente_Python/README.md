# Chat Cliente Python

Cliente gráfico Tkinter para o middleware Java. Utiliza o mesmo envelope JSON dos clientes Java, TCP/TLS para mensagens e histórico, HTTPS para imagens e catálogo de figurinhas e UDP multicast para receber anúncios de publicidade.

## Requisitos

- Windows 10/11 ou ambiente compatível.
- Python 3.10 ou superior (validado no projeto com Python 3.14).
- Tkinter.
- Pillow para exibir imagens e animar GIFs no painel de publicidade.
- Certificado público `SD-v4/certificados/chat-root-ca.cer`.
- Middleware A ou B ativo e acessível na rede.

## Instalação

Na pasta `Chat_Cliente_Python`:

```powershell
py --version
py -m pip install -r requirements.txt
```

## Configuração

O arquivo `config.json` contém valores de desenvolvimento. Para execução distribuída, prefira configurar as variáveis de ambiente no computador do cliente:

| Variável | Uso | Exemplo local |
|---|---|---|
| `CHAT_SERVER_HOST` | Middleware principal | `127.0.0.1` |
| `CHAT_SERVER_BACKUP_HOSTS` | Um ou mais hosts reserva separados por vírgula | `127.0.0.1` |
| `CHAT_SERVER_BACKUP_HOST` | Host reserva adicional | `127.0.0.1` |
| `CHAT_SERVER_PYTHON_PORT` | Porta JSON/TLS | `6665` |
| `CHAT_SERVER_WEB_PORT` | Porta HTTPS dos recursos | `8443` |
| `CHAT_FAILOVER_CLIENT_WAIT_SECONDS` | Janela de tentativas durante promoção | `25` |
| `CHAT_CA_FILE` | Caminho alternativo da CA pública | caminho para `chat-root-ca.cer` |
| `CHAT_AD_MULTICAST_GROUP` | Grupo de publicidade | `239.255.42.99` |
| `CHAT_AD_MULTICAST_PORT` | Porta UDP multicast | `5005` |
| `CHAT_AD_MULTICAST_INTERFACE` | IPv4 local da interface conectada à rede multicast | IPv4 Wi-Fi local |

O endereço de cada middleware deve corresponder a um IP/DNS presente no SAN do certificado apresentado. A conexão verifica a CA e o hostname; não desative essa validação para contornar erros de certificado.

Para configurar as variáveis A/B e multicast no Windows, a partir da raiz do repositório, use:

```powershell
.\scripts\windows\configurar-clientes-failover.ps1 `
    -PrimaryHost "IP_DO_MIDDLEWARE_A" `
    -BackupHost "IP_DO_MIDDLEWARE_B" `
    -MulticastInterface "IPV4_LOCAL_DA_INTERFACE"
```

O parâmetro `MulticastInterface` é o IPv4 da própria máquina cliente na interface conectada ao hotspot/rede, e não o IP do servidor. Depois da configuração, feche e reabra o VS Code/terminal para o processo receber as variáveis.

## Execução

```powershell
py main.py
```

O diagnóstico independente testa TCP, TLS e a solicitação do histórico:

```powershell
py diagnostico.py
```

Testes automatizados e verificação de sintaxe:

```powershell
py -m pytest -q tests
py -m compileall -q .
```

## Funcionalidades

- Envio de mensagens de texto, inclusive Unicode/emoji.
- Seletor de emojis.
- Consulta do catálogo HTTPS e envio de figurinhas.
- Histórico HTML compartilhado com Desktop e Web.
- Atualização periódica e atualização manual do histórico.
- Carregamento de imagens por HTTPS com validação de certificado e hostname.
- Painel de publicidade que recebe `ADVERTISEMENT_UPDATE` via UDP multicast.
- Exibição de banners e animação de GIF quando Pillow está instalado.
- Retentativa de entrada no grupo multicast após queda/reconexão da interface.
- Failover do transporte: tenta o middleware principal e os hosts reserva; repete a mesma requisição/ID durante a janela de promoção.

## Protocolo e portas

- TCP/TLS 1.2 ou 1.3, padrão `6665`, uma mensagem JSON por conexão terminada em `LF` (`\n`).
- HTTPS padrão `8443` para catálogo de figurinhas, imagens, publicidade e outros recursos.
- UDP multicast padrão `239.255.42.99:5005` para avisos de publicidade (o navegador usa o gateway WebSocket no GlassFish; o Python recebe UDP diretamente).
- `protocolVersion`: `1`.
- Tipos comuns: `SEND_MESSAGE`, `HISTORY_REQUEST`, `HISTORY_RESPONSE`, `ACK`, `ERROR`; publicidade usa `ADVERTISEMENT_UPDATE`.

## Observação sobre multicast

O funcionamento de TCP/TLS não garante que o hotspot encaminhe multicast. Alguns celulares/roteadores isolam clientes Wi-Fi ou bloqueiam UDP multicast. Caso o cliente não receba anúncios, verifique a interface configurada, o firewall UDP local e teste numa rede que encaminhe multicast. O banner pode ser baixado por HTTPS somente depois que o aviso multicast chega.
