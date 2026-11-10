"""Protocol smoke tests: synthetic files only, no personal phone data."""
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

from backup import run_server, read_exact, discovery_service


def u32(n):
    return struct.pack(">I", n)


def simulate_android(pin, payload):
    client = ec.generate_private_key(ec.SECP256R1())
    public = client.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    salt = bytes(range(16))
    sock = None
    for attempt in range(50):
        try:
            sock = socket.create_connection(("127.0.0.1", 38471), 0.2)
            break
        except (OSError, ConnectionRefusedError):
            time.sleep(0.1)
    if sock is None:
        raise ConnectionError("Windows receiver did not listen")
    with sock:
        sock.sendall(b"WPB3" + struct.pack(">H", len(public)) + public + salt)
        public_len = struct.unpack(">H", read_exact(sock, 2))[0]
        remote = serialization.load_der_public_key(read_exact(sock, public_len))
        shared = client.exchange(ec.ECDH(), remote)
        key = HKDF(algorithm=hashes.SHA256(), length=32, salt=salt,
                   info=("WPB3-data-" + pin).encode("ascii")).derive(shared)
        cipher = AESGCM(key)
        def encrypted(data, counter):
            return cipher.encrypt(salt[:4] + struct.pack(">Q", counter), data, None)
        manifest = {"device":"Test_Galaxy_S23",
                    "files":[{"category":"files", "name":"photo.jpg", "size":len(payload),
                              "sha256":hashlib.sha256(payload).hexdigest()}],
                    "unavailable":["RCS not accessible"]}
        head = encrypted(json.dumps(manifest).encode("utf-8"), 0)
        sock.sendall(u32(len(head)) + head)
        body = encrypted(payload, 1)
        sock.sendall(u32(len(body)) + body)


class TestBackup(unittest.TestCase):
    def test_discovery_tcp_and_udp(self):
        """Phone should discover PC without consuming the file-transfer connection."""
        stop = threading.Event()
        thread = threading.Thread(target=discovery_service, args=(stop,), daemon=True)
        thread.start()
        try:
            reply = None
            for attempt in range(30):
                try:
                    with socket.create_connection(("127.0.0.1", 38473), timeout=0.3) as connection:
                        connection.settimeout(2)
                        connection.sendall(b"WPB_STATUS_V4")
                        reply = connection.recv(64)
                    break
                except OSError:
                    time.sleep(0.1)
            self.assertEqual(reply, b"WPB_READY_V4", "Direct TCP status not responding")
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as udp:
                udp.settimeout(2)
                udp.sendto(b"WPB_DISCOVER_V4", ("127.0.0.1", 38472))
                response, _ = udp.recvfrom(64)
                self.assertEqual(response, b"WPB_READY_V4", "UDP discovery not responding")
        finally:
            stop.set()
            thread.join(3)
            self.assertFalse(thread.is_alive(), "Discovery listener failed to close")

    def test_six_digit_secure_transfer_and_report(self):
        with tempfile.TemporaryDirectory() as temp:
            values = []
            def worker():
                try:
                    values.append(run_server(temp, "123456", lambda msg: None))
                except Exception as err:
                    values.append(err)
            thread = threading.Thread(target=worker, daemon=True)
            thread.start()
            simulate_android("123456", b"hello\x00world")
            thread.join(15)
            self.assertFalse(thread.is_alive(), "Windows receiver hung")
            self.assertEqual(len(values), 1)
            if isinstance(values[0], Exception):
                raise values[0]
            report=values[0]
            self.assertEqual(report["verified"], 1)
            self.assertEqual(report["expected"], 1)
            self.assertEqual(report["unavailable"], ["RCS not accessible"])
            saved=list(Path(temp).rglob("*photo.jpg"))
            self.assertEqual(len(saved), 1)
            self.assertEqual(saved[0].read_bytes(), b"hello\x00world")
            self.assertTrue(list(Path(temp).rglob("report.json")))


if __name__ == "__main__":
    unittest.main()
