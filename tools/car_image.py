#!/usr/bin/env python3
"""Prepara la imagen del carro para el launcher (Clean Launcher).

Entrada: un PNG del carro (vista lateral o 3/4) con FONDO TRANSPARENTE.
Salidas (por defecto en res/drawable-nodpi del repo):
  sail.png        recortado al contorno del carro y reducido a un maximo de 900x420
                  (si ya es mas chico, no se agranda).
  sail_lights.png la misma imagen con brillo de faros (blanco calido, adelante)
                  y de luces traseras (ambar/rojo), y un cono de luz suave al frente.

Las posiciones de las luces son coordenadas relativas (0 a 1) respecto de la
imagen ya recortada: x = 0 izquierda, x = 1 derecha, y = 0 arriba, y = 1 abajo.
Por defecto el frente esta a la izquierda (0.06,0.55) y la trasera a la derecha
(0.95,0.5). Si el carro mira hacia la derecha, cambia los valores.
El cono de luz puede salir del borde de la imagen y se recorta.

Uso:
  py tools\\car_image.py tools\\sail_source.png
  py tools\\car_image.py tools\\sail_source.png --front 0.06,0.55 --rear 0.95,0.5
  py tools\\car_image.py entrada.png --out-dir res\\drawable-nodpi

Requisitos: Python 3 y Pillow.  Instalar con:  py -m pip install pillow
"""
import argparse
import math
import os
import sys

try:
    from PIL import Image, ImageChops, ImageDraw, ImageFilter
except ImportError:
    sys.exit("Falta Pillow. Instala con: py -m pip install pillow")

RESAMPLE = getattr(Image, "Resampling", Image).LANCZOS
MAX_W, MAX_H = 900, 420
FRONT_COLOR = (255, 244, 214)  # blanco calido (faros)
REAR_COLOR = (255, 96, 32)     # ambar/rojo (luces traseras)


def parse_point(text):
    try:
        x, y = (float(v) for v in text.split(","))
    except ValueError:
        raise argparse.ArgumentTypeError("usa el formato x,y con numeros, por ejemplo 0.06,0.55")
    if not (0 <= x <= 1 and 0 <= y <= 1):
        raise argparse.ArgumentTypeError("x e y deben estar entre 0 y 1")
    return x, y


def trim_and_fit(img):
    img = img.convert("RGBA")
    alpha = img.getchannel("A")
    if alpha.getextrema()[0] == 255:
        sys.exit("La imagen no tiene fondo transparente. Quita el fondo (PNG con alfa) y vuelve a intentar.")
    box = alpha.getbbox()
    if box is None:
        sys.exit("La imagen esta totalmente transparente.")
    img = img.crop(box)
    w, h = img.size
    scale = min(MAX_W / w, MAX_H / h, 1.0)
    if scale < 1.0:
        img = img.resize((max(1, round(w * scale)), max(1, round(h * scale))), RESAMPLE)
    return img


def radial_mask(size, cx, cy, rx, ry, power=2.0):
    """Gradiente radial: brillante en el centro, cero en el borde del elipse."""
    mask = Image.new("L", size, 0)
    draw = ImageDraw.Draw(mask)
    steps = max(12, int(max(rx, ry)))
    for i in range(steps, 0, -1):
        t = i / steps
        value = int(255 * (1 - t) ** power)
        draw.ellipse(
            (round(cx - rx * t), round(cy - ry * t), round(cx + rx * t), round(cy + ry * t)),
            fill=value,
        )
    return mask.filter(ImageFilter.GaussianBlur(max(2, min(rx, ry) * 0.25)))


def cone_mask(size, fx, fy, dx, dy, length, far_half, near_half=None):
    """Cono de luz que sale de (fx,fy) en la direccion (dx,dy), con desenfoque."""
    near_half = near_half if near_half is not None else far_half * 0.2
    px, py = -dy, dx
    tx, ty = fx + dx * length, fy + dy * length
    poly = [
        (fx + px * near_half, fy + py * near_half),
        (fx - px * near_half, fy - py * near_half),
        (tx - px * far_half, ty - py * far_half),
        (tx + px * far_half, ty + py * far_half),
    ]
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).polygon([(round(x), round(y)) for x, y in poly], fill=255)
    return mask.filter(ImageFilter.GaussianBlur(max(2, far_half * 0.5)))


def add_glow(base_rgb, mask, color, strength):
    """Suma (aditiva) un color usando la mascara como intensidad."""
    mask = mask.point(lambda v: int(v * strength))
    tint = Image.new("RGB", base_rgb.size, color)
    layer = Image.composite(tint, Image.new("RGB", base_rgb.size, (0, 0, 0)), mask)
    return ImageChops.add(base_rgb, layer)


def main():
    parser = argparse.ArgumentParser(description="Prepara la imagen del carro para el launcher.")
    parser.add_argument("entrada", help="PNG del carro con fondo transparente")
    parser.add_argument("--front", type=parse_point, default=(0.06, 0.55),
                        help="posicion del faro delantero, x,y entre 0 y 1 (defecto 0.06,0.55)")
    parser.add_argument("--rear", type=parse_point, default=(0.95, 0.5),
                        help="posicion de la luz trasera, x,y entre 0 y 1 (defecto 0.95,0.5)")
    parser.add_argument("--out-dir", default=None,
                        help="carpeta de salida (defecto: res/drawable-nodpi del repo)")
    args = parser.parse_args()

    if not os.path.isfile(args.entrada):
        sys.exit("No existe el archivo: " + args.entrada)

    here = os.path.dirname(os.path.abspath(__file__))
    repo = os.path.abspath(os.path.join(here, ".."))
    out_dir = args.out_dir or os.path.join(repo, "res", "drawable-nodpi")
    os.makedirs(out_dir, exist_ok=True)

    with Image.open(args.entrada) as src:
        base = trim_and_fit(src)
    w, h = base.size
    size = (w, h)
    alpha = base.getchannel("A")
    lights = base.convert("RGB")

    fx, fy = args.front[0] * w, args.front[1] * h
    rx, ry = args.rear[0] * w, args.rear[1] * h

    # Direccion hacia adelante = de la trasera al frente.
    dist = math.hypot(fx - rx, fy - ry) or 1.0
    dx, dy = (fx - rx) / dist, (fy - ry) / dist
    lights = add_glow(lights, cone_mask(size, fx, fy, dx, dy, length=w * 0.5, far_half=h * 0.35), FRONT_COLOR, 0.18)
    lights = add_glow(lights, radial_mask(size, fx, fy, w * 0.07, h * 0.05), FRONT_COLOR, 0.9)
    lights = add_glow(lights, radial_mask(size, rx, ry, w * 0.05, h * 0.04), REAR_COLOR, 0.9)

    plain_path = os.path.join(out_dir, "sail.png")
    lights_path = os.path.join(out_dir, "sail_lights.png")
    base.save(plain_path, optimize=True)

    out = lights.convert("RGBA")
    out.putalpha(alpha)
    out.save(lights_path, optimize=True)

    print("Listo: %dx%d" % (w, h))
    print("  " + plain_path)
    print("  " + lights_path)


if __name__ == "__main__":
    main()
