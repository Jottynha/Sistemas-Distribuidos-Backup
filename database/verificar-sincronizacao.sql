-- Verifica se os bancos A e B possuem os mesmos registros.
-- Execute no MySQL Workbench conectado ao servidor que hospeda ambos os schemas.
-- O resultado ideal das tres consultas e vazio (ou zero divergencias).

-- 1) Mensagens que existem no Banco B, mas ainda nao existem no Banco A.
SELECT
    'SOMENTE_NO_B' AS divergencia,
    b.message_id,
    b.sender,
    b.message_type,
    b.sent_at
FROM chat_distribuido_b.mensagens AS b
LEFT JOIN chat_distribuido.mensagens AS a
       ON a.message_id = b.message_id
WHERE a.message_id IS NULL
ORDER BY b.sent_at, b.message_id;

-- 2) Mensagens que existem no Banco A, mas ainda nao existem no Banco B.
SELECT
    'SOMENTE_NO_A' AS divergencia,
    a.message_id,
    a.sender,
    a.message_type,
    a.sent_at
FROM chat_distribuido.mensagens AS a
LEFT JOIN chat_distribuido_b.mensagens AS b
       ON b.message_id = a.message_id
WHERE b.message_id IS NULL
ORDER BY a.sent_at, a.message_id;

-- 3) Mesmo message_id nos dois bancos, mas conteudo/HTML/metadados divergentes.
SELECT
    a.message_id,
    a.sender AS sender_a,
    b.sender AS sender_b,
    a.content AS content_a,
    b.content AS content_b,
    a.sent_at AS sent_at_a,
    b.sent_at AS sent_at_b
FROM chat_distribuido.mensagens AS a
JOIN chat_distribuido_b.mensagens AS b
  ON b.message_id = a.message_id
WHERE NOT (
       a.message_type <=> b.message_type
   AND a.sender <=> b.sender
   AND a.recipient <=> b.recipient
   AND a.content <=> b.content
   AND a.mode <=> b.mode
   AND a.color <=> b.color
   AND a.avatar <=> b.avatar
   AND a.sticker <=> b.sticker
   AND a.sent_at <=> b.sent_at
   AND CAST(a.metadata AS CHAR) <=> CAST(b.metadata AS CHAR)
   AND a.rendered_html <=> b.rendered_html
)
ORDER BY a.sent_at, a.message_id;

-- 4) Resumo quantitativo.
SELECT 'A' AS banco, COUNT(*) AS total FROM chat_distribuido.mensagens
UNION ALL
SELECT 'B' AS banco, COUNT(*) AS total FROM chat_distribuido_b.mensagens;
