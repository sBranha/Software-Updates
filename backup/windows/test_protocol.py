"""End-to-end simulated Android server -> Windows receiver tests, no real personal data."""
import hashlib
import json
import socket
import struct
import tempfile
import threading
import time
import unittest
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

from backup import run_backup, read_exact


def transmit_fake_android(server, pin, payload):
    with server:
        conn, address = server.accept()
        with conn:
            conn.settimeout(10)
            private = ec.generate_private_key(ec.SECP256R1())
            public = private.public_key().public_bytes(
                serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
            salt = bytes(range(16))
            conn.sendall(b"WPB3" + struct.pack(">H", len(public)) + public + salt)
            peer_len = struct.unpack(">H", read_exact(conn, 2))[0]
            peer = serialization.load_der_public_key(read_exact(conn, peer_len))
            shared = private.exchange(ec.ECDH(), peer)
            key = HKDF(algorithm=hashes.SHA256(), length=32, salt=salt,
                       info=("WPB3-data-" + pin).encode("ascii")).derive(shared)
            aes = AESGCM(key)
            def encrypt(plaintext, count):
                return aes.encrypt(salt[:4] + struct.pack(">Q", count), plaintext, None)
            manifest = {
                "device": "Synthetic_Galaxy_S23",
                "files": [
                    {"name": "demo-photo.jpg", "category": "files",
                     "sha256": hashlib.sha256(payload).hexdigest(), "size": len(payload)}
                ],
                "unavailable": ["RCS chats not accessible"]
            }
            header = encrypt(json.dumps(manifest).encode("utf-8"), 0)
            body = encrypt(payload, 1)
            conn.sendall(struct.pack(">I", len(header)) + header)
            conn.sendall(struct.pack(">I", len(body)) + body)


class BackupTests(unittest.TestCase):
    def test_phone_server_to_windows_encrypted_file_and_report(self):
        with tempfile.TemporaryDirectory() as root:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
                server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
                server.bind(("127.0.0.1", 38471))
                server.listen(1)
                client = threading.Thread(
                    target=transmit_fake_android, args=(server, "123456", b"sample-photo-bytes"),
                    daemon=True)
                client.start()
                status = []
                report = run_backup(root, "123456", "127.0.0.1", status.append)
                client.join(timeout=10)
                self.assertFalse(client.is_alive(), "Simulated phone server hung")
                self.assertEqual(report["verified"], 1)
                self.assertEqual(report["expected"], 1)
                self.assertEqual(report["unavailable"], ["RCS chats not accessible"])
                files = list(Path(root).rglob("*demo-photo.jpg"))
                self.assertEqual(len(files), 1)
                self.assertEqual(files[0].read_bytes(), b"sample-photo-bytes")
                self.assertEqual(len(list(Path(root).rglob("report.json"))), 1)
                self.assertTrue(any("Connected to phone!" in message for message in status))

    def test_bad_code_rejected_before_connecting(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaisesRegex(RuntimeError, "6-digit"):
                run_backup(root, "123", "127.0.0.1", lambda _: None)

    def test_missing_backup_drive_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "external drive"):
            run_backup(r"Z:\\NO-DRIVE-EXISTS", "123456", "127.0.0.1", lambda _: None)


if __name__ == "__main__":
    unittest.main()
