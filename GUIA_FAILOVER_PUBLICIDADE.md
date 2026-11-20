# Guia de failover e publicidade multicast

Este guia cobre a preparação e a demonstração dos requisitos 5 e 7 do Trabalho 1: replicação contínua, promoção do middleware reserva, reconexão dos clientes e publicidade sincronizada por multicast.

## 1. Estado e componentes

### Redundância A/B

- **Middleware A (principal inicial):** MySQL `chat_distribuido`; atende Desktop, Python e Web quando está ACTIVE.
- **Middleware B (reserva):** MySQL `chat_distribuido_b`; inicia em `--replica-only`, recebe replicações e heartbeats TLS na porta 6666 e assume o atendimento após a expiração do heartbeat.
- **Replicação:** A grava no Banco A, envia a mesma mensagem/ID e o HTML renderizado ao B e espera o ACK interno antes de confirmar a mensagem ao cliente.
- **Idempotência:** a chave `message_id` é preservada. Uma repetição idêntica é aceita como duplicata; um ID com conteúdo divergente é rejeitado.
- **Promoção:** B grava `data/middleware-reserva-ativo.flag` ao assumir. O marcador faz com que B continue ACTIVE mesmo após reiniciar. A detecta `ACTIVE` ou perda do lease e interrompe seus listeners.
- **Clientes:** Desktop, Python e Web têm endereços A/B e repetem solicitações por uma janela configurável durante a promoção. Mensagens que são repetidas conservam o mesmo ID durante as tentativas.

### Publicidade

- O middleware que estiver ACTIVE percorre `SD-v4/Chat_Middleware_Service/publicidade/` e anuncia um arquivo por UDP multicast.
- Grupo e porta padrão: `239.255.42.99:5005`; intervalo padrão: 10 segundos.
- Desktop Java e Python recebem o datagrama e buscam o arquivo por HTTPS em `/publicidade/{arquivo}`.
- Navegadores não recebem UDP multicast diretamente. O `AdvertisementMulticastBridge`, hospedado no GlassFish, recebe o multicast e retransmite os avisos aos navegadores por WebSocket (`/ws/publicidade`). O navegador baixa a imagem por HTTPS usando o cliente com failover A/B.

## 2. Preparar a rede e os hosts

Para desenvolvimento local, A, B, MySQL, GlassFish e clientes podem estar na mesma máquina. Use `127.0.0.1` nos parâmetros dos scripts. Isso permite testar o fluxo de processos e failover lógico, mas não comprova tolerância à falha física do host ou do MySQL.

Para a demonstração do enunciado, conecte os computadores ao mesmo hotspot/rede e distribua os componentes em hosts diferentes. Uma topologia recomendada:

- Host do Middleware A + Banco A.
- Host do Middleware B + Banco B independente.
- Host do GlassFish (não desligado junto com A).
- Hosts de clientes Desktop, Web e Python.

A máquina do GlassFish precisa continuar funcionando quando A for desligado; se a página JSP estiver hospedada no mesmo computador que A, o navegador perde a própria aplicação, mesmo que a API B tenha assumido.

### Configurar cada computador de cliente e o host do GlassFish

No PowerShell, a partir da raiz do repositório, execute em cada computador que inicia um cliente ou GlassFish:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\windows\configurar-clientes-failover.ps1 `
    -PrimaryHost "IP_OU_DNS_DO_MIDDLEWARE_A" `
    -BackupHost "IP_OU_DNS_DO_MIDDLEWARE_B" `
    -MulticastInterface "IPV4_LOCAL_DESTA_MAQUINA"
```

`MulticastInterface` é o IPv4 local da interface conectada ao hotspot/rede multicast, não o IP do servidor. Pode ser omitido para seleção automática. O script configura:

- `CHAT_SERVER_HOST` e `CHAT_SERVER_BACKUP_HOST`;
- portas Desktop 6664, Python 6665 e HTTPS 8443;
- `CHAT_FAILOVER_CLIENT_WAIT_SECONDS` (padrão 25);
- grupo/porta multicast e `CHAT_AD_MULTICAST_INTERFACE`.

As variáveis são salvas no perfil do usuário. Feche e reabra NetBeans, VS Code, os clientes e GlassFish para que os processos herdem os valores. O GlassFish lê os hosts A/B e a interface multicast do ambiente ao iniciar.

### Certificados TLS

