"""Texture-only art pass. Uses a local Minecraft JAR as the netherite style reference.

Requires Pillow and numpy. Run from the repository root; no gameplay/data files
are generated. Previews are software UV studies, not in-game screenshots.
"""
from pathlib import Path
import argparse
import io
import json
import math
import os
import shutil
import zipfile

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SHARED = Path('src/main/resources/assets/wardenpact/textures')
FABRIC = Path('fabric/src/main/resources/assets/wardenpact/textures')
BACKUP = ROOT / '.tools/texture-refresh-backup'
OUTPUT = ROOT / '.tools/texture-refresh'
INK = '#0c121d'
DARK = '#202938'
STEEL = '#394b5c'
EDGE = '#718a97'
TEAL_DARK = '#24545c'
TEAL = '#368e94'
CYAN = '#65d9ce'
LIGHT = '#b4e9df'
BONE = '#c6c4a1'
LEATHER = '#3c3034'


def save_png(image, relative):
    target = ROOT / relative
    backup = BACKUP / relative
    if target.exists() and not backup.exists():
        backup.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(target, backup)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(target.name + '.tmp')
    image.save(temporary, format='PNG', optimize=True)
    os.replace(temporary, target)


def netherite_base(archive, name):
    with archive.open('assets/minecraft/textures/' + name + '.png') as source:
        image = Image.open(source).convert('RGBA')
    # Retain the vanilla silhouette, transparent openings, bevels and all
    # luminance steps. Shift the metal to a cool, slightly brighter alloy.
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = image.getpixel((x, y))
            if a:
                value = 0.25 * r + 0.55 * g + 0.20 * b
                image.putpixel((x, y), (int(value * .90 + 1),
                                       int(value * 1.02 + 6),
                                       int(value * 1.12 + 12), a))
    return image


def inlay(image, lines=(), pixels=()):
    alpha = image.getchannel('A')
    d = ImageDraw.Draw(image)
    for points, color in lines:
        d.line(points, fill=color)
    for xy, color in pixels:
        d.point(xy, fill=color)
    # An etched detail must never fill a visor opening or spill off a blade.
    image.putalpha(alpha)
    return image


def items(archive):
    result = {}
    for kind in ('helmet', 'chestplate', 'leggings', 'boots',
                 'pickaxe', 'axe', 'shovel', 'hoe', 'sword'):
        im = netherite_base(archive, 'item/netherite_' + kind)
        lines, pixels = [], []
        if kind == 'helmet':
            lines = [([(5, 6), (6, 6)], TEAL), ([(9, 6), (10, 6)], TEAL)]
            pixels = [((7, 5), CYAN), ((8, 5), TEAL_DARK),
                      ((4, 8), TEAL_DARK), ((11, 8), TEAL_DARK)]
        elif kind == 'chestplate':
            lines = [([(2, 5), (3, 6)], TEAL_DARK),
                     ([(12, 6), (13, 5)], TEAL_DARK),
                     ([(6, 8), (7, 9), (9, 8)], TEAL),
                     ([(6, 11), (7, 12), (9, 11)], TEAL_DARK)]
            pixels = [((7, 8), LIGHT), ((8, 8), CYAN)]
        elif kind == 'leggings':
            lines = [([(4, 4), (6, 4)], TEAL_DARK),
                     ([(9, 4), (11, 4)], TEAL_DARK)]
            pixels = [((7, 3), CYAN), ((8, 3), TEAL),
                      ((5, 9), TEAL), ((10, 9), TEAL)]
        elif kind == 'boots':
            pixels = [((4, 7), TEAL), ((11, 7), TEAL),
                      ((3, 10), TEAL_DARK), ((12, 10), TEAL_DARK)]
        elif kind == 'sword':
            # The cutting edge stays steel; a recessed echo channel follows
            # the blade between its two bevels.
            lines = [([(7, 8), (13, 2)], TEAL)]
            pixels = [((12, 3), CYAN), ((10, 5), CYAN),
                      ((8, 7), CYAN), ((5, 9), TEAL_DARK), ((1, 14), CYAN)]
        else:
            # Distinct functional head shapes remain legible at native 16px.
            details = {
                'pickaxe': ([([(7, 3), (10, 3)], TEAL),
                             ([(12, 5), (13, 7)], TEAL_DARK)], [((10, 3), CYAN)]),
                'axe': ([([(9, 3), (8, 4), (8, 5)], TEAL_DARK)],
                        [((10, 4), CYAN), ((10, 5), TEAL)]),
                'shovel': ([([(11, 4), (11, 5)], TEAL)], [((12, 4), CYAN)]),
                'hoe': ([([(8, 2), (10, 3)], TEAL_DARK)], [((9, 2), CYAN)]),
            }
            lines, pixels = details[kind]
            pixels += [((4, 12), TEAL_DARK), ((3, 13), TEAL)]
        inlay(im, lines, pixels)
        name = 'summoning_sword' if kind == 'sword' else 'echo_' + kind
        save_png(im, SHARED / 'item' / (name + '.png'))
        result[name] = im
    return result


