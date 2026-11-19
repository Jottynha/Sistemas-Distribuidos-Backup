from __future__ import annotations

import json
import socket
import ssl
import urllib.request
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
    def __init__(self, host: str, port: int, ca_file: Path, timeout: float = 15):
        self.host = host
        self.port = port
        self.ca_file = Path(ca_file)
        self.timeout = timeout
        self._ssl_context = self._create_ssl_context()

    def _create_ssl_context(self) -> ssl.SSLContext:
        if not self.ca_file.is_file():
            raise FileNotFoundError(f"Certificado CA nao encontrado: {self.ca_file}")

        context = ssl.create_default_context(ssl.Purpose.SERVER_AUTH)
        context.check_hostname = True
        context.verify_mode = ssl.CERT_REQUIRED

        # O certificado .cer pode estar em DER (binario) ou PEM (texto).
        certificate = self.ca_file.read_bytes()
        if b"-----BEGIN CERTIFICATE-----" in certificate:
            context.load_verify_locations(cafile=str(self.ca_file))
        else:
            context.load_verify_locations(cadata=certificate)

        return context

    def request(self, message: dict[str, Any]) -> dict[str, Any]:
        payload = encode_line(message)

        with socket.create_connection(
            (self.host, self.port),
            timeout=self.timeout,
        ) as raw_socket:
            with self._ssl_context.wrap_socket(
                raw_socket,
                server_hostname=self.host,
            ) as tls_socket:
                tls_socket.settimeout(self.timeout)

                # O servidor Java processa uma linha JSON por conexao.
                with tls_socket.makefile("rwb") as stream:
                    stream.write(payload)
                    stream.flush()
                    line = stream.readline(1_048_577)

        response = decode_response_line(line)
        if response.get("type") == "ERROR":
            raise MiddlewareError(response)
        return response

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
            raise ValueError(
                "O recurso aponta para um host diferente do middleware configurado."
            )

        expected_port = base.port or 443
        actual_port = parsed.port or 443
        if actual_port != expected_port:
            raise ValueError("O recurso aponta para uma porta HTTPS nao autorizada.")

        return url

    def _get_https_resource(
        self,
        source: str,
        base_url: str,
        max_bytes: int,
        expected_type: str,
    ) -> tuple[bytes, str]:
        url = self._validated_https_url(source, base_url)
        request = urllib.request.Request(
            url,
            headers={"User-Agent": "ChatClientePython/1.0"},
        )
        with urllib.request.urlopen(
            request,
            context=self._ssl_context,
            timeout=self.timeout,
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
        return data, url

    def fetch_image(self, source: str, base_url: str) -> bytes:
        """Baixa imagem HTTPS do middleware, validando CA e hostname."""
        data, _url = self._get_https_resource(
            source,
            base_url,
            MAX_RESOURCE_BYTES,
            "image/",
        )
        return data

    def fetch_stickers(self, base_url: str) -> list[dict[str, str]]:
        """Consulta o catalogo de figurinhas publicado pelo middleware."""
        data, _url = self._get_https_resource(
            "/api/stickers",
            base_url,
            MAX_CATALOG_BYTES,
            "application/json",
        )
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

        normalized: list[dict[str, str]] = []
        if isinstance(entries, dict):
            iterable = [
                {"name": str(name), "value": value}
                for name, value in entries.items()
            ]
        elif isinstance(entries, list):
            iterable = entries
        else:
            raise ValueError("Formato inesperado no catalogo de figurinhas.")

        for entry in iterable:
            if isinstance(entry, str):
                value = entry.strip()
                label_source = value
                label = unquote(urlsplit(value).path.rsplit("/", 1)[-1]) or value
            elif isinstance(entry, dict):
                value = ""
                for key in ("sticker", "value", "id", "name", "filename", "file", "path", "url", "src"):
                    raw = entry.get(key)
                    if isinstance(raw, str) and raw.strip():
                        value = raw.strip()
                        break
                label_source = str(
                    entry.get("label")
                    or entry.get("name")
                    or entry.get("title")
                    or entry.get("filename")
                    or value
                ).strip()
                label = label_source or value
            else:
                continue

            if not value:
                continue
            if len(value) > 500 or "\r" in value or "\n" in value:
                continue
            normalized.append({"label": label[:120], "value": value})

        # Remove duplicatas preservando a ordem do middleware.
        unique: dict[str, dict[str, str]] = {}
        for item in normalized:
            unique.setdefault(item["value"], item)
        return list(unique.values())
