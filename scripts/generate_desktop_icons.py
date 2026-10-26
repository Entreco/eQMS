#!/usr/bin/env python3
"""Generate desktop icons from branding/logo.png; use --check in CI."""

import argparse
from pathlib import Path
import tempfile

from PIL import Image


BRANDING = Path(__file__).resolve().parents[1] / "apps/desktop/src/main/resources/branding"
ICON_FILES = ("icon.png", "icon.ico", "icon.icns")


def generate(destination):
    with Image.open(BRANDING / "logo.png") as source:
        logo = source.convert("RGBA")
    side = max(logo.size)
    square = Image.new("RGBA", (side, side))
    square.alpha_composite(logo, ((side - logo.width) // 2, (side - logo.height) // 2))
    image = square.resize((1024, 1024), Image.Resampling.LANCZOS)
    image.save(destination / "icon.png")
    image.save(
        destination / "icon.ico",
        sizes=[(size, size) for size in (16, 24, 32, 48, 64, 128, 256)],
    )
    image.save(destination / "icon.icns")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Fail if committed icons differ")
    arguments = parser.parse_args()
    if arguments.check:
        with tempfile.TemporaryDirectory(prefix="dutchdoxx-icons-") as directory:
            destination = Path(directory)
            generate(destination)
            stale = [
                name for name in ICON_FILES
                if not (BRANDING / name).exists()
                or (BRANDING / name).read_bytes() != (destination / name).read_bytes()
            ]
            if stale:
                parser.exit(1, "Regenerate stale desktop icons: " + ", ".join(stale) + "\n")
        print("Desktop icons match branding/logo.png.")
    else:
        generate(BRANDING)
        print("Generated " + ", ".join(ICON_FILES))


if __name__ == "__main__":
    main()