def armor(archive):
    result = {}
    for kind in ('humanoid', 'humanoid_baby', 'humanoid_leggings'):
        im = netherite_base(archive, 'entity/equipment/' + kind + '/netherite')
        lines, pixels = [], []
        if kind == 'humanoid':
            # Full vanilla bevel/seam map, with a small echo crystal at the
            # sternum and dim inlays on the brow, pauldrons and greaves.
            lines = [([(21, 25), (22, 26)], TEAL_DARK),
                     ([(25, 26), (26, 25)], TEAL_DARK),
                     ([(21, 28), (22, 29)], TEAL_DARK),
                     ([(25, 29), (26, 28)], TEAL_DARK),
                     ([(34, 23), (34, 27)], TEAL_DARK),
                     ([(44, 22), (45, 23), (46, 22)], TEAL_DARK)]
            pixels = [((11, 10), TEAL), ((12, 10), CYAN),
                      ((9, 12), TEAL_DARK), ((14, 12), TEAL_DARK),
                      ((23, 24), CYAN), ((24, 24), TEAL),
                      ((23, 25), TEAL), ((24, 25), TEAL_DARK),
                      ((45, 22), TEAL), ((5, 29), TEAL), ((6, 29), TEAL_DARK)]
        elif kind == 'humanoid_leggings':
            pixels = [((23, 30), TEAL), ((24, 30), CYAN),
                      ((5, 25), TEAL), ((6, 25), TEAL_DARK)]
        else:
            # Baby armor uses a DIFFERENT 64x64 UV sheet in 26.3. Preserve
            # its own vanilla map rather than reusing a 64x32 adult texture.
            pixels = [((13, 10), TEAL), ((14, 10), CYAN),
                      ((9, 24), TEAL), ((10, 24), TEAL_DARK),
                      ((34, 10), TEAL_DARK), ((43, 40), TEAL)]
        inlay(im, lines, pixels)
        save_png(im, SHARED / 'entity/equipment' / kind / 'echo.png')
        result[kind] = im
    return result


def uv_faces(u, v, w, h, depth):
    return [(u + depth, v, w, depth), (u + depth + w, v, w, depth),
            (u, v + depth, depth, h), (u + depth, v + depth, w, h),
            (u + depth + w, v + depth, depth, h),
            (u + 2 * depth + w, v + depth, w, h)]


