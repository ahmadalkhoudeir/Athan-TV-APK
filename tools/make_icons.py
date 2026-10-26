"""Draws the launcher icons and the Android TV banner (original artwork: crescent + dome)."""
import os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = sys.argv[1]
GOLD = (226, 194, 117)
TOP, BOT = (8, 52, 42), (1, 16, 13)
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

def gradient(w, h):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(TOP[i] + (BOT[i] - TOP[i]) * t) for i in range(3)))
    return img

def emblem(size):
    """Gold crescent with a small star and a dome silhouette, drawn at 4x then downsampled."""
    S = size * 4
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # Dome + base
    d.pieslice([S*0.22, S*0.42, S*0.78, S*0.98], 180, 360, fill=GOLD)
    d.rectangle([S*0.22, S*0.70, S*0.78, S*0.86], fill=GOLD)
    d.rectangle([S*0.46, S*0.74, S*0.54, S*0.86], fill=(0, 0, 0, 0))  # door
    # Finial
    d.rectangle([S*0.49, S*0.30, S*0.51, S*0.44], fill=GOLD)
    # Crescent above finial
    r = S*0.11; cx, cy = S*0.50, S*0.20
    d.ellipse([cx-r, cy-r, cx+r, cy+r], fill=GOLD)
    d.ellipse([cx-r+S*0.05, cy-r-S*0.02, cx+r+S*0.05, cy+r-S*0.02], fill=(0, 0, 0, 0))
    return im.resize((size, size), Image.LANCZOS)

def icon(px):
    bg = gradient(px, px).convert("RGBA")
    mask = Image.new("L", (px*4, px*4), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, px*4-1, px*4-1], radius=px*0.9, fill=255)
    mask = mask.resize((px, px), Image.LANCZOS)
    e = emblem(int(px*0.78))
    bg.alpha_composite(e, ((px - e.width)//2, (px - e.height)//2 + int(px*0.02)))
    out = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    out.paste(bg, (0, 0), mask)
    return out

for folder, px in {"mipmap-mdpi": 48, "mipmap-hdpi": 72, "mipmap-xhdpi": 96,
                   "mipmap-xxhdpi": 144, "mipmap-xxxhdpi": 192}.items():
    os.makedirs(f"{ROOT}/{folder}", exist_ok=True)
    icon(px).save(f"{ROOT}/{folder}/ic_launcher.png")

# 320x180 TV banner (drawable-xhdpi)
W, H = 320, 180
b = gradient(W, H).convert("RGBA")
e = emblem(120)
b.alpha_composite(e, (18, 30))
d = ImageDraw.Draw(b)
d.text((150, 52), "Athan", font=ImageFont.truetype(BOLD, 40), fill=GOLD)
d.text((152, 98), "TV", font=ImageFont.truetype(BOLD, 34), fill=(255, 255, 255))
d.text((152, 142), "Prayer times", font=ImageFont.truetype(BOLD, 13), fill=(183, 201, 194))
os.makedirs(f"{ROOT}/drawable-xhdpi", exist_ok=True)
b.convert("RGB").save(f"{ROOT}/drawable-xhdpi/banner.png")
print("ok")
