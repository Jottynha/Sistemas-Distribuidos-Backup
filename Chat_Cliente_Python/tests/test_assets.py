import unittest
from chat_client.transport import TLSJsonTransport


class ImageResourceSecurityTests(unittest.TestCase):
    def setUp(self):
        # Os casos abaixo devem ser rejeitados antes de qualquer acesso de rede.
        self.transport = TLSJsonTransport.__new__(TLSJsonTransport)

    def test_rejects_plain_http(self):
        with self.assertRaisesRegex(ValueError, "HTTPS"):
            self.transport.fetch_image(
                "http://127.0.0.1:8443/figurinhas/teste.png",
                "https://127.0.0.1:8443/",
            )

    def test_rejects_external_host(self):
        with self.assertRaisesRegex(ValueError, "host diferente"):
            self.transport.fetch_image(
                "https://example.com/imagem.png",
                "https://127.0.0.1:8443/",
            )

    def test_rejects_unconfigured_port(self):
        with self.assertRaisesRegex(ValueError, "porta HTTPS"):
            self.transport.fetch_image(
                "https://127.0.0.1:9443/imagem.png",
                "https://127.0.0.1:8443/",
            )


if __name__ == "__main__":
    unittest.main()
