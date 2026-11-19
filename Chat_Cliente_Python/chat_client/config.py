from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path


PROJECT_DIR = Path(__file__).resolve().parent.parent
CONFIG_FILE = PROJECT_DIR / "config.json"


@dataclass(frozen=True)
class ClientConfig:
    host: str
    port: int
    ca_file: Path
    timeout: float
    history_interval: int
    web_port: int

    @property
    def web_base_url(self) -> str:
        host = f"[{self.host}]" if ":" in self.host and not self.host.startswith("[") else self.host
        return f"https://{host}:{self.web_port}/"


def _resolve_ca_file(configured_value: str) -> Path:
    """Resolve o certificado em layouts diferentes do repositorio."""
    environment_value = os.getenv("CHAT_CA_FILE")
    value = environment_value.strip() if environment_value else configured_value
    configured_path = Path(value).expanduser()

    candidates: list[Path] = []
    if configured_path.is_absolute():
        candidates.append(configured_path)
    else:
        candidates.append((PROJECT_DIR / configured_path).resolve())

    for ancestor in (PROJECT_DIR, *PROJECT_DIR.parents):
        candidates.append(
            (ancestor / "SD-v4" / "certificados" / "chat-root-ca.cer").resolve()
        )

    unique_candidates: list[Path] = []
    seen: set[str] = set()
    for candidate in candidates:
        normalized = str(candidate).lower()
        if normalized not in seen:
            seen.add(normalized)
            unique_candidates.append(candidate)

    for candidate in unique_candidates:
        if candidate.is_file():
            return candidate

    searched = "\n".join(f" - {candidate}" for candidate in unique_candidates)
    raise FileNotFoundError(
        "Certificado raiz do middleware nao encontrado.\n"
        "Confira se o arquivo existe em SD-v4/certificados/chat-root-ca.cer, "
        "ou ajuste tls.ca_file em config.json.\n"
        "Caminhos verificados:\n" + searched
    )


def load_config() -> ClientConfig:
    if not CONFIG_FILE.is_file():
        raise FileNotFoundError(
            f"Arquivo de configuracao nao encontrado: {CONFIG_FILE}"
        )

    data = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
    server = data.get("server", {})
    tls = data.get("tls", {})
    web = data.get("web", {})

    ca_value = str(
        tls.get("ca_file", "../SD-v4/certificados/chat-root-ca.cer")
    )

    config = ClientConfig(
        host=str(server.get("host", "127.0.0.1")).strip(),
        port=int(server.get("port", 6665)),
        ca_file=_resolve_ca_file(ca_value),
        timeout=float(data.get("timeout_seconds", 15)),
        history_interval=max(2, int(data.get("history_interval_seconds", 3))),
        web_port=int(web.get("port", 8443)),
    )

    if not config.host:
        raise ValueError("O endereco do middleware nao pode ficar vazio.")
    if not 1 <= config.port <= 65535:
        raise ValueError("A porta TLS do middleware deve estar entre 1 e 65535.")
    if not 1 <= config.web_port <= 65535:
        raise ValueError("A porta HTTPS dos recursos deve estar entre 1 e 65535.")
    if config.timeout <= 0:
        raise ValueError("O timeout deve ser maior que zero.")

    return config
