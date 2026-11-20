# Banco de dados da plataforma

A plataforma utiliza dois bancos com a mesma estrutura de mensagens:

- `chat_distribuido`: Banco A, utilizado pelo middleware principal;
- `chat_distribuido_b`: Banco B, utilizado pelo middleware reserva.

Os clientes nao acessam o MySQL diretamente. Somente os processos de middleware utilizam JDBC.

## Criar os bancos

Execute `schema.sql` no MySQL Workbench para criar o Banco A e `schema_b.sql` para criar o Banco B. Os bancos podem ficar no mesmo servidor durante o desenvolvimento local. Na demonstracao de tolerancia a falhas, o Banco B deve estar em uma instancia/maquina independente para que a falha de A nao torne os dois bancos indisponiveis.

## Criar os usuarios locais

Escolha senhas fortes localmente e substitua os marcadores antes de executar:

```sql
CREATE USER IF NOT EXISTS 'chat_app'@'127.0.0.1'
IDENTIFIED BY 'SUBSTITUA_POR_UMA_SENHA_FORTE_A';
GRANT SELECT, INSERT ON chat_distribuido.mensagens
TO 'chat_app'@'127.0.0.1';

CREATE USER IF NOT EXISTS 'chat_app_b'@'127.0.0.1'
IDENTIFIED BY 'SUBSTITUA_POR_UMA_SENHA_FORTE_B';
GRANT SELECT, INSERT ON chat_distribuido_b.mensagens
TO 'chat_app_b'@'127.0.0.1';

FLUSH PRIVILEGES;
```

Para uma topologia com os bancos em hosts diferentes, crie as contas com o host de origem efetivo do middleware (ou uma faixa restrita da rede) e limite o Firewall do Windows a esses enderecos. Nao exponha a porta 3306 a internet.

## Variaveis de ambiente

O middleware principal usa `CHAT_DB_NAME=chat_distribuido` e `CHAT_DB_USER=chat_app`. O middleware reserva usa `CHAT_DB_NAME=chat_distribuido_b` e `CHAT_DB_USER=chat_app_b`. Ambos recebem `CHAT_DB_HOST`, `CHAT_DB_PORT`, `CHAT_DB_PASSWORD` e `CHAT_DB_SSL_MODE` do ambiente do proprio processo.

As senhas nao devem ser salvas no Git, em scripts, POMs ou no README.

## Replicacao local em desenvolvimento

A primeira fase da replicacao usa um listener TLS interno do Middleware B na porta TCP 6666. O Middleware B executa em modo `--replica-only`: conecta-se ao Banco B e recebe mensagens do principal, sem abrir as portas destinadas aos clientes.

1. Abra um PowerShell na raiz do repositorio.
2. Execute `Set-ExecutionPolicy -Scope Process Bypass` se a politica local impedir scripts.
3. Inicie o reserva: `.\scripts\windows\iniciar-middleware-b.ps1`.
4. Em outro PowerShell, inicie o principal: `.\scripts\windows\iniciar-middleware-a.ps1`.
5. Informe em ambos os processos o mesmo token secreto de replicacao. Use um token aleatorio forte (recomenda-se pelo menos 32 caracteres).
6. Envie uma mensagem por qualquer cliente e compare os bancos usando o mesmo `message_id`.

Os scripts definem as variaveis somente no processo PowerShell e removem-nas ao encerrar. Se `mvn.cmd` nao estiver no PATH nem na localizacao usual do NetBeans, passe `-MavenPath "C:\caminho\para\mvn.cmd"`.

`CHAT_REPLICATION_ENABLED=true` e configurado apenas no Middleware A. O reserva nao replica de volta. O principal envia uma mensagem em TLS e aguarda o ACK do reserva; os dois bancos utilizam o mesmo `message_id`, e `MensagemDAO.salvarSeAusente` trata repeticoes identicas de forma idempotente.

Esta primeira fase exige que B esteja ativo para que A confirme uma nova mensagem. Se B nao responder, A nao envia ACK. O registro local de A pode permanecer para permitir uma repeticao posterior com o mesmo identificador; ainda nao existe uma fila persistente de reenvio automatico.

**Escopo ainda pendente:** promover automaticamente B para atender clientes, reconectar os clientes sem intervencao manual, transferir a configuracao dos endpoints e comprovar o failover entre hosts independentes. O modo `--replica-only` e apenas a etapa inicial de recepcao/replicacao.

## Verificar a replicacao

Depois de enviar mensagens, execute no Workbench:

```sql
SELECT message_id, sender, content, sent_at
FROM chat_distribuido.mensagens
ORDER BY created_at DESC;

SELECT message_id, sender, content, sent_at
FROM chat_distribuido_b.mensagens
ORDER BY created_at DESC;
```

Os registros replicados devem possuir o mesmo `message_id` nos dois bancos.
