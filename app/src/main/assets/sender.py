#!/usr/bin/env python3
"""
ZeroLink PC Sender
Stream your Windows / macOS / Linux desktop to the ZeroLink Android app.

Requirements
    pip install mss Pillow

Usage
    python sender.py <PIN>

    PIN  is shown in ZeroLink app  ->  Mirror PC  ->  Setup Mirror.
    Start this script first, note the IP it prints, then enter
    that IP and the PIN in the app.
"""

import io
import socket
import struct
import sys
import time

try:
    import mss
except ImportError:
    sys.exit("mss not found.  Run:  pip install mss Pillow")

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow not found.  Run:  pip install mss Pillow")

PORT = 5000
MAX_DIM = 1280
JPEG_QUALITY = 60
TARGET_FPS = 24


def _local_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


def _capture(sct, monitor) -> bytes:
    raw = sct.grab(monitor)
    img = Image.frombytes("RGB", (raw.width, raw.height), raw.bgra, "raw", "BGRX")
    w, h = img.size
    longest = max(w, h)
    if longest > MAX_DIM:
        scale = MAX_DIM / longest
        nw = int(w * scale) & ~1
        nh = int(h * scale) & ~1
        img = img.resize((nw or 2, nh or 2), Image.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=JPEG_QUALITY)
    return buf.getvalue()


def main(pin: str) -> None:
    ip = _local_ip()
    sep = "-" * 34
    print(f"\n  ZeroLink PC Sender")
    print(f"  {sep}")
    print(f"  IP   {ip}")
    print(f"  PIN  {pin}")
    print(f"  Port {PORT}")
    print(f"  {sep}")
    print(f"  Open ZeroLink -> Mirror PC -> Setup Mirror")
    print(f"  and enter the IP and PIN shown above.")
    print(f"\n  Waiting for connection...")

    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", PORT))
    srv.listen(1)

    try:
        conn, addr = srv.accept()
    except KeyboardInterrupt:
        print("\n  Cancelled.")
        srv.close()
        return

    print(f"  Phone connected: {addr[0]}")

    try:
        received = conn.recv(6).decode().strip()
    except Exception as e:
        print(f"  Auth error: {e}")
        conn.close()
        srv.close()
        return

    if received != pin:
        print(f"  Wrong PIN '{received}'.  Closing.")
        conn.close()
        srv.close()
        return

    print("  Authenticated! Streaming desktop... (Ctrl-C to stop)\n")

    interval = 1.0 / TARGET_FPS
    last = 0.0

    with mss.mss() as sct:
        monitor = sct.monitors[1]
        try:
            while True:
                now = time.monotonic()
                wait = interval - (now - last)
                if wait > 0:
                    time.sleep(wait)
                last = time.monotonic()
                frame = _capture(sct, monitor)
                try:
                    conn.sendall(struct.pack(">I", len(frame)) + frame)
                except (BrokenPipeError, ConnectionResetError, OSError):
                    print("  Phone disconnected.")
                    break
        except KeyboardInterrupt:
            print("\n  Stopped.")

    conn.close()
    srv.close()


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage:  python sender.py <PIN>")
        print("        The PIN is shown in ZeroLink -> Mirror PC -> Setup Mirror.")
        sys.exit(1)
    main(pin=sys.argv[1].strip())
