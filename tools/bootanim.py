#!/usr/bin/env python3
"""Genera la animacion de arranque del Sail (bootanimation.zip) para la pantalla 1024x600.

Parte 0 (una vez, ~2 s): el Sail aparece desde la oscuridad y se encienden los faros.
Parte 1 (en bucle hasta que Android termina de cargar): brillo suave de los faros.

Uso:  py tools\\bootanim.py            -> build\\bootanimation.zip
      py tools\\bootanim.py salida.zip
Requisitos: Python 3 y Pillow (py -m pip install pillow).
Se instala con tools\\windows\\animacion.bat (T17).
"""
import io
import math
import os
import sys
import zipfile

from PIL import Image, ImageEnhance

W, H, FPS = 1024, 600, 15
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BG = (8, 10, 18)


def frame(car, lights, k_car, k_light):
    """k_car: 0..1 visibility of the car; k_light: 0..1 headlight glow."""
    img = Image.new("RGB", (W, H), BG)
    c = ImageEnhance.Brightness(car).enhance(0.15 + 0.85 * k_car)
    l = ImageEnhance.Brightness(lights).enhance(0.15 + 0.85 * k_car)
    body = Image.blend(c, l, k_light) if k_light > 0 else c
    alpha = car.split()[3].point(lambda a: int(a * k_car))
    img.paste(body.convert("RGB"), ((W - car.width) // 2, (H - car.height) // 2 + 20), alpha)
    return img


def jpg(img):
    b = io.BytesIO()
    img.save(b, "JPEG", quality=85, optimize=True)
    return b.getvalue()


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "build", "bootanimation.zip")
    res = os.path.join(ROOT, "res", "drawable-nodpi")
    car = Image.open(os.path.join(res, "sail.png")).convert("RGBA")
    lights = Image.open(os.path.join(res, "sail_lights.png")).convert("RGBA")
    scale = min(W * 0.8 / car.width, H * 0.8 / car.height)
    size = (int(car.width * scale), int(car.height * scale))
    car, lights = car.resize(size, Image.LANCZOS), lights.resize(size, Image.LANCZOS)

    part0 = []
    for i in range(30):  # 2 s: fade in (first 1.2 s), then headlights on
        t = i / 29
        part0.append(frame(car, lights, min(1, t / 0.6), max(0, (t - 0.6) / 0.4)))
    part1 = []
    for i in range(20):  # gentle pulse while Android loads
        part1.append(frame(car, lights, 1, 0.8 + 0.2 * math.sin(2 * math.pi * i / 20)))

    os.makedirs(os.path.dirname(out), exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_STORED) as z:  # Android requires no compression
        z.writestr("desc.txt", "%d %d %d\np 1 0 part0\np 0 0 part1\n" % (W, H, FPS))
        for name, frames in (("part0", part0), ("part1", part1)):
            for i, f in enumerate(frames):
                z.writestr("%s/%03d.jpg" % (name, i), jpg(f))
    print("OK: %s (%.1f MB)" % (out, os.path.getsize(out) / 1e6))


if __name__ == "__main__":
    main()
