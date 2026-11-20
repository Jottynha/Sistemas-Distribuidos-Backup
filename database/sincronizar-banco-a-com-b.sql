-- SINCRONIZACAO MANUAL DE RECUPERACAO: Banco B -> Banco A.
-- Use SOMENTE depois de promover B e antes de voltar A a principal.
-- Este script supoe que os dois schemas estao na MESMA instancia MySQL.
-- Primeiro execute verificar-sincronizacao.sql e investigue qualquer ID conflitante.
-- O script apenas insere mensagens ausentes no A; nao sobrescreve registros existentes.
-- Execute com uma conta administrativa do MySQL (nao com chat_app/chat_app_b).

START TRANSACTION;

INSERT INTO chat_distribuido.mensagens (
    message_id,
    message_type,
    sender,
    recipient,
    content,
    mode,
    color,
    avatar,
    sticker,
    sent_at,
    metadata,
    rendered_html,
    created_at
)
SELECT
    b.message_id,
    b.message_type,
    b.sender,
    b.recipient,
    b.content,
    b.mode,
    b.color,
    b.avatar,
    b.sticker,
    b.sent_at,
    b.metadata,
    b.rendered_html,
    b.created_at
FROM chat_distribuido_b.mensagens AS b
LEFT JOIN chat_distribuido.mensagens AS a
       ON a.message_id = b.message_id
WHERE a.message_id IS NULL;

SELECT ROW_COUNT() AS mensagens_copiadas_do_banco_b_para_a;

COMMIT;

-- Execute novamente database/verificar-sincronizacao.sql.
-- So reinicie a hierarquia A/B depois que nao houver mensagens faltantes
-- nem IDs com conteudo divergente.
