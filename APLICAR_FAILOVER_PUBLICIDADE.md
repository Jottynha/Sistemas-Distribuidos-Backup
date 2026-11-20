# Aplicar a entrega de failover e publicidade

Este pacote atualiza os fontes, scripts e documentação do Trabalho 1. Ele é um **overlay** do repositório: copie os diretórios/arquivos mantendo os caminhos relativos e permita substituir os arquivos indicados.

## 1. Aplicar os arquivos

1. Faça uma cópia de segurança do repositório local antes de substituir fontes.
2. Extraia o ZIP na raiz do repositório `Sistemas-Distribuidos-Backup`, mantendo a estrutura de pastas.
3. Autorize a substituição dos arquivos existentes.
4. Não copie keystores, senhas, `tls.properties`, dados locais, logs ou pastas `target` para o Git.

O pacote **não inclui** `FrmChat.java` nem `FrmChat.form`, pois são arquivos do NetBeans GUI Builder. Na versão do projeto analisada, `FrmChat.java` já contém o painel de publicidade, o listener multicast e o envio com failover. Preserve as alterações manuais existentes nessa classe. Não substitua `FrmChat.java` por uma cópia antiga do Git ou de outro ZIP.

## 2. Compilar

Na raiz do repositório, compile o middleware e as dependências:

```powershell
mvn -pl SD-v4/Chat_Middleware_Service -am clean install
```

Compile também o Desktop e o Web:

```powershell
mvn -pl SD-v4/Chat_Cliente_Desktop -am clean package
mvn -pl SD-v6/Chat_Cliente_Web clean package
```

Se `mvn` não estiver no PATH, use o Maven incluído no NetBeans ou execute Clean and Build nos projetos pela IDE. O ambiente de geração deste pacote não possui Maven/GlassFish; portanto, os builds Java completos e o deploy Web precisam ser confirmados na máquina do grupo.

## 3. Testar A/B localmente

Para desenvolvimento na mesma máquina, mantenha os bancos lógicos `chat_distribuido` e `chat_distribuido_b` na instância MySQL local. Em terminais separados, na raiz do repositório:

**Janela 1 — B:**

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\windows\iniciar-middleware-b.ps1 -DatabaseHost "127.0.0.1"
```

**Janela 2 — A:**

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\windows\iniciar-middleware-a.ps1 -DatabaseHost "127.0.0.1" -ReplicationHost "127.0.0.1"
```

Informe a senha do usuário do respectivo banco. Use o mesmo token de replicação nas duas janelas. Não compartilhe nem versiona o token.

## 4. Configurar os clientes

Para teste local, A e B compartilham o host `127.0.0.1` e assumem as mesmas portas de clientes em momentos diferentes:

```powershell
.\scripts\windows\configurar-clientes-failover.ps1 `
    -PrimaryHost "127.0.0.1" `
    -BackupHost "127.0.0.1"
```

Feche e reabra Desktop, Python e GlassFish depois de alterar variáveis de ambiente. Em rede distribuída, use o IP/DNS real do Middleware A e do B; os certificados precisam conter esses IPs/DNS no SAN.

## 5. Teste operacional de failover

1. Inicie B primeiro e confirme que está em STANDBY.
2. Inicie A e confirme que Desktop, Python e HTTPS estão ativos.
3. Deixe pelo menos um cliente de cada modalidade aberto e envie mensagens. Verifique o mesmo `message_id` nos dois bancos.
4. Encerre **somente o processo Middleware A**. Não feche os clientes, B, o MySQL B nem o GlassFish.
5. Aguarde o log `[FAILOVER] Middleware B assumiu o atendimento dos clientes.`
6. Sem reiniciar os clientes, envie novas mensagens pelo Desktop, Web e Python.
7. Confirme que os clientes retomaram a comunicação e que as novas linhas estão no Banco B.

Esse teste ainda precisa ser executado pelo grupo. A replicação síncrona A→B e a persistência após reinicialização foram previamente testadas, mas não substituem a demonstração de queda do A com clientes já abertos.

## 6. Testar publicidade multicast

