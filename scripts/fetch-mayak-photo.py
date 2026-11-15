#!/usr/bin/env python3
"""Bundle a fixed, straight-on photograph of a Mayak-203 control deck.

This is deliberately NOT the angled Wikimedia Majak203.jpg photo: that is the
cause of the giant rotating lid/background artifacts in 0.7.7.
The user supplied this exact front-on reference image for this private app.
Source: Virtual Museum of Soviet Audio Equipment (rw6ase.narod.ru), Mayak-203
Olympics-80 variant, photo attributed on the source page to its contributors.
The finished Android app does not download anything or request INTERNET.
"""
from pathlib import Path
import hashlib
import struct
import urllib.parse
import urllib.request

OUTPUT = Path("app/src/main/res/drawable-nodpi/mayak_photo.jpg")
SOURCE = "https://rw6ase.narod.ru/index1/mag_ml/mg_kat_set_/majak203w2.jpg"
URLS = (
    SOURCE,
    "https://images.weserv.nl/?url=" + urllib.parse.quote(SOURCE.split("://", 1)[1], safe="") + "&output=jpg",
    "https://wsrv.nl/?url=" + urllib.parse.quote(SOURCE, safe="") + "&output=jpg",
)
# Stable source photograph: no resizing/cropping by CDN, so geometric reel
# positions always match the image. Check source SHA after first verified CI.
EXPECTED_SOURCE_SHA256 = None
MAX_BYTES = 5_000_000


def jpeg_dimensions(raw: bytes) -> tuple[int, int]:
    if not raw.startswith(b"\xff\xd8") or not raw.endswith(b"\xff\xd9"):
        raise ValueError("Not a complete JPEG")
    pos = 2
    while pos + 9 < len(raw):
        if raw[pos] != 0xff:
            pos += 1
            continue
        while pos < len(raw) and raw[pos] == 0xff:
            pos += 1
        marker = raw[pos]
        pos += 1
        if marker in (0xd8, 0x01) or 0xd0 <= marker <= 0xd7:
            continue
        if pos + 2 >= len(raw):
            break
        length = struct.unpack(">H", raw[pos:pos+2])[0]
        if length < 2 or pos + length > len(raw):
            break
        if marker in (0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf):
            height, width = struct.unpack(">HH", raw[pos+3:pos+7])
            return width, height
        pos += length
    raise ValueError("Cannot read JPEG dimensions")


def valid_image(raw: bytes) -> tuple[int, int, str]:
    if not 80_000 <= len(raw) < MAX_BYTES:
        raise ValueError(f"Unexpected photo size: {len(raw)}")
    w, h = jpeg_dimensions(raw)
    if not 1.27 < w / h < 1.33 or w < 1100 or h < 850:
        raise ValueError(f"Not the straight-on Mayak front panel: {w}x{h}")
    digest = hashlib.sha256(raw).hexdigest()
    if EXPECTED_SOURCE_SHA256 and digest != EXPECTED_SOURCE_SHA256:
        raise ValueError("Reference photograph digest changed")
    return w, h, digest


def download() -> None:
    if OUTPUT.is_file():
        try:
            w, h, digest = valid_image(OUTPUT.read_bytes())
            print(f"Using verified top-view Mayak source {w}x{h}: {digest}")
            return
        except ValueError:
            OUTPUT.unlink()
    errors = []
    for url in URLS:
        try:
            req = urllib.request.Request(url, headers={
                "User-Agent": "Mozilla/5.0 (compatible; MarkAudio/0.7.8; noncommercial reference asset)",
                "Accept": "image/jpeg,*/*;q=0.8",
            })
            with urllib.request.urlopen(req, timeout=48) as response:
                data = response.read(MAX_BYTES + 1)
            w, h, digest = valid_image(data)
            OUTPUT.parent.mkdir(parents=True, exist_ok=True)
            OUTPUT.write_bytes(data)
            print(f"Mayak front view {w}x{h}, {len(data)} bytes, SHA256: {digest}")
            print(f"Photographic source: {url}")
            return
        except Exception as exc:
            errors.append(f"{url}: {type(exc).__name__}: {exc}")
    raise RuntimeError("Unable to acquire an approved straight-on photograph. "
                       "Refusing to package the previous angled photo.\n" +
                       "\n".join(errors))


if __name__ == "__main__":
    download()
