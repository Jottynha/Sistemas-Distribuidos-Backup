from __future__ import annotations

import socket
import ssl
import sys

from chat_client.config import load_config
from chat_client.protocol import (
    create_history_request,
    decode_response_line,
    encode_line,
)


def create_context(ca_file):
    context = ssl.create_default_context(ssl.Purpose.SERVER_AUTH)
    context.check_hostname = True
    context.verify_mode = ssl.CERT_REQUIRED

    certificate = ca_file.read_bytes()
    if b"-----BEGIN CERTIFICATE-----" in certificate:
        context.load_verify_locations(cafile=str(ca_file))
    else:
        # Certificados .cer exportados pelo keytool podem estar em DER.
        context.load_verify_locations(cadata=certificate)
    return context


def main() -> int:
    print("=" * 64)
    print("DIAGNOSTICO DO CLIENTE PYTHON / MIDDLEWARE JAVA")
    print("=" * 64)

    try:
        config = load_config()
        print("[OK] Configuracao carregada")
        print(f"     Servidor: {config.host}:{config.port}")
        print(f"     Certificado CA: {config.ca_file}")

        context = create_context(config.ca_file)
        print("[OK] Certificado CA carregado pelo Python")

    except Exception as error:
        print("[FALHA] Configuracao/certificado:")
        print(f"        {type(error).__name__}: {error}")
        return 1

    request = create_history_request("Python-Diagnostico")

    try:
        with socket.create_connection(
            (config.host, config.port), timeout=config.timeout
        ) as raw_socket:
            print("[OK] Conexao TCP estabelecida")

            with context.wrap_socket(
                raw_socket, server_hostname=config.host
            ) as tls_socket:
                tls_socket.settimeout(config.timeout)
                print(
                    "[OK] Handshake TLS concluido: "
                    f"{tls_socket.version()}"
                )
                print(
                    "     Certificado apresentado pelo servidor: "
                    f"{tls_socket.getpeercert().get('subject', 'nao identificado')}"
                )

                tls_socket.sendall(encode_line(request))
                print("[OK] HISTORY_REQUEST enviado em JSON")

                with tls_socket.makefile("rb") as stream:
                    response_line = stream.readline(1_048_577)

        response = decode_response_line(response_line)
        response_type = response.get("type")
        print(f"[OK] Resposta JSON recebida: {response_type}")

        if response_type == "ERROR":
            metadata = response.get("metadata") or {}
            print(
                "[FALHA] Middleware retornou ERROR: "
                f"{metadata.get('code', 'SEM_CODIGO')} - "
                f"{response.get('content', '')}"
            )
            return 1

        if response_type != "HISTORY_RESPONSE":
            print("[FALHA] Resposta diferente de HISTORY_RESPONSE")
            print(response)
            return 1

        print("[SUCESSO] Python comunicou com o middleware Java.")
        print("         O servidor recebeu o pedido e devolveu o historico.")
        print("         Quantidade de caracteres no historico: "
              f"{len(response.get('content', ''))}")
        return 0

    except ConnectionRefusedError as error:
        print("[FALHA] TCP recusado. Confira se o botao Terceiros esta ativo ")
        print("        e se host/porta estao corretos.")
        print(f"        {error}")
    except socket.timeout as error:
        print("[FALHA] Timeout ao conectar ou aguardar resposta do Java.")
        print(f"        {error}")
    except ssl.SSLCertVerificationError as error:
        print("[FALHA] O certificado ou o nome do servidor nao foi validado.")
        print("        Confirme que o CA e o host pertencem ao certificado atual.")
        print(f"        {error}")
    except ssl.SSLError as error:
        print("[FALHA] Erro durante o handshake TLS.")
        print(f"        {error}")
    except (ConnectionError, OSError, ValueError) as error:
        print("[FALHA] Comunicacao/protocolo:")
        print(f"        {type(error).__name__}: {error}")

    print("\nConsulte tambem o console do middleware e os logs em data/logs.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