1. Coloque pelo menos dois banners (por exemplo, PNG e GIF) em `SD-v4/Chat_Middleware_Service/publicidade/` no host A e copie os mesmos arquivos, com nomes e conteúdos idênticos, para a pasta equivalente no host B.
2. Inicie o GlassFish depois de configurar as variáveis de ambiente para ele herdar `CHAT_AD_MULTICAST_GROUP`, `CHAT_AD_MULTICAST_PORT` e `CHAT_AD_MULTICAST_INTERFACE`.
3. Nos clientes Desktop/Python e no host do GlassFish, autorize recepção UDP multicast. Em PowerShell **executado como Administrador**, se o firewall bloquear:

   ```powershell
   .\scripts\windows\permitir-publicidade-multicast.ps1
   ```

4. Use o mesmo grupo e porta em todos os participantes. Padrões: grupo `239.255.42.99`, UDP `5005`, intervalo `10` segundos.
5. Observe o painel no Desktop, a área de publicidade do Python e o banner na página Web. O browser recebe o aviso pelo WebSocket do GlassFish; não recebe UDP multicast diretamente.
6. Repita depois de promover B. Como B também anuncia e hospeda os mesmos banners, a publicidade deve continuar após a promoção.

O TTL do emissor é 1. Alguns hotspots/celulares bloqueiam multicast ou isolam clientes Wi-Fi. Se TCP/TLS funciona, mas nenhum cliente recebe os anúncios, teste numa rede local que encaminhe multicast; não tente resolver isso desativando TLS.

## 7. Topologia exigida na demonstração final

O teste local em uma máquina é útil para desenvolvimento, mas não comprova tolerância à falha física. Para a videoaula, distribua os serviços:

- Middleware A e Banco A;
- Middleware B e Banco B em host/instância MySQL independentes;
- GlassFish num host que permaneça ligado durante a parada do A;
- clientes em vários hosts da mesma rede/hotspot.

Os dois schemas na mesma instância MySQL não protegem contra falha da máquina/serviço MySQL. O host GlassFish também não pode ser desligado junto com A, ou a interface Web desaparecerá mesmo que B assuma o backend.

Portas principais: TCP/TLS 6664 (Desktop), TCP/TLS 6665 (Python), TCP/TLS 6666 (replicação A→B), HTTPS 8443 (API/recursos/logs), UDP 5005 (multicast), MySQL TCP 3306 somente entre middleware e seu banco. Não exponha 3306 à Internet.

## 8. Retorno manual de B para A

Após B ser promovido, novas mensagens são gravadas no Banco B; não há replicação reversa automática B→A nesta implementação. Antes de voltar A a principal:

1. encerre A e B;
2. compare os bancos com `database/verificar-sincronizacao.sql`;
3. para schemas na mesma instância, avalie e execute `database/sincronizar-banco-a-com-b.sql` apenas se não houver IDs conflitantes;
4. valide novamente a sincronização;
5. execute `scripts/windows/resetar-reserva.ps1` e confirme as duas frases solicitadas;
6. inicie B primeiro em STANDBY e depois A.

Para bancos em hosts distintos, faça a sincronização via exportação/importação e staging; o script SQL local referencia os dois schemas na mesma instância.

## 9. Segurança do Git

O `.gitignore` não deixa de rastrear arquivos que já foram adicionados ao índice. Antes do commit, execute:

```powershell
git ls-files | Select-String -Pattern '(?i)(\.p12$|\.pfx$|\.jks$|tls\.properties$|/data/|\.csr$)'
```

Revise a saída. Se houver chaves/keystores, `tls.properties`, CSR, logs ou histórico, remova-os do índice mantendo os arquivos locais (`git rm --cached -- <caminho>`). Se chaves privadas já foram enviadas a um repositório remoto, trate-as como expostas e reemita os certificados/segredos; apenas apagá-las no commit mais recente não remove versões antigas do histórico Git.

## 10. Critério para declarar concluído

Não marque o requisito 7 como integralmente demonstrado antes de comprovar: (a) failover com clientes já abertos, (b) continuidade de mensagens depois que A é parado, (c) DB A e DB B em hosts/instâncias independentes e (d) comunicação TLS validada pelos endereços SAN efetivamente usados.

Não marque o requisito 5 como integralmente demonstrado antes de exibir a troca sincronizada do anúncio em Desktop, Web e Python, incluindo a operação após a promoção de B e numa rede que encaminhe multicast.
