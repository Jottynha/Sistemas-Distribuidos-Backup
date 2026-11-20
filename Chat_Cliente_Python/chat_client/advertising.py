from __future__ import annotations

import json
import os
import socket
import struct
import threading
from typing import Callable


class MulticastAdvertisementListener:
    """Recebe atualizacoes ADVERTISEMENT_UPDATE por UDP multicast.

    Se a interface de rede ficar indisponivel ou o hotspot reiniciar, o
    listener tenta ingressar novamente no grupo sem exigir reiniciar o chat.
    """

    def __init__(self, group: str, port: int, callback: Callable[[str], None]):
        self.group = group
        self.port = port
        self.callback = callback
        self.interface_address = "0.0.0.0"
        configurada = os.getenv("CHAT_AD_MULTICAST_INTERFACE", "").strip()
        if configurada:
            # No cliente Python, a configuracao e o IPv4 local da interface
            # conectada ao hotspot/rede multicast.
            socket.inet_aton(configurada)
            self.interface_address = configurada
        self._socket: socket.socket | None = None
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(
            target=self._receive,
            name="python-advertising-multicast",
            daemon=True,
        )
        self._thread.start()

    def _receive(self) -> None:
        group_bytes = socket.inet_aton(self.group)
        interface_bytes = socket.inet_aton(self.interface_address)
        while not self._stop.is_set():
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
            self._socket = sock
            try:
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                sock.bind(("", self.port))
                membership = struct.pack("=4s4s", group_bytes, interface_bytes)
                sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, membership)
                sock.settimeout(1.0)
                print(f"Cliente Python inscrito no multicast {self.group}:{self.port} pela interface {self.interface_address}")

                while not self._stop.is_set():
                    try:
                        payload, _address = sock.recvfrom(65507)
                    except socket.timeout:
                        continue
                    except OSError:
                        if self._stop.is_set():
                            break
                        raise

                    try:
                        message = json.loads(payload.decode("utf-8"))
                        if message.get("type") != "ADVERTISEMENT_UPDATE":
                            continue
                        filename = message.get("content", "")
                        if isinstance(filename, str) and self._valid_filename(filename):
                            self.callback(filename)
                    except (UnicodeDecodeError, json.JSONDecodeError, AttributeError):
                        continue
            except OSError as error:
                if not self._stop.is_set():
                    print(f"Publicidade multicast indisponivel no Python; nova tentativa em 3s: {error}")
                    self._stop.wait(3.0)
            finally:
                try:
                    sock.close()
                except OSError:
                    pass
                if self._socket is sock:
                    self._socket = None

    @staticmethod
    def _valid_filename(filename: str) -> bool:
        import re
        return bool(
            re.fullmatch(
                r"[A-Za-z0-9._-]{1,180}\.(png|gif|jpg|jpeg|webp)",
                filename,
                re.IGNORECASE,
            )
        ) and ".." not in filename

    def close(self) -> None:
        self._stop.set()
        current = self._socket
        if current is not None:
            try:
                current.close()
            except OSError:
                pass
