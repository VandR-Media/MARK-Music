#!/usr/bin/env python3
"""Install only the retained tube radio image (the other four are removed).

The remaining artwork is packed locally and hash-checked. No Android INTERNET
permission, no CD/MiniDisc/DAT/Jukebox images in the release APK.
The tube radio is a generated photographic concept, not a historic photo.
"""
import base64
import hashlib
from pathlib import Path

BUNDLE = Path("scripts/assets/tube_radio.txt")
OUT = Path("app/src/main/res/drawable-nodpi")
PINS = {
    "tube_radio.webp": "1dd410d57dcfef0a4f650d3703d2eacdf086b4fa8f9e9749eb242ec90aa3f924",
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
        raise ValueError("Missing retained valve radio image")
    OUT.mkdir(parents=True, exist_ok=True)
    for name, raw in parsed.items():
        actual = hashlib.sha256(raw).hexdigest()
        if actual != PINS[name]:
            raise ValueError("Visual asset checksum mismatch: " + name)
        if len(raw) < 8_000 or raw[:4] != b"RIFF" or raw[8:12] != b"WEBP":
            raise ValueError("Invalid WebP image: " + name)
        (OUT / name).write_bytes(raw)
        print(f"Tube radio art {name}: {len(raw)} bytes, SHA-256 verified")

if __name__ == "__main__":
    main()
