"""Wireless Phone Backup receiver -- prototype 0.2, local encrypted one-shot backup."""
import hashlib
import json
import os
import re
import secrets
import socket
import struct
import threading
import tkinter as tk
from datetime import datetime
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes

PORT = 38471
MAX_HEADER = 16 * 1024 * 1024
MAX_FILE = 100 * 1024 ** 3
MAX_CHUNK = 1024 * 1024


def read_exact(sock, count):
    data = bytearray()
    while len(data) < count:
        chunk = sock.recv(min(count - len(data), 65536))
        if not chunk:
            raise EOFError("Phone disconnected before finishing its backup")
        data.extend(chunk)
    return bytes(data)


def read_u32(sock):
    return struct.unpack(">I", read_exact(sock, 4))[0]


def safe_segment(value):
    name = re.sub(r"[^a-zA-Z0-9_.-]+", "_", str(value))[:120].strip(".")
    return name or "unnamed"


def local_ip():
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.connect(("192.0.2.1", 80))
            return sock.getsockname()[0]
    except OSError:
        return "Check ipconfig for your Wi-Fi IPv4 address"


def session_key(sock, code):
    """Ephemeral ECDH P-256 session with a 6-digit pairing code."""
    if read_exact(sock, 4) != b"WPB3":
        raise ValueError("Update both apps to version 0.3 before connecting")
    n = struct.unpack(">H", read_exact(sock, 2))[0]
    if not 60 <= n <= 256:
        raise ValueError("Invalid public key length")
    peer = serialization.load_der_public_key(read_exact(sock, n))
    if not isinstance(peer, ec.EllipticCurvePublicKey) or not isinstance(peer.curve, ec.SECP256R1):
        raise ValueError("Invalid phone public key")
    salt = read_exact(sock, 16)
    private = ec.generate_private_key(ec.SECP256R1())
    pub = private.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    sock.sendall(struct.pack(">H", len(pub)) + pub)
    shared = private.exchange(ec.ECDH(), peer)
    key = HKDF(algorithm=hashes.SHA256(), length=32, salt=salt,
               info=("WPB3-data-" + code).encode("ascii")).derive(shared)
    return AESGCM(key), salt


