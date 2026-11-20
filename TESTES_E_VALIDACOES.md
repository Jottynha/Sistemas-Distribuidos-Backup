# Testes e limites da validação desta entrega

## Verificações executadas durante a preparação

- Python: `pytest` — 11 testes aprovados, incluindo validação de nomes de publicidade e segurança básica dos recursos.
- Python: `compileall` sem erros.
- JavaScript externo `middleware-client.js`: `node --check` aprovado.
- JavaScript inline de `mensagem.jsp`: extraído após remover expressões JSP e validado com `node --check`.
- XML: 16 arquivos Maven/GlassFish analisados com parser XML.
- Fontes Java do emissor multicast, receptor Desktop e ponte WebSocket: compilação sintática com `javac` usando stubs temporários das APIs externas. Isso não substitui o build Maven real.

## O que ainda precisa ser executado no ambiente do grupo

- `mvn clean install` ou Clean and Build pelo NetBeans para os módulos Java.
- Deploy do WAR no GlassFish 8 e teste real do endpoint `/ws/publicidade`.
- Teste de failover desligando o Middleware A com clientes que já estavam abertos.
- Teste de recepção multicast simultânea nos clientes Desktop, Web e Python.
- Teste em vários computadores/hotspot, com Banco A e Banco B em hosts/instâncias independentes.

A replicação síncrona A→B, a persistência após reinício e a conexão JDBC dos dois bancos já foram testadas pelo usuário localmente. A promoção/failover e a publicidade estão implementadas no código, mas não devem ser declaradas comprovadas até realizar os testes operacionais acima.
