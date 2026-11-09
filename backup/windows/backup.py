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


def derive_key(code, salt):
    kdf = PBKDF2HMAC(algorithm=hashes.SHA256(), length=32, salt=salt, iterations=180000)
    return kdf.derive(code.encode("utf-8"))


def restore_session(sock, folder, code, progress):
    """Receiver protocol: WPB2, salt, length-prefixed encrypted manifest, GCM chunks."""
    if read_exact(sock, 4) != b"WPB2":
        raise ValueError("Phone is using a different backup protocol")
    salt = read_exact(sock, 16)
    cipher = AESGCM(derive_key(code, salt))
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
                  "files": results, "expected": len(items),
                  "verified": sum(x["verified"] for x in results)}
        (root / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    progress(f"Finished: {report['verified']} of {len(items)} verified. Report at {root}")
    return report


def run_server(destination, code, progress):
    folder = Path(destination)
    if not folder.is_dir():
        raise RuntimeError("Your selected external drive is not connected")
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind(("0.0.0.0", PORT))
        server.listen(1)
        server.settimeout(180)
        progress(f"Waiting for phone at port {PORT}; use the pairing code shown above")
        conn, address = server.accept()
        with conn:
            conn.settimeout(120)
            progress(f"Phone connected from {address[0]}; receiving encrypted backup")
            return restore_session(conn, folder, code, progress)


def main():
    root = tk.Tk()
    root.title("Wireless Phone Backup 0.2")
    root.geometry("650x330")
    root.resizable(True, False)
    destination = tk.StringVar()
    status = tk.StringVar(value="Select your 8 TB drive folder, then click Backup Now")
    code = tk.StringVar(value=secrets.token_urlsafe(15))

    ttk.Label(root, text="Windows computer IP: " + local_ip()).pack(pady=(12, 4))
    ttk.Label(root, text="External drive folder (choose while the drive is connected)").pack()
    ttk.Entry(root, textvariable=destination, width=82).pack(pady=4)
    ttk.Button(root, text="Browse for external drive folder",
               command=lambda: destination.set(filedialog.askdirectory())).pack()
    ttk.Label(root, text="One-time pairing code (enter this on your Android phone)").pack(pady=(12, 0))
    ttk.Entry(root, textvariable=code, width=40, state="readonly").pack(pady=4)
    ttk.Label(root, text="The phone and PC must be connected to the same Wi-Fi network.").pack()
    button = ttk.Button(root, text="Backup Now")
    button.pack(pady=12)
    ttk.Label(root, textvariable=status, wraplength=610).pack()

    def update_status(message):
        root.after(0, lambda: status.set(message))

    def backup():
        directory = destination.get()
        if not Path(directory).is_dir():
            messagebox.showerror("Drive unavailable", "Connect and choose your external drive folder.")
            return
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
