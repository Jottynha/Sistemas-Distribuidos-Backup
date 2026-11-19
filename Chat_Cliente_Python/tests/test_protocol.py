import json
import unittest

from chat_client.protocol import (
    PROTOCOL_VERSION,
    create_history_request,
    create_text_message,
    decode_response_line,
    encode_line,
)


class ProtocolTests(unittest.TestCase):
    def test_history_request_uses_java_schema(self):
        message = create_history_request("Python")
        self.assertEqual(message["protocolVersion"], 1)
        self.assertEqual(message["type"], "HISTORY_REQUEST")
        self.assertEqual(message["sender"], "Python")
        self.assertIn("id", message)
        self.assertIsNone(message["correlationId"])
        self.assertIn("metadata", message)

    def test_send_message_uses_java_schema_and_newline(self):
        message = create_text_message("Python", "Java", "Ola")
        encoded = encode_line(message)
        self.assertTrue(encoded.endswith(b"\n"))
        decoded = json.loads(encoded.decode("utf-8"))
        self.assertEqual(decoded["protocolVersion"], PROTOCOL_VERSION)
        self.assertEqual(decoded["type"], "SEND_MESSAGE")
        self.assertEqual(decoded["content"], "Ola")

    def test_decode_response(self):
        response = {
            "protocolVersion": 1,
            "id": "server-id",
            "correlationId": "client-id",
            "type": "ACK",
            "sender": "middleware",
            "recipient": "Python",
            "content": "OK",
            "mode": "",
            "color": "",
            "avatar": "",
            "sticker": "",
            "sentAt": "2026-11-19T00:00:00Z",
            "metadata": {"status": "SUCCESS"},
        }
        parsed = decode_response_line(json.dumps(response).encode("utf-8") + b"\n")
        self.assertEqual(parsed["type"], "ACK")

    def test_rejects_wrong_protocol_version(self):
        message = create_history_request("Python")
        message["protocolVersion"] = 2
        with self.assertRaises(ValueError):
            encode_line(message)


if __name__ == "__main__":
    unittest.main()
