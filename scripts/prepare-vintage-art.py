#!/usr/bin/env python3
"""Install the FIVE user-selected generated equipment photographs into Android.

Source art is committed as UTF-8 Base64 instead of untrusted build-time network
downloads. This preserves the exact designs selected by the user (CD, MD, DAT,
tube radio, jukebox), without INTERNET permission or any runtime network usage.

The images are original generated concept renders; they are *not* historical
manufacturer photographs. They are composited with dynamic on-device hardware
movements, metadata displays and measured PCM meters in Compose.
"""
import base64
import hashlib
from pathlib import Path

BUNDLE = Path("scripts/assets/five_vintage_decks.txt")
OUT = Path("app/src/main/res/drawable-nodpi")
PINS = {
    "cd_deck.webp": "8cd2fc37e4a7336568feed873612a6cf160fb0c63a4568b16f9c7900a363ba45",
    "minidisc_deck.webp": "d0f30f3bb803d035c5fe9740786ea11fd11dc053c8225ad080ee3b2515729689",
    "dat_deck.webp": "a4524dd42dcd05be5c2f6b1a60efed1aeefa46a524d2637690b05d61f8953a69",
    "tube_radio.webp": "1dd410d57dcfef0a4f650d3703d2eacdf086b4fa8f9e9749eb242ec90aa3f924",
    "jukebox.webp": "3574fb5ff099fe3df19157a38846fecc094676040094a7baff56a219afe00226",
}

def main():
    current = None
    parts = []
    parsed = {}
    for line in BUNDLE.read_text(encoding="ascii").splitlines():
        if not line or line.startswith("#"):
            continue
        if line.startswith(">asset "):
            if current is not None:
                parsed[current] = base64.b64decode("".join(parts), validate=True)
            _, filename, digest = line.split()
            if filename not in PINS or PINS[filename] != digest:
                raise ValueError("Unapproved vintage art asset: " + filename)
            if filename in parsed:
                raise ValueError("Duplicate art asset: " + filename)
            current, parts = filename, []
        else:
            if current is None:
                raise ValueError("Image data without asset heading")
            parts.append(line)
    if current is not None:
        parsed[current] = base64.b64decode("".join(parts), validate=True)
    if parsed.keys() != PINS.keys():
        raise ValueError("Missing generated equipment photo assets")
    OUT.mkdir(parents=True, exist_ok=True)
    for name, raw in parsed.items():
        actual = hashlib.sha256(raw).hexdigest()
        if actual != PINS[name]:
            raise ValueError("Visual asset checksum mismatch: " + name)
        if len(raw) < 8_000 or raw[:4] != b"RIFF" or raw[8:12] != b"WEBP":
            raise ValueError("Invalid WebP image: " + name)
        (OUT / name).write_bytes(raw)
        print(f"Photo art {name}: {len(raw)} bytes, SHA-256 verified")

if __name__ == "__main__":
    main()
