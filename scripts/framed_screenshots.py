#!/usr/bin/env python3
"""Builds the framed store-style images in assets/screenshots/ from the test renders.

    ./gradlew recordRoborazziDebug
    python3 scripts/framed_screenshots.py

Needs Pillow. Each image is 1080 x 1920: a short caption above a phone frame around one rendered screen.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/build/screenshots"
OUT = ROOT / "assets/screenshots"

# (test render, output name, caption)
SHOTS = [
    ("home", "home", "A calm home screen"),
    ("drag_mid", "app-list", "Jump to any app"),
    ("fold_app_sheet", "app-menu", "Organize your way"),
    ("gestures_settings", "gestures", "Gestures you choose"),
]

W, H = 1080, 1920
S = 2  # draw at 2x, then scale down for smooth edges
FONTS = [
    "/System/Library/Fonts/SFNS.ttf",
    "/System/Library/Fonts/Helvetica.ttc",
    "/Library/Fonts/Arial Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]


def font(size):
    for path in FONTS:
        try:
            f = ImageFont.truetype(path, size)
            try:
                f.set_variation_by_name("Bold")
            except Exception:
                pass
            return f
        except OSError:
            continue
    return ImageFont.load_default()


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return m


def background():
    img = Image.new("RGB", (W * S, H * S))
    px = ImageDraw.Draw(img)
    for y in range(H * S):  # vertical gradient, lighter at the top
        t = y / (H * S)
        v = int(30 - 20 * t)
        px.line([(0, y), (W * S, y)], fill=(v, v, v))
    glow = Image.new("L", (W * S, H * S), 0)
    ImageDraw.Draw(glow).ellipse((W * S * 0.1, H * S * 0.28, W * S * 0.9, H * S * 0.95), fill=70)
    glow = glow.filter(ImageFilter.GaussianBlur(220 * S))
    return Image.composite(Image.new("RGB", img.size, (52, 52, 54)), img, glow)


def frame(shot_path, caption):
    img = background()
    d = ImageDraw.Draw(img)

    f = font(96 * S)
    tw = d.textlength(caption, font=f)
    d.text(((W * S - tw) / 2, 150 * S), caption, font=f, fill=(244, 244, 244))

    screen_w = 664 * S
    shot = Image.open(shot_path).convert("RGB")
    screen_h = int(screen_w * shot.height / shot.width)
    shot = shot.resize((screen_w, screen_h), Image.LANCZOS)

    bezel = 20 * S
    pw, ph = screen_w + 2 * bezel, screen_h + 2 * bezel
    px0, py0 = (W * S - pw) // 2, 370 * S

    shadow = Image.new("L", img.size, 0)
    ImageDraw.Draw(shadow).rounded_rectangle((px0, py0 + 30 * S, px0 + pw, py0 + ph + 30 * S), radius=96 * S, fill=200)
    shadow = shadow.filter(ImageFilter.GaussianBlur(40 * S))
    img = Image.composite(Image.new("RGB", img.size, (0, 0, 0)), img, shadow)
    d = ImageDraw.Draw(img)

    d.rounded_rectangle((px0, py0, px0 + pw, py0 + ph), radius=96 * S, fill=(22, 22, 24), outline=(120, 120, 126), width=3 * S)
    d.rounded_rectangle((px0 + 5 * S, py0 + 5 * S, px0 + pw - 5 * S, py0 + ph - 5 * S), radius=90 * S, outline=(48, 48, 52), width=2 * S)
    img.paste(shot, (px0 + bezel, py0 + bezel), rounded_mask(shot.size, 72 * S))
    return img.resize((W, H), Image.LANCZOS)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for src, name, caption in SHOTS:
        frame(SRC / f"{src}.png", caption).save(OUT / f"{name}.png", optimize=True)
        print("wrote", OUT / f"{name}.png")


if __name__ == "__main__":
    main()