Os certificados dos dois middlewares devem ser assinados pela CA pública confiada pelos clientes e possuir no SAN o IP/DNS usado para acessar cada servidor. Não desative a validação de hostname/cadeia para contornar erro de certificado. Cada host deve possuir o seu keystore privado e `data/tls.properties` local; esses arquivos nunca devem ir para o Git.

## 3. Iniciar o Middleware B e o Middleware A

### Primeiro: Middleware B

No host B, com `chat_app_b` autorizado no Banco B:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\windows\iniciar-middleware-b.ps1 `
    -DatabaseHost "IP_DO_MYSQL_B" `
    -MulticastInterface "IPV4_LOCAL_DO_HOST_B"
```

Informe a senha de `chat_app_b` e crie um token forte para replicação. Mantenha a janela aberta. No desenvolvimento local, use `-DatabaseHost "127.0.0.1"` e omita o endereço de interface se a seleção automática funcionar.

Resultado esperado enquanto estiver em espera:

```text
Receptor de replicacao TLS ativo na porta 6666
Middleware B em STANDBY. A promocao sera automatica se os heartbeats do principal expirarem.
```

### Depois: Middleware A

No host A, com `chat_app` autorizado no Banco A:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\windows\iniciar-middleware-a.ps1 `
    -DatabaseHost "IP_DO_MYSQL_A" `
    -ReplicationHost "IP_OU_DNS_DO_MIDDLEWARE_B" `
    -MulticastInterface "IPV4_LOCAL_DO_HOST_A"
```

Informe a senha de `chat_app` e exatamente o mesmo token de replicação usado no B. Em desenvolvimento local, use `-DatabaseHost "127.0.0.1"` e `-ReplicationHost "127.0.0.1"`.

Quando A for autorizado, o console deve apresentar os serviços Desktop/TLS 6664, Python/TLS 6665, HTTPS 8443 e, se a rede suportar, a publicidade multicast. Os tempos padrão são heartbeat a cada 2 s, lease de A de 7 s, promoção de B após 10 s sem heartbeat e uma janela inicial de 20 s para o A se anunciar ao B.

## 4. Teste de replicação antes de provocar a falha

1. Inicie B e depois A.
2. Envie uma mensagem pelo Desktop, outra pelo Python e outra pelo Web.
3. Consulte os schemas `chat_distribuido` e `chat_distribuido_b` no Workbench.
4. Confirme que cada mensagem aparece nos dois bancos com o mesmo `message_id`.
5. Reinicie A e B em sequência normal e confirme que o histórico continua disponível.

Exemplo de consulta:

```sql
SELECT message_id, sender, content, sent_at
FROM chat_distribuido.mensagens
ORDER BY created_at DESC;

