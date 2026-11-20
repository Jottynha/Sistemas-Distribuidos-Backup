# Bancos MySQL da plataforma

A persistencia das conversas e centralizada no middleware por JDBC. Os clientes Desktop, Web e Python nao acessam o MySQL diretamente.

- `chat_distribuido`: Banco A, associado ao Middleware A (principal inicial).
- `chat_distribuido_b`: Banco B, associado ao Middleware B (reserva).

Os dois schemas utilizam a mesma tabela `mensagens`. O identificador `message_id` e chave primaria, permitindo que a replicacao preserve a identidade da mensagem e reconheca repeticoes.

## Criacao dos bancos

Execute no MySQL Workbench:

1. `schema.sql` para criar `chat_distribuido.mensagens`.
2. `schema_b.sql` para criar `chat_distribuido_b.mensagens`.

Os dois bancos podem ficar na mesma instancia MySQL durante o desenvolvimento. Isso valida a replicacao logica entre bancos, mas nao protege contra a falha fisica do servidor/host MySQL. Para a demonstracao distribuida, Banco A e Banco B devem ficar em instancias ou hosts independentes.

## Usuarios JDBC locais

Escolha senhas diferentes e fortes localmente. Nao salve as senhas neste repositorio.

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

Para bancos em hosts diferentes, crie usuarios com o host de origem efetivo do middleware e limite o Firewall ao IP do middleware autorizado. Nao exponha TCP 3306 a internet.

## Configuracao dos processos

O middleware le as configuracoes JDBC por variaveis de ambiente:

- `CHAT_DB_HOST`
- `CHAT_DB_PORT`
- `CHAT_DB_NAME`
- `CHAT_DB_USER`
- `CHAT_DB_PASSWORD`
- `CHAT_DB_SSL_MODE`

O script `scripts/windows/iniciar-middleware-a.ps1` configura o processo A para `chat_distribuido`/`chat_app`. O script `scripts/windows/iniciar-middleware-b.ps1` configura o processo B para `chat_distribuido_b`/`chat_app_b`. As senhas sao solicitadas de forma interativa e nao ficam nos arquivos do projeto.

## Replicacao A -> B e failover

A replicacao sincrona A→B foi testada com mensagens enviadas pelos clientes Desktop, Web e Python. O principal persiste no Banco A, envia uma copia JSON/TLS para o receptor interno de B e aguarda a confirmacao da persistencia no Banco B antes de responder ACK ao cliente. O `message_id` original e preservado e o DAO trata repeticoes identicas de forma idempotente.

O Middleware B inicia com `--replica-only` e mantem o receptor TLS interno (porta 6666). O A envia heartbeat; quando o lease expira, A interrompe seus listeners e B pode promover-se para ACTIVE, iniciar os servicos de cliente e manter um marcador persistente em `data/middleware-reserva-ativo.flag`. Os clientes devem ser configurados com os enderecos A e B usando `scripts/windows/configurar-clientes-failover.ps1`.

O failover automatico deve ser validado desligando A enquanto os clientes permanecem abertos. O teste em que ambos os bancos estao no mesmo MySQL e apenas uma simulacao logica; para tolerancia a falha fisica, os bancos e middlewares precisam estar em hosts/instancias independentes.

## Verificar replicacao e sincronizacao

`verificar-sincronizacao.sql` compara os IDs presentes em A/B, lista mensagens ausentes e identifica IDs com conteudo divergente. Execute-o antes de qualquer procedimento de retorno do principal.

Depois que B assumir o atendimento, as novas mensagens passam a existir apenas em B. O retorno seguro para A exige parar A e B, copiar para A as mensagens ausentes de B e confirmar que nao ha divergencias. Se ambos schemas estiverem na mesma instancia MySQL, pode-se usar `sincronizar-banco-a-com-b.sql` depois de analisar a saida do script de verificacao. Esse script nao sobrescreve linhas existentes e nao resolve conflitos: interrompa e investigue se o script de verificacao apontar IDs divergentes.

Para bancos em hosts diferentes, o procedimento precisa utilizar exportacao/importacao para uma tabela de staging no host A; nao execute o script de sincronizacao local, pois ele referencia os dois schemas na mesma instancia.

Somente depois da sincronizacao e da verificacao e seguro executar `scripts/windows/resetar-reserva.ps1`, que pede confirmacao explicita antes de remover o marcador de promocao. Inicie B primeiro em standby e depois A.

## Configuracao portatil

O repositorio versiona os scripts SQL e exemplos/documentacao, nao os dados de execucao. As credenciais ficam no ambiente local de cada processo. Nao versionar:

- senhas, `.env` ou arquivos de configuracao local com segredos;
- keystores/chaves privadas `.p12`, `.pfx`, `.jks`;
- `tls.properties` com caminho/senha de keystore;
- logs, TXT de historico, dumps com dados reais ou diretorios locais `data/`.

O certificado publico da CA (`.cer`) pode ser versionado para distribuicao da confianca TLS.
