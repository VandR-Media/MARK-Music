#!/usr/bin/env python3
"""Pack the owner's straight-on Весна-202-1 cassette-recorder reference image.

The file is checked by SHA-256 because both small reel animations are registered
to fixed, measured positions on this EXACT picture. If the photo changes,
fail the build instead of shipping rotating pieces of unrelated scenery.

Photo originally supplied by the app owner, sourced from the Vintage Soviet
Audio Equipment Museum's Весна-202-1 entry (rw6ase.narod.ru).
This is an internal prototype; public redistribution rights of the photograph
must be resolved before publishing the app in a store.
The installed player has no network permissions.
"""
import hashlib
from pathlib import Path
import struct
import urllib.parse
import urllib.request

OUTPUT = Path("app/src/main/res/drawable-nodpi/vesna_202_photo.jpg")
SOURCE = "https://rw6ase.narod.ru/index1/mag_ml/mg_kas_nos_/wesna202_1_3.jpg"
URLS = (
    SOURCE,
    "https://images.weserv.nl/?url=" + urllib.parse.quote(SOURCE.split("://", 1)[1], safe="") + "&output=jpg",
    "https://wsrv.nl/?url=" + urllib.parse.quote(SOURCE, safe="") + "&output=jpg",
)
EXPECTED_SHA256 = "0355ae7ef63bcae98d979f34dc8a40ec3c7153129d17b2c66771822019a37877"
EXPECTED_SIZE = (1024, 1009)
MAX_BYTES = 3_000_000


def jpeg_dimensions(raw):
    if not raw.startswith(b"\xff\xd8") or not raw.endswith(b"\xff\xd9"):
        raise ValueError("Not a complete JPEG")
    pos = 2
    while pos + 9 < len(raw):
        if raw[pos] != 255:
            pos += 1
            continue
        while pos < len(raw) and raw[pos] == 255:
            pos += 1
        marker = raw[pos]
        pos += 1
        if marker in (0xD8, 0x01) or 0xD0 <= marker <= 0xD7:
            continue
        if pos + 2 > len(raw):
            break
        length = struct.unpack(">H", raw[pos:pos+2])[0]
        if length < 2 or pos + length > len(raw):
            break
        if marker in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
            return tuple(struct.unpack(">HH", raw[pos+3:pos+7])[::-1])
        pos += length
    raise ValueError("Missing JPEG dimensions")


def validate(raw):
    if not 50_000 < len(raw) < MAX_BYTES:
        raise ValueError(f"Unexpected image size: {len(raw)} bytes")
    actual = jpeg_dimensions(raw)
    if actual != EXPECTED_SIZE:
        raise ValueError(f"Wrong photograph geometry: {actual}, expected {EXPECTED_SIZE}")
    digest = hashlib.sha256(raw).hexdigest()
    if digest != EXPECTED_SHA256:
        raise ValueError("Photo checksum mismatch: expected user-supplied Весна-202-1")
    return digest


def main():
    if OUTPUT.exists():
        try:
            digest = validate(OUTPUT.read_bytes())
            print(f"Using approved Весна-202-1 photograph {EXPECTED_SIZE}, SHA256={digest}")
            return
        except ValueError:
            OUTPUT.unlink()
    errors = []
    for url in URLS:
        try:
            request = urllib.request.Request(url, headers={
                "User-Agent": "Mozilla/5.0 (compatible; MARKAudio/0.7.9; private music visualizer)",
                "Accept": "image/jpeg,*/*;q=0.7"
            })
            with urllib.request.urlopen(request, timeout=55) as response:
                raw = response.read(MAX_BYTES + 1)
            digest = validate(raw)
            OUTPUT.parent.mkdir(parents=True, exist_ok=True)
            OUTPUT.write_bytes(raw)
            print(f"Весна-202-1 photograph ready: {len(raw)} bytes, {EXPECTED_SIZE}, SHA256={digest}")
            return
        except Exception as exc:
            errors.append(f"{url}: {type(exc).__name__}: {exc}")
    raise RuntimeError("Unable to package correct Весна-202-1 photo, refusing fallback artwork.\n"
                       + "\n".join(errors))


if __name__ == "__main__":
    main()