SELECT message_id, sender, content, sent_at
FROM chat_distribuido_b.mensagens
ORDER BY created_at DESC;
```

## 5. Demonstrar o failover automático

1. Deixe B em STANDBY e A em ACTIVE.
2. Abra pelo menos uma instância de cada modalidade de cliente. Para a avaliação, a demonstração final exige duas Web, duas Desktop e duas Python, com vários hosts envolvidos.
3. Envie mensagens e confirme a replicação em ambos os bancos.
4. Encerre **somente o processo Middleware A** (Ctrl+C ou feche abruptamente a janela do A). Não encerre B, MySQL B, GlassFish nem clientes.
5. Aguarde B registrar no console que o heartbeat expirou e que ele assumiu o atendimento. O tempo é aproximadamente 10 segundos após o último heartbeat, além do ciclo do monitor.
6. Sem reiniciar/reabrir os clientes, envie mensagens novas por Desktop, Web e Python.
7. Confirme que as mensagens novas aparecem no Banco B. A pode estar desligado e, portanto, não receber as mensagens novas.
8. Reinicie B e confirme que o marcador persistente mantém B ACTIVE. Se A voltar a iniciar enquanto B está ACTIVE, A deve detectar esse estado e permanecer em STANDBY.

A confirmação de que o failover foi concluído exige mais do que B estar ativo: os clientes que já estavam abertos precisam conseguir enviar novas mensagens por B sem intervenção manual.

## 6. Publicidade sincronizada

### Adicionar os banners

Coloque PNG/GIF/JPG/JPEG/WEBP em:

```text
SD-v4/Chat_Middleware_Service/publicidade/
```

Copie exatamente os mesmos arquivos, com os mesmos nomes e conteúdos, para a pasta `publicidade/` do Middleware B. O datagrama multicast transmite o nome do arquivo; a imagem é baixada do middleware que estiver ACTIVE.

O pacote contém `chat-banner-exemplo.png`. Para demonstrar rotação, adicione pelo menos mais um arquivo, por exemplo `campanha-2.gif`.

### Iniciar e observar

Com A ou B ACTIVE, o console deve mostrar algo semelhante a:

```text
Publicidade multicast ativa: grupo 239.255.42.99:5005 | intervalo 10s | interface ...
Publicidade anunciada por multicast: chat-banner-exemplo.png
```

Evidência esperada:

- Desktop: painel inferior “Publicidade sincronizada” altera o banner.
- Python: área de publicidade altera o banner; GIF animado é reproduzido se Pillow estiver instalado.
- Web: quadro de publicidade recebe o aviso por WebSocket do GlassFish e a imagem é buscada por HTTPS.
- Após a promoção de B, a publicidade continua sendo anunciada pelo B; os clientes não precisam reiniciar.

O intervalo pode ser alterado por `CHAT_AD_INTERVAL_SECONDS` nos scripts A/B. Grupo e porta podem ser alterados por `CHAT_AD_MULTICAST_GROUP` e `CHAT_AD_MULTICAST_PORT`, mas todos os emissores e receptores devem usar os mesmos valores.

### Firewall e hotspot

Se o firewall bloquear a recepção UDP, abra PowerShell como administrador no computador que precisa receber anúncios e execute:

```powershell
.\scripts\windows\permitir-publicidade-multicast.ps1
```

O script abre UDP 5005 somente para a sub-rede local. Isso não força o hotspot/roteador a encaminhar multicast. Alguns hotspots bloqueiam multicast ou isolam clientes Wi-Fi; nessa situação, TCP/TLS pode funcionar enquanto os anúncios UDP não chegam. Teste primeiro numa rede que encaminhe multicast e permita comunicação entre os clientes.

O TTL do emissor é 1, para manter os datagramas no segmento local.

## 7. Retorno manual do Banco B para A (failback)

A implementação promove B automaticamente, mas **não replica de volta para A enquanto B está ACTIVE**. Após a promoção, novas mensagens podem existir somente no Banco B. Não remova o marcador do B nem volte a iniciar A como principal antes de sincronizar os bancos.

### Quando os dois schemas estão na mesma instância MySQL

1. Encerre os processos A e B.
2. No Workbench, execute `database/verificar-sincronizacao.sql`.
3. Investigue qualquer ID divergente. As consultas de mensagens ausentes devem ser entendidas antes de continuar.
4. Se houver mensagens apenas em B e nenhum ID conflitante, execute `database/sincronizar-banco-a-com-b.sql`.
5. Execute novamente `database/verificar-sincronizacao.sql`. Não deve haver mensagens ausentes nem IDs conflitantes.
6. No host do B, execute `scripts/windows/resetar-reserva.ps1` e responda `BANCO_A_SINCRONIZADO` e depois `RESETAR` somente após a verificação SQL.
7. Inicie B primeiro em STANDBY e depois A.

O script PowerShell solicita confirmação manual; ele não consegue provar sozinho que os bancos foram sincronizados.

### Quando os bancos estão em hosts MySQL diferentes

Os scripts SQL acima referenciam os dois schemas na mesma instância e não transferem dados entre hosts. Nesse cenário, faça um dump do Banco B, importe para uma tabela de staging no host A, compare IDs/conteúdo e insira apenas mensagens ausentes em A. Só depois de uma verificação consistente remova o marcador e retorne A como principal.

## 8. Limitações que devem ser consideradas na videoaula

- Dois schemas (`chat_distribuido` e `chat_distribuido_b`) no mesmo MySQL Server não protegem contra falha física do servidor MySQL. Use instâncias/hosts independentes para comprovar esse requisito.
- A e B em computadores diferentes precisam de endereços acessíveis na mesma rede, certificados TLS com SAN adequado e porta 6666 liberada somente entre os middlewares.
- O host do GlassFish deve permanecer ativo durante a falha do A.
- A publicidade multicast depende de suporte do roteador/hotspot e da interface correta em cada host.
- Não coloque tokens, senhas, `tls.properties` ou keystores privados no repositório.
