from __future__ import annotations

import json
import socket
import ssl
import time
import os
import urllib.request
import urllib.error
from pathlib import Path
from typing import Any
from urllib.parse import unquote, urljoin, urlsplit

from .protocol import decode_response_line, encode_line


MAX_RESOURCE_BYTES = 8 * 1024 * 1024
MAX_CATALOG_BYTES = 2 * 1024 * 1024


class MiddlewareError(RuntimeError):
    """Erro estruturado devolvido pelo middleware."""

    def __init__(self, response: dict[str, Any]):
        self.response = response
        metadata = response.get("metadata") or {}
        code = metadata.get("code", "ERRO_MIDDLEWARE")
        description = response.get("content") or "O middleware recusou a requisicao."
        super().__init__(f"{code}: {description}")


class TLSJsonTransport:
    """Transporte TLS com tentativa automatica no middleware reserva."""

    def __init__(
        self,
        host: str,
        port: int,
        ca_file: Path,
        timeout: float = 15,
        backup_hosts: tuple[str, ...] = (),
        web_port: int = 8443,
    ):
        self.primary_host = host
        self.backup_hosts = tuple(backup_hosts)
        self.hosts = tuple(dict.fromkeys(h for h in (host, *backup_hosts) if h))
        self._active_host = host
        self.port = port
        self.web_port = web_port
        self.ca_file = Path(ca_file)
        self.timeout = timeout
        self._ssl_context = self._create_ssl_context()

    @property
    def active_host(self) -> str:
        return self._active_host

    def web_base_url(self, host: str | None = None) -> str:
        selected = host or self._active_host
        formatted = f"[{selected}]" if ":" in selected and not selected.startswith("[") else selected
        return f"https://{formatted}:{self.web_port}/"

    def _ordered_hosts(self) -> tuple[str, ...]:
        return tuple(dict.fromkeys((self._active_host, *self.hosts)))

    def _create_ssl_context(self) -> ssl.SSLContext:
        if not self.ca_file.is_file():
            raise FileNotFoundError(f"Certificado CA nao encontrado: {self.ca_file}")
        context = ssl.create_default_context(ssl.Purpose.SERVER_AUTH)
        context.check_hostname = True
        context.verify_mode = ssl.CERT_REQUIRED
        certificate = self.ca_file.read_bytes()
        if b"-----BEGIN CERTIFICATE-----" in certificate:
            context.load_verify_locations(cafile=str(self.ca_file))
        else:
            context.load_verify_locations(cadata=certificate)
        return context

    def request(self, message: dict[str, Any]) -> dict[str, Any]:
        """Envia JSON/TLS e repete no reserva durante a janela de promocao."""
        payload = encode_line(message)
        hosts = self._ordered_hosts()
        espera = max(5.0, min(60.0, float(os.getenv("CHAT_FAILOVER_CLIENT_WAIT_SECONDS", "25"))))
        limite = time.monotonic() + espera
        ultima_falha: Exception | None = None
        codigos_transitorios = {
            "RESERVA_ATIVO", "MIDDLEWARE_STANDBY", "ERRO_PERSISTENCIA",
            "FAILOVER_EM_ANDAMENTO", "MIDDLEWARE_REBAIXADO"
        }

        while True:
            for host in self._ordered_hosts():
                try:
                    with socket.create_connection(
                        (host, self.port), timeout=min(self.timeout, 4.0)
                    ) as raw_socket:
                        with self._ssl_context.wrap_socket(
                            raw_socket, server_hostname=host
                        ) as tls_socket:
                            tls_socket.settimeout(self.timeout)
                            with tls_socket.makefile("rwb") as stream:
                                stream.write(payload)
                                stream.flush()
                                line = stream.readline(1_048_577)

                    response = decode_response_line(line)
                    if response.get("type") == "ERROR":
                        erro = MiddlewareError(response)
                        metadata = response.get("metadata") or {}
                        codigo = str(metadata.get("code", ""))
                        if codigo in codigos_transitorios:
                            ultima_falha = erro
                            continue
                        raise erro

                    self._active_host = host
                    return response

                except MiddlewareError:
                    raise
                except (OSError, ssl.SSLError, ConnectionError, ValueError) as error:
                    ultima_falha = error

            if time.monotonic() >= limite:
                break
            time.sleep(min(0.9, max(0.0, limite - time.monotonic())))

        raise ConnectionError(
            "Nenhum middleware configurado confirmou a requisicao por TLS dentro da janela de failover. "
            f"Hosts tentados: {', '.join(hosts)}."
        ) from ultima_falha

    @staticmethod
    def _validated_https_url(source: str, base_url: str) -> str:
        url = urljoin(base_url, source.strip())
        parsed = urlsplit(url)
        base = urlsplit(base_url)
        if parsed.scheme.lower() != "https":
            raise ValueError("O recurso precisa ser servido por HTTPS.")
        if not parsed.hostname or parsed.username or parsed.password:
            raise ValueError("URL de recurso invalida.")
        expected_host = (base.hostname or "").lower()
        actual_host = parsed.hostname.lower()
        loopback_aliases = {"localhost", "127.0.0.1", "::1"}
        same_host = actual_host == expected_host or (
            expected_host in loopback_aliases and actual_host in loopback_aliases
        )
        if not same_host:
            raise ValueError("O recurso aponta para um host diferente do middleware configurado.")
        if (parsed.port or 443) != (base.port or 443):
            raise ValueError("O recurso aponta para uma porta HTTPS nao autorizada.")
        return url

    def _get_https_resource(
        self,
        source: str,
        max_bytes: int,
        expected_type: str,
        base_url: str | None = None,
    ) -> tuple[bytes, str]:
        prevalidated_url = self._validated_https_url(source, base_url) if base_url else None
        if base_url:
            base_host = urlsplit(base_url).hostname
            if not base_host:
                raise ValueError("URL base HTTPS invalida.")
            hosts = (base_host,)
        else:
            hosts = self._ordered_hosts()
        espera = max(5.0, min(60.0, float(os.getenv("CHAT_FAILOVER_CLIENT_WAIT_SECONDS", "25"))))
        limite = time.monotonic() + espera
        ultima_falha: Exception | None = None

        while True:
            for host in hosts:
                current_base = base_url or self.web_base_url(host)
                url = prevalidated_url if prevalidated_url is not None else self._validated_https_url(source, current_base)
                request = urllib.request.Request(
                    url, headers={"User-Agent": "ChatClientePython/1.0"}
                )
                try:
                    with urllib.request.urlopen(
                        request, context=self._ssl_context, timeout=min(self.timeout, 5.0)
                    ) as response:
                        content_type = response.headers.get_content_type()
                        if not content_type.startswith(expected_type):
                            raise ValueError(
                                f"O recurso retornado nao e {expected_type} ({content_type})."
                            )
                        data = response.read(max_bytes + 1)
                    if not data:
                        raise ValueError("O servidor retornou um recurso vazio.")
                    if len(data) > max_bytes:
                        raise ValueError("O recurso ultrapassa o limite permitido.")
                    self._active_host = host
                    return data, url
                except urllib.error.HTTPError as error:
                    # 4xx representa recurso inexistente/nao autorizado; 5xx pode
                    # ocorrer durante a transicao do principal para o reserva.
                    if error.code < 500:
                        raise ValueError(f"O middleware retornou HTTP {error.code} para o recurso.") from error
                    ultima_falha = error
                except (OSError, ssl.SSLError, urllib.error.URLError, TimeoutError) as error:
                    ultima_falha = error

            if time.monotonic() >= limite:
                break
            time.sleep(min(0.9, max(0.0, limite - time.monotonic())))

        raise ConnectionError(
            "Nao foi possivel obter o recurso em nenhum middleware configurado dentro da janela de failover."
        ) from ultima_falha

    def fetch_image(self, source: str, base_url: str | None = None) -> bytes:
        data, _ = self._get_https_resource(source, MAX_RESOURCE_BYTES, "image/", base_url)
        return data

    def fetch_stickers(self, base_url: str | None = None) -> list[dict[str, str]]:
        data, _ = self._get_https_resource("/api/stickers", MAX_CATALOG_BYTES, "application/json", base_url)
        try:
            payload = json.loads(data.decode("utf-8-sig"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise ValueError("O catalogo /api/stickers nao retornou JSON valido.") from error
        entries: Any = payload
        if isinstance(payload, dict):
            for key in ("stickers", "figurinhas", "items", "data", "files", "results"):
                candidate = payload.get(key)
                if isinstance(candidate, (list, dict)):
                    entries = candidate
                    break
        if isinstance(entries, dict):
            iterable = [{"name": str(name), "value": value} for name, value in entries.items()]
        elif isinstance(entries, list):
            iterable = entries
        else:
            raise ValueError("Formato inesperado no catalogo de figurinhas.")
        normalized: list[dict[str, str]] = []
        for entry in iterable:
            if isinstance(entry, str):
                value = entry.strip()
                label = unquote(urlsplit(value).path.rsplit("/", 1)[-1]) or value
            elif isinstance(entry, dict):
                value = ""
                for key in ("sticker", "value", "id", "name", "filename", "file", "path", "url", "src"):
                    raw = entry.get(key)
                    if isinstance(raw, str) and raw.strip():
                        value = raw.strip()
                        break
                label = str(entry.get("label") or entry.get("name") or entry.get("title") or entry.get("filename") or value).strip()
            else:
                continue
            if not value or len(value) > 500 or "\r" in value or "\n" in value:
                continue
            normalized.append({"label": label[:120] or value, "value": value})
        unique: dict[str, dict[str, str]] = {}
        for item in normalized:
            unique.setdefault(item["value"], item)
        return list(unique.values())