def restore_session(sock, folder, code, progress):
    """Receiver protocol WPB3, ECDH, AES GCM."""
    cipher, salt = session_key(sock, code)
    counter = 0

    def decrypt(ciphertext):
        nonlocal counter
        nonce = salt[:4] + struct.pack(">Q", counter)
        counter += 1
        return cipher.decrypt(nonce, ciphertext, None)

    header_length = read_u32(sock)
    if not 16 <= header_length <= MAX_HEADER:
        raise ValueError("Invalid manifest length")
    manifest = json.loads(decrypt(read_exact(sock, header_length)))
    items = manifest["files"]
    if not isinstance(items, list) or len(items) > 25000:
        raise ValueError("Invalid file count")
    device = safe_segment(manifest.get("device", "Android"))
    root = folder / "PhoneBackups" / device / datetime.now().strftime("%Y%m%d-%H%M%S")
    if root.exists():
        root = root.with_name(root.name + "-" + secrets.token_hex(3))
    root.mkdir(parents=True, exist_ok=False)
    results = []
    try:
        for index, info in enumerate(items, 1):
            size = int(info["size"])
            if not 0 <= size <= MAX_FILE:
                raise ValueError("Invalid file size")
            category = safe_segment(info.get("category", "files"))
            filename = safe_segment(info["name"])
            target = root / category / f"{index:05d}_{filename}"
            target.parent.mkdir(parents=True, exist_ok=True)
            partial = target.with_name(target.name + ".part")
            hasher = hashlib.sha256()
            received = 0
            with partial.open("wb") as out:
                while received < size:
                    encrypted_size = read_u32(sock)
                    if not 17 <= encrypted_size <= MAX_CHUNK + 16:
                        raise ValueError("Invalid encrypted chunk length")
                    chunk = decrypt(read_exact(sock, encrypted_size))
                    if len(chunk) > size - received:
                        raise ValueError("File exceeded advertised size")
                    out.write(chunk)
                    hasher.update(chunk)
                    received += len(chunk)
            verified = (hasher.hexdigest() == info["sha256"])
            if verified:
                partial.rename(target)
            else:
                partial.rename(target.with_name(target.name + ".invalid"))
            results.append({"name": filename, "category": category, "bytes": received,
                            "verified": verified})
            progress(f"{index}/{len(items)} saved: {filename} ({'verified' if verified else 'FAILED CHECK'})")
    finally:
        report = {"device": device, "saved_at": datetime.now().isoformat(),
                  "files": results, "unavailable": manifest.get("unavailable", []), "expected": len(items),
                  "verified": sum(x["verified"] for x in results)}
        (root / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    progress("Finished: " + str(report["verified"]) + "/" + str(len(items)) + " verified; " + str(len(report["unavailable"])) + " limitations. Report at " + str(root))
    return report


def discovery_service(stop):
    """Respond to local Wi-Fi discovery only while a backup is waiting."""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as udp:
            udp.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            udp.bind(("0.0.0.0", 38472))
            udp.settimeout(1)
            while not stop.is_set():
                try:
                    request, address = udp.recvfrom(256)
                    if request == b"WPB_DISCOVER_V3":
                        udp.sendto(b"WPB_READY_V3", address)
                except socket.timeout:
                    pass
    except OSError:
        return

def run_server(destination, code, progress):
    folder = Path(destination)
    if not str(destination).strip() or not folder.is_dir():
        raise RuntimeError("Your selected external drive is not connected")
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind(("0.0.0.0", PORT))
        server.listen(1)
        server.settimeout(300)
        progress(f"Waiting for phone. On Android tap FIND COMPUTER, then BACKUP NOW.")
        stop_discovery = threading.Event()
        threading.Thread(target=discovery_service, args=(stop_discovery,), daemon=True).start()
        try:
            conn, address = server.accept()
        finally:
            stop_discovery.set()
        with conn:
            conn.settimeout(120)
            progress(f"Phone connected from {address[0]}; receiving encrypted backup")
            return restore_session(conn, folder, code, progress)


def main():
    root = tk.Tk()
    root.title("Wireless Phone Backup 0.3")
    root.geometry("840x590")
    root.minsize(780, 540)
    style = ttk.Style(root)
    style.configure(".", font=("Segoe UI", 15))
    style.configure("Big.TButton", font=("Segoe UI", 18, "bold"), padding=(18, 12))
    style.configure("Big.TLabel", font=("Segoe UI", 16))
    style.configure("Code.TLabel", font=("Segoe UI", 28, "bold"))
    destination = tk.StringVar()
    status = tk.StringVar(value="Select your 8 TB drive folder, then click Backup Now")
    code = tk.StringVar(value=f"{secrets.randbelow(1000000):06d}")

    ttk.Label(root, text="1. Connect your 8 TB drive and choose its folder", style="Big.TLabel").pack(pady=(16, 5))
    ttk.Entry(root, textvariable=destination, width=65, font=("Segoe UI", 16)).pack(pady=6)
    ttk.Button(root, text="Choose External Drive Folder", style="Big.TButton",
               command=lambda: destination.set(filedialog.askdirectory())).pack(pady=4)
    ttk.Label(root, text="2. Windows computer IP: " + local_ip(), style="Big.TLabel").pack(pady=(18, 2))
    ttk.Label(root, text="3. Type this six-digit pairing code on your phone", style="Big.TLabel").pack(pady=(10, 2))
    ttk.Label(root, textvariable=code, style="Code.TLabel").pack(pady=5)
    ttk.Label(root, text="4. Press BACKUP NOW here, then FIND COMPUTER / BACKUP NOW on the phone.",
              style="Big.TLabel", wraplength=780).pack(pady=(4, 8))
    button = ttk.Button(root, text="BACKUP NOW", style="Big.TButton")
    button.pack(pady=10)
    bar = ttk.Progressbar(root, length=740, maximum=100)
    bar.pack(pady=6)
    ttk.Label(root, textvariable=status, font=("Segoe UI", 15, "bold"), wraplength=790).pack(pady=6)
    ttk.Label(root, text="If Windows Firewall asks, allow access on Private networks.", font=("Segoe UI", 12)).pack(pady=3)

    def update_status(message):
        def update():
            status.set(message)
            match = re.search(r"(\d+)/(\d+) saved", message)
            if match and int(match.group(2)):
                bar["value"] = round(100 * int(match.group(1)) / int(match.group(2)))
            elif message.startswith("Finished:"):
                match = re.search(r"Finished: (\d+)/(\d+)", message)
                if match and int(match.group(2)):
                    bar["value"] = round(100 * int(match.group(1)) / int(match.group(2)))
        root.after(0, update)

    def backup():
        directory = destination.get()
        if not directory.strip() or not Path(directory).is_dir():
            messagebox.showerror("Drive unavailable", "Connect and choose your external drive folder.")
            return
        bar["value"] = 0
        button.config(state="disabled")
        def worker():
            try:
                run_server(directory, code.get(), update_status)
            except Exception as error:
                update_status("Backup failed: " + str(error))
            finally:
                root.after(0, lambda: button.config(state="normal"))
        threading.Thread(target=worker, daemon=True).start()

    button.configure(command=backup)
    root.mainloop()


if __name__ == "__main__":
    main()
