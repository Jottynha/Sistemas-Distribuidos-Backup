from __future__ import annotations

import json
import uuid
from datetime import datetime, timezone
from typing import Any


PROTOCOL_VERSION = 1
MAX_JSON_LENGTH = 1_048_576


def _timestamp() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def create_message(
    *,
    message_type: str,
    sender: str,
    recipient: str = "",
    content: str = "",
    mode: str = "",
    color: str = "",
    avatar: str = "",
    sticker: str = "",
    correlation_id: str | None = None,
) -> dict[str, Any]:
    """Cria o mesmo envelope definido pelo record protocolo.ChatMessage."""
    return {
        "protocolVersion": PROTOCOL_VERSION,
        "id": str(uuid.uuid4()),
        "correlationId": correlation_id,
        "type": message_type,
        "sender": sender.strip(),
        "recipient": recipient.strip(),
        "content": content,
        "mode": mode,
        "color": color,
        "avatar": avatar,
        "sticker": sticker,
        "sentAt": _timestamp(),
        "metadata": {},
    }


def create_history_request(sender: str) -> dict[str, Any]:
    return create_message(
        message_type="HISTORY_REQUEST",
        sender=sender,
    )


def create_text_message(sender: str, recipient: str, content: str) -> dict[str, Any]:
    """Cria mensagem textual; emojis Unicode podem estar no próprio content."""
    normalized_content = content.strip()
    if not normalized_content:
        raise ValueError("A mensagem de texto nao pode ficar vazia.")
    return create_message(
        message_type="SEND_MESSAGE",
        sender=sender,
        recipient=recipient,
        content=normalized_content,
    )


def create_sticker_message(
    sender: str,
    recipient: str,
    sticker: str,
) -> dict[str, Any]:
    """Cria uma mensagem de figurinha usando o campo sticker do protocolo."""
    sticker_value = sticker.strip()
    if not sticker_value:
        raise ValueError("Selecione uma figurinha antes de enviar.")
    return create_message(
        message_type="SEND_MESSAGE",
        sender=sender,
        recipient=recipient,
        sticker=sticker_value,
    )


def encode_line(message: dict[str, Any]) -> bytes:
    if message.get("protocolVersion") != PROTOCOL_VERSION:
        raise ValueError("protocolVersion deve ser 1.")
    if not message.get("id"):
        raise ValueError("O campo id e obrigatorio.")
    if not message.get("type"):
        raise ValueError("O campo type e obrigatorio.")

    encoded = json.dumps(
        message,
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8") + b"\n"

    if len(encoded) > MAX_JSON_LENGTH:
        raise ValueError("A mensagem ultrapassa o limite de 1 MiB.")
    return encoded


def decode_response_line(line: bytes) -> dict[str, Any]:
    if not line:
        raise ConnectionError("O middleware encerrou a conexao sem responder.")
    if len(line) > MAX_JSON_LENGTH:
        raise ValueError("A resposta do middleware ultrapassa 1 MiB.")

    response = json.loads(line.decode("utf-8").strip())
    if not isinstance(response, dict):
        raise ValueError("A resposta JSON nao e um objeto.")
    if response.get("protocolVersion") != PROTOCOL_VERSION:
        raise ValueError("Versao de protocolo recebida diferente de 1.")
    return response
