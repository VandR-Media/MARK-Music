#!/usr/bin/env python3
"""Package a public-domain photograph of a genuine Soviet Mayak-203.

The photograph is fetched only on the trusted build runner and is bundled
inside the APK. The running Android app needs no network access.
Source: https://commons.wikimedia.org/wiki/File:Majak203.jpg
Photographer: Viktor Khodyeyev (Віктор Ходєєв), 2018.
License: public-domain dedication by the photographer.
"""
from pathlib import Path
import hashlib
import struct
import urllib.request

OUTPUT = Path("app/src/main/res/drawable-nodpi/mayak_photo.jpg")
# Official public-domain original is mirrored by a deterministic 1600px JPEG
# proxy to avoid Wikimedia's GitHub-runner HTTP 429 rate limit.
URLS = (
    "https://images.weserv.nl/?url=upload.wikimedia.org%2Fwikipedia%2Fcommons%2Fc%2Fc1%2FMajak203.jpg&w=1600&output=jpg",
    "https://wsrv.nl/?url=https%3A%2F%2Fupload.wikimedia.org%2Fwikipedia%2Fcommons%2Fc%2Fc1%2FMajak203.jpg&w=1600&output=jpg",
)
EXPECTED_SHA256 = "86bed8bfc18ac6504eae2ff4ad50f438b008ce76a985f2ea3c9af3daf669a138"
MAX_BYTES = 3_000_000


def jpeg_dimensions(raw):
    """Read JPEG's actual dimensions without adding a Python dependency."""
    if not raw.startswith(b"\xff\xd8"):
        raise ValueError("Asset is not a JPEG")
    pos = 2
    while pos + 9 < len(raw):
        if raw[pos] != 0xFF:
            pos += 1
            continue
        while pos < len(raw) and raw[pos] == 0xFF:
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
            height, width = struct.unpack(">HH", raw[pos+3:pos+7])
            return width, height
        pos += length
    raise ValueError("Cannot extract JPEG dimensions")


def download():
    # Support offline Gradle rebuilds after the photo has been downloaded once.
    if OUTPUT.is_file():
        cached = OUTPUT.read_bytes()
        if 50_000 < len(cached) < MAX_BYTES and cached.endswith(b"\xff\xd9"):
            try:
                w, h = jpeg_dimensions(cached)
                if (w, h) == (1600, 1545) and hashlib.sha256(cached).hexdigest() == EXPECTED_SHA256:
                    print(f"Using cached Mayak-203 photograph ({w}x{h})")
                    return
            except ValueError:
                pass
    errors = []
    for url in URLS:
        try:
            req = urllib.request.Request(url, headers={
                "User-Agent": "MARK-Audio/0.7.7 (open-source music player; Wikimedia Commons public-domain asset)",
                "Accept": "image/jpeg,*/*;q=0.5",
            })
            with urllib.request.urlopen(req, timeout=65) as response:
                data = response.read(MAX_BYTES + 1)
            if not 50_000 < len(data) < MAX_BYTES:
                raise ValueError(f"Unexpected photo size: {len(data)} bytes")
            if not data.endswith(b"\xff\xd9"):
                raise ValueError("Truncated JPEG asset")
            width, height = jpeg_dimensions(data)
            if (width, height) != (1600, 1545):
                raise ValueError(f"Wrong photo dimensions: {width}x{height}")
            digest = hashlib.sha256(data).hexdigest()
            if digest != EXPECTED_SHA256:
                raise ValueError("Photograph SHA-256 differs from previously verified CI release asset")
            OUTPUT.parent.mkdir(parents=True, exist_ok=True)
            OUTPUT.write_bytes(data)
            print(f"Mayak-203 photograph: {width}x{height}, {len(data)} bytes")
            print(f"Photographic source: {url}")
            print(f"Photo SHA-256: {hashlib.sha256(data).hexdigest()}")
            return
        except Exception as exc:
            errors.append(f"{url}: {type(exc).__name__}: {exc}")
    raise RuntimeError(
        "Unable to package the Mayak-203 photograph; refusing an APK with an "
        "empty or vector placeholder.\n" + "\n".join(errors)
    )


if __name__ == "__main__":
    download()