def mount_skin():
    skin = Image.new('RGBA', (128, 128), INK)
    d = ImageDraw.Draw(skin)

    def cube(u, v, w, h, depth, kind):
        for index, (x, y, fw, fh) in enumerate(uv_faces(u, v, w, h, depth)):
            if kind == 'horn':
                colors = ['#d8d3af', '#706d64', '#a49f84', '#c9c5a4', '#8d947f', '#b1b394']
                d.rectangle((x, y, x + fw - 1, y + fh - 1), fill=colors[index])
                for band in (4, 9):
                    if band < fh:
                        d.line((x, y + band, x + fw - 1, y + band), fill='#6c7d74')
                d.line((x, y, x, y + fh - 1), fill='#e5dfbc')
                continue
            bases = ['#344750', '#121e2b', '#253742', '#314650', '#1f303d', '#2b3e49']
            base = tuple(int(bases[index][i:i+2], 16) for i in (1, 3, 5))
            for py in range(fh):
                for px in range(fw):
                    # Quiet 3-4px scale clusters, not independent pixel noise.
                    cluster = ((px // 4) * 7 + (py // 3) * 11 + u + v) % 5 - 2
                    bevel = 7 if px == 0 or py == 0 else -9 if px == fw - 1 or py == fh - 1 else 0
                    shade = cluster * 2 + bevel - int(5 * py / max(1, fh - 1))
                    d.point((x + px, y + py), tuple(max(0, c + shade) for c in base) + (255,))
            if kind in ('body', 'neck') and fw > 9 and fh > 8:
                for px, py in ((3, 4), (fw - 5, fh - 4)):
                    d.line([(x + px, y + py), (x + px + 2, y + py),
                            (x + px + 2, y + py + 2)], fill=TEAL_DARK)
            if kind == 'leg' and fh > 20:
                d.rectangle((x, y + fh - 7, x + fw - 1, y + fh - 1), fill='#121c28')
                d.line((x, y + fh - 7, x + fw - 1, y + fh - 7), fill=EDGE)
                d.line((x + 1, y + 11, x + fw - 2, y + 11), fill='#445866')
                if fw >= 6:
                    d.line((x + 2, y + 12, x + 2, y + 15), fill=TEAL)
                    d.rectangle((x + 2, y + fh - 3, x + 3, y + fh - 2), fill='#566878')
                    d.rectangle((x + 5, y + fh - 3, x + 6, y + fh - 2), fill='#566878')

    cube(0, 0, 16, 20, 16, 'head')
    cube(0, 36, 16, 3, 16, 'jaw')
    cube(0, 55, 14, 16, 20, 'body')
    cube(0, 91, 12, 13, 18, 'body')
    cube(68, 73, 10, 10, 18, 'neck')
    cube(64, 0, 8, 37, 8, 'leg')
    cube(96, 0, 8, 37, 8, 'leg')
    cube(74, 55, 2, 14, 4, 'horn')

    # Angular forehead plate with bevelled brow and paired inset eyes.
    d.polygon([(17, 17), (30, 17), (29, 22), (26, 24),
               (21, 24), (18, 22)], fill='#455b69')
    d.line([(18, 18), (22, 18), (23, 19), (25, 18), (29, 18)], fill=EDGE)
    d.polygon([(23, 18), (25, 20), (23, 23), (22, 20)], fill=TEAL_DARK)
    d.point((23, 20), fill=CYAN)
    for x in (18, 26):
        d.rectangle((x - 1, 23, x + 4, 26), fill=INK)
        d.line((x, 23, x + 3, 23), fill='#697e88')
        d.line((x, 24, x + 3, 24), fill=CYAN)
        d.point((x + 1, 24), fill=LIGHT)
        d.line((x + 1, 26, x + 1, 29), fill=TEAL_DARK)
    # Cheek armor, small carved sensors on the sides, and a dark lower jaw.
    for x in (1, 33):
        d.polygon([(x + 1, 20), (x + 12, 19), (x + 13, 24),
                   (x + 10, 29), (x + 3, 29)], fill='#3b515f')
        d.line((x + 2, 20, x + 10, 20), fill=EDGE)
        for j in range(3):
            d.line((x + 4 + j * 2, 23, x + 4 + j * 2, 25 + j % 2), fill=TEAL)
    d.rectangle((17, 32, 30, 34), fill='#141f2a')
    for x in (19, 23, 27):
        d.line((x, 33, x + 1, 33), fill='#728689')
    d.rectangle((17, 52, 30, 54), fill='#172430')
    d.line((18, 53, 29, 53), fill='#405461')
    for x in (19, 23, 27):
        d.point((x, 52), fill=BONE)
    # Body rotates 90deg around X in RavagerModel: its BACK UV face is
    # the upward-facing back. Place the saddle there, not on its belly.
    for x, width in ((0, 20), (20, 14), (34, 20), (54, 14)):
        d.rectangle((x, 81, x + width - 1, 83), fill=LEATHER)
        d.line((x, 81, x + width - 1, 81), fill='#7b6960')
        d.line((x, 83, x + width - 1, 83), fill='#211f29')
    d.rectangle((55, 76, 66, 89), fill='#1a202b')
    d.rectangle((56, 77, 65, 88), fill='#64504b')
    d.rectangle((57, 79, 64, 87), fill='#47383e')
    d.line((56, 77, 65, 77), fill='#a68c70')
    d.line((57, 79, 64, 79), fill='#8b7161')
    d.line((56, 88, 65, 88), fill='#8c7561')
    for x in (56, 65):
        for y in (79, 82, 85):
            d.point((x, y), fill='#b5a182')
    # A readable echo emblem on each shoulder; the flank is rotated in-game.
    for x in (6, 40):
        d.polygon([(x, 85), (x + 3, 82), (x + 6, 85), (x + 3, 88)], fill=INK)
        d.line([(x + 1, 85), (x + 3, 83), (x + 5, 85),
                (x + 3, 87), (x + 1, 85)], fill=TEAL)
        d.point((x + 3, 85), fill=CYAN)
    # Snout occupies a separate corner of the head UV map.
    cube(0, 0, 4, 8, 4, 'snout')
    d.rectangle((4, 6, 7, 8), fill='#1b2835')
    d.point((4, 6), fill='#677982')
    d.point((7, 6), fill='#677982')
    save_png(skin, FABRIC / 'entity/sculk_mount.png')
    return skin


def reins():
    im = Image.new('RGBA', (16, 16))
    d = ImageDraw.Draw(im)
    path = [(6, 4), (3, 5), (2, 8), (3, 11), (6, 13),
            (10, 13), (13, 10), (13, 6), (10, 4)]
    d.line(path, fill=INK, width=3)
    d.line(path, fill='#76645c')
    d.line([(4, 6), (3, 8), (4, 10), (6, 11)], fill='#a48b70')
    d.line([(10, 6), (11, 8), (10, 10), (7, 11)], fill='#3c484e')
    d.polygon([(7, 0), (11, 3), (9, 7), (5, 6), (4, 3)], fill=INK)
    d.polygon([(7, 1), (10, 3), (8, 6), (5, 4)], fill=STEEL)
    d.polygon([(7, 2), (9, 3), (8, 5), (6, 4)], fill=TEAL)
    d.line([(7, 2), (6, 3)], fill=LIGHT)
    d.point((8, 5), fill=CYAN)
    d.point((7, 7), fill='#a68c70')
    save_png(im, FABRIC / 'item/sculk_reins.png')
    return im


def box_faces(texture, uv, position, size, dimensions=None, rotation=None, pivot=(0, 0, 0)):
    x, y, z = position
    w, h, depth = size
    p = np.array([(x, y, z), (x + w, y, z), (x + w, y + h, z), (x, y + h, z),
                  (x, y, z + depth), (x + w, y, z + depth),
                  (x + w, y + h, z + depth), (x, y + h, z + depth)], dtype=float)
    if rotation is not None:
        p = p @ rotation.T
    p += np.array(pivot)
    # Winding agrees with the regular Java box UV layout.
    orders = [(4, 5, 1, 0), (3, 2, 6, 7), (4, 0, 3, 7),
              (0, 1, 2, 3), (1, 5, 6, 2), (5, 4, 7, 6)]
    uw, uh, ud = dimensions or size
    rects = uv_faces(*uv, uw, uh, ud)
    return [(p[list(indices)], rect, texture) for indices, rect in zip(orders, rects)]


def rotate_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def render(faces, size=(300, 340), yaw=-.55, pitch=.15):
    # Orthographic software renderer, with nearest-neighbor UV lookup and a
    # depth buffer. It previews the same cube UVs without launching a client.
    width, height = size
    c, s = math.cos(yaw), math.sin(yaw)
    ry = np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    rx = rotate_x(pitch)
    camera = rx @ ry
    projected = [verts @ camera.T for verts, _, _ in faces]
    all_vertices = np.concatenate(projected)
    lo, hi = all_vertices[:, :2].min(0), all_vertices[:, :2].max(0)
    scale = min((width - 28) / (hi[0] - lo[0]), (height - 20) / (hi[1] - lo[1]))
    offset = np.array([width / 2, height / 2]) - (lo + hi) * .5 * scale
    canvas = np.zeros((height, width, 4), dtype=np.uint8)
    depth_buffer = np.full((height, width), np.inf)
    light = np.array([-.35, -.7, -.62])
    light /= np.linalg.norm(light)
    for (verts, rect, texture), points in zip(faces, projected):
        normal = np.cross(verts[1] - verts[0], verts[2] - verts[0])
        normal /= max(1e-9, np.linalg.norm(normal))
        shade = .77 + .23 * abs(float(normal @ light))
        x, y, fw, fh = rect
        texcoords = np.array([(x, y), (x + fw, y), (x + fw, y + fh), (x, y + fh)])
        screen = points[:, :2] * scale + offset
        pixels = np.array(texture)
        for tri in ((0, 1, 2), (0, 2, 3)):
            v = screen[list(tri)]
            tx = texcoords[list(tri)]
            zz = points[list(tri), 2]
            left = max(0, int(np.floor(v[:, 0].min())))
            right = min(width - 1, int(np.ceil(v[:, 0].max())))
            top = max(0, int(np.floor(v[:, 1].min())))
            bottom = min(height - 1, int(np.ceil(v[:, 1].max())))
            if left > right or top > bottom:
                continue
            xx, yy = np.meshgrid(np.arange(left, right + 1) + .5, np.arange(top, bottom + 1) + .5)
            denom = ((v[1, 1] - v[2, 1]) * (v[0, 0] - v[2, 0]) +
                     (v[2, 0] - v[1, 0]) * (v[0, 1] - v[2, 1]))
            if abs(denom) < 1e-8:
                continue
            a = ((v[1, 1] - v[2, 1]) * (xx - v[2, 0]) +
                 (v[2, 0] - v[1, 0]) * (yy - v[2, 1])) / denom
            b = ((v[2, 1] - v[0, 1]) * (xx - v[2, 0]) +
                 (v[0, 0] - v[2, 0]) * (yy - v[2, 1])) / denom
            cc = 1 - a - b
            u = np.clip((a * tx[0, 0] + b * tx[1, 0] + cc * tx[2, 0]).astype(int), x, x + fw - 1)
            vv = np.clip((a * tx[0, 1] + b * tx[1, 1] + cc * tx[2, 1]).astype(int), y, y + fh - 1)
            colors = pixels[vv, u].copy()
            colors[:, :, :3] = (colors[:, :, :3] * shade).astype(np.uint8)
            zbuf = a * zz[0] + b * zz[1] + cc * zz[2]
            current = depth_buffer[top:bottom + 1, left:right + 1]
            mask = (a >= -1e-6) & (b >= -1e-6) & (cc >= -1e-6) & (zbuf < current - 1e-6) & (colors[:, :, 3] > 0)
            canvas[top:bottom + 1, left:right + 1][mask] = colors[mask]
            current[mask] = zbuf[mask]
    return Image.fromarray(canvas)


def armor_model(textures):
    faces = []
    solid = Image.new('RGBA', (64, 32), '#302e32')
    def part(pos, dims, uv, texture, inflate=0):
        p = tuple(v - inflate for v in pos)
        size = tuple(v + 2 * inflate for v in dims)
        faces.extend(box_faces(texture, uv, p, size, dims))
    parts = [((-4, -8, -4), (8, 8, 8), (0, 0)),
             ((-4, 0, -2), (8, 12, 4), (16, 16)),
             ((-8, 0, -2), (4, 12, 4), (40, 16)),
             ((4, 0, -2), (4, 12, 4), (40, 16)),
             ((-4, 12, -2), (4, 12, 4), (0, 16)),
             ((0, 12, -2), (4, 12, 4), (0, 16))]
    for pos, dims, uv in parts:
        part(pos, dims, uv, solid)
    # Inner leggings first, then the outer boots/chest/helmet layer.
    for index in (1, 4, 5):
        pos, dims, uv = parts[index]
        part(pos, dims, uv, textures['humanoid_leggings'], .22)
    for pos, dims, uv in parts:
        part(pos, dims, uv, textures['humanoid'], .55)
    return faces


def mount_model(skin):
    faces = []
    def part(uv, pos, dims, rotation=None, pivot=(0, 0, 0)):
        faces.extend(box_faces(skin, uv, pos, dims, rotation=rotation, pivot=pivot))
    for x in (-8, 8):
        part((64, 0), (x - 4, -13, -9), (8, 37, 8))
        part((96, 0), (x - 4, -13, 14), (8, 37, 8))
    part((0, 55), (-7, -10, -7), (14, 16, 20), rotate_x(math.pi / 2), (0, 1, 2))
    part((0, 91), (-6, 6, -7), (12, 13, 18), rotate_x(math.pi / 2), (0, 1, 2))
    part((68, 73), (-5, -8, -12.5), (10, 10, 18))
    part((0, 0), (-8, -11, -42.5), (16, 20, 16))
    part((0, 0), (-2, 3, -46.5), (4, 8, 4))
    part((0, 36), (-8, 7, -42.5), (16, 3, 16))
    for x in (-10, 8):
        part((74, 55), (0, -14, -2), (2, 14, 4), rotate_x(1.0995574), (x, -5, -36.5))
    return faces


def make_previews(item_textures, armor_textures, skin):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    font_path = 'C:/Windows/Fonts/msyh.ttc'
    regular = ImageFont.truetype(font_path, 18)
    small = ImageFont.truetype(font_path, 14)
    title = ImageFont.truetype(font_path, 30)
    sheet = Image.new('RGB', (1280, 980), '#131922')
    d = ImageDraw.Draw(sheet)
    d.text((36, 24), '回响装备 · 下界合金重甲风格', font=title, fill='#e0e5e6')
    d.text((38, 72), '厚重倒角 / 冷色金属 / 青色嵌纹     |     WARDEN PACT 1.3.2', font=small, fill='#97afb7')
    names = {'echo_helmet': '回响头盔', 'echo_chestplate': '回响胸甲',
             'echo_leggings': '回响护腿', 'echo_boots': '回响靴子',
             'summoning_sword': '召唤剑', 'echo_pickaxe': '回响镐', 'echo_axe': '回响斧',
             'echo_shovel': '回响锹', 'echo_hoe': '回响锄', 'sculk_reins': '幽匿缰绳'}
    for index, (name, im) in enumerate(item_textures.items()):
        row, col = divmod(index, 5)
        x, y = 36 + col * 244, 116 + row * 146
        d.rounded_rectangle((x, y, x + 230, y + 132), radius=6, fill='#202a35')
        sprite = im.resize((96, 96), Image.Resampling.NEAREST)
        sheet.paste(sprite, (x + 8, y + 14), sprite)
        d.text((x + 108, y + 49), names[name], font=regular, fill='#c9d7dd')
    d.line((36, 426, 1244, 426), fill='#36434e')
    poses = [('正面', -.15), ('侧前', -.70), ('背面', math.pi - .5)]
    for index, (label, yaw) in enumerate(poses):
        preview = render(armor_model(armor_textures), (240, 380), yaw=yaw, pitch=.10)
        sheet.paste(preview, (25 + index * 235, 480), preview)
        d.text((115 + index * 235, 880), label, font=regular, fill='#a6c2c9')
    mount = render(mount_model(skin), (520, 400), yaw=-.78, pitch=.14)
    sheet.paste(mount, (735, 480), mount)
    d.text((890, 880), '幽匿战兽 · 甲片与皮革鞍具', font=regular, fill='#a6c2c9')
    d.text((36, 944), '穿戴与坐骑为离线 UV 模型预览；最终效果以游戏内光照、附魔光效为准。', font=small, fill='#71858f')
    sheet.save(OUTPUT / 'preview.png')
    (ROOT / 'docs').mkdir(exist_ok=True)
    sheet.save(ROOT / 'docs/texture-preview.png')

    compare = Image.new('RGB', (1240, 550), '#171e28')
    cd = ImageDraw.Draw(compare)
    cd.text((32, 18), '物品图标对比：上排旧版 / 下排新版', font=title, fill='#e0e5e6')
    for i, (name, im) in enumerate(item_textures.items()):
        relative = (FABRIC if name == 'sculk_reins' else SHARED) / 'item' / (name + '.png')
        old = Image.open(BACKUP / relative).convert('RGBA')
        for row, texture in enumerate((old, im)):
            resized = texture.resize((96, 96), Image.Resampling.NEAREST)
            compare.paste(resized, (25 + i * 122, 95 + row * 235), resized)
        cd.text((25 + i * 122, 460), names[name], font=small, fill='#bdcdd3')
    compare.save(OUTPUT / 'before-after.png')
    skin.resize((768, 768), Image.Resampling.NEAREST).save(OUTPUT / 'mount-uv.png')


def validate(archive):
    files = list((ROOT / SHARED).rglob('*.png')) + list((ROOT / FABRIC).rglob('*.png'))
    checks = []
    for path in files:
        with Image.open(path) as im:
            assert im.mode == 'RGBA'
            if '/item/' in path.as_posix():
                assert im.size == (16, 16)
                assert im.getpixel((15, 15))[3] == 0
                assert 0 < np.count_nonzero(np.array(im.getchannel('A'))) < 220
            elif path.name == 'sculk_mount.png':
                assert im.size == (128, 128)
                assert im.getchannel('A').getextrema() == (255, 255)
            else:
                kind = path.parent.name
                ref = Image.open(io.BytesIO(archive.read('assets/minecraft/textures/entity/equipment/' + kind + '/netherite.png'))).convert('RGBA')
                assert im.size == ref.size, kind
                assert im.getchannel('A').tobytes() == ref.getchannel('A').tobytes(), kind
            backup = BACKUP / path.relative_to(ROOT)
            assert backup.exists() and path.read_bytes() != backup.read_bytes()
            checks.append({'file': path.relative_to(ROOT).as_posix(), 'size': list(im.size)})
    assert len(checks) == 14
    (OUTPUT / 'validation.json').write_text(json.dumps(checks, indent=2), encoding='utf-8')
    return checks


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--minecraft-jar', type=Path, default=ROOT / '.tools/minecraft-26.3.jar')
    args = parser.parse_args()
    with zipfile.ZipFile(args.minecraft_jar) as archive:
        icons = items(archive)
        textures = armor(archive)
        skin = mount_skin()
        icons['sculk_reins'] = reins()
        make_previews(icons, textures, skin)
        checks = validate(archive)
    print(json.dumps({'textures': len(checks), 'preview': str(OUTPUT / 'preview.png'),
                      'backup': str(BACKUP), 'native_alpha_and_uv_preserved': True}, ensure_ascii=False))


if __name__ == '__main__':
    main()
