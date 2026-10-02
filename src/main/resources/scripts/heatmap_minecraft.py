#!/usr/bin/env python3
"""
heatmap_minecraft.py — top-down path map for a Minecraft baseline run.

Renders one PNG where **1 pixel = 1 block**:
  * white background (+ a white padding border on every side);
  * every cell that holds a block in the level CSV is drawn gray;
  * the player trajectory is green;
  * each mob trajectory gets its own color (red, orange, yellow, ...).

Inputs
------
--level  the Minecraft arena CSV (stacked WxH layers; each layer starts with a
         line beginning with '|', the first line is the inventory). Used only to
         paint the gray block layer. A cell is a block if it is non-empty and does
         NOT start with '@' (entities are not blocks).
--trace  the per-tick location trace (ticks.csv). Every column pair
         '<name>_x'/'<name>_z' is a track: 'own' is the player, all the others
         are mobs. Missing/blank coordinates are treated as gaps (not connected).

The map size is taken from --width/--height (falling back to the level grid size).
Coordinates are world coordinates; grid cell = floor(coord - origin).
"""

from __future__ import annotations
import argparse
import csv
import math

import numpy as np
import matplotlib
matplotlib.use("Agg")          # headless PNG backend; must precede the pyplot import
import matplotlib.pyplot as plt


# --- palette (RGB, 0..1) -------------------------------------------------------
GRAY   = (0.60, 0.60, 0.60)    # blocks
PLAYER = (0.13, 0.70, 0.13)    # green
MOB_COLORS = [
    (0.85, 0.11, 0.11),   # red
    (1.00, 0.55, 0.00),   # orange
    (0.95, 0.82, 0.10),   # yellow
    (0.60, 0.20, 0.80),   # purple
    (0.00, 0.65, 0.70),   # teal
    (0.90, 0.40, 0.70),   # pink
]


def load_block_mask(path, width=None, height=None):
    """Parse the Minecraft level CSV and return (H, W, mask) with mask[z][x] True
    iff some layer places a (non-entity) block at column x, row z.

    Layers are stacked vertically in the file: a line starting with '|' opens a
    new layer and is its first row; the following rows belong to the same layer
    until the next '|'. Lines before the first '|' (the inventory) are ignored.
    """
    with open(path, encoding="latin-1") as f:
        lines = f.read().splitlines()

    layers = []
    current = None
    for line in lines:
        if line.startswith("|"):
            current = [[c.strip() for c in line[1:].split(",")]]
            layers.append(current)
        elif current is not None:
            current.append([c.strip() for c in line.split(",")])
        # else: pre-layer lines (inventory) are skipped
    if not layers:
        raise ValueError(f"no layers (lines starting with '|') found in {path}")

    H = height or len(layers[0])
    W = width or max(len(row) for row in layers[0])
    mask = np.zeros((H, W), dtype=bool)
    for layer in layers:
        for z, row in enumerate(layer):
            if z >= H:
                continue
            for x, cell in enumerate(row):
                if x < W and cell and not cell.startswith("@"):
                    mask[z, x] = True
    return H, W, mask


def detect_tracks(fieldnames, player):
    """From CSV headers, return (player_cols, mobs): player_cols is (xcol, zcol) or
    None; mobs is a sorted list of (name, xcol, zcol) for every other '_x'/'_z'."""
    pairs = {}
    for col in fieldnames or []:
        if col.endswith("_x"):
            name = col[:-2]
            zcol = name + "_z"
            if zcol in fieldnames:
                pairs[name] = (col, zcol)
    player_cols = pairs.pop(player, None)
    mobs = [(n, xz[0], xz[1]) for n, xz in sorted(pairs.items())]
    return player_cols, mobs


def read_cells(rows, xcol, zcol, origin_x, origin_z, W, H):
    """Turn trace rows into a list of integer (x, z) block cells. A row with a
    missing/blank coordinate, or a cell outside the map, becomes a None gap so the
    path is not drawn across it."""
    cells = []
    for r in rows:
        sx, sz = r.get(xcol, ""), r.get(zcol, "")
        if sx in ("", None) or sz in ("", None):
            cells.append(None)
            continue
        x = int(math.floor(float(sx) - origin_x))
        z = int(math.floor(float(sz) - origin_z))
        cells.append((x, z) if 0 <= x < W and 0 <= z < H else None)
    return cells


def bresenham(x0, z0, x1, z1):
    """Integer grid points on the segment (x0,z0)->(x1,z1), endpoints included."""
    dx, dz = abs(x1 - x0), abs(z1 - z0)
    sx = 1 if x0 < x1 else -1
    sz = 1 if z0 < z1 else -1
    err = dx - dz
    pts = []
    while True:
        pts.append((x0, z0))
        if x0 == x1 and z0 == z1:
            break
        e2 = 2 * err
        if e2 > -dz:
            err -= dz
            x0 += sx
        if e2 < dx:
            err += dx
            z0 += sz
    return pts


def paint_path(img, cells, color, pad):
    """Draw a track (list of (x,z) or None gaps) onto the padded image, connecting
    consecutive samples with a straight line so the trajectory stays continuous."""
    prev = None
    for c in cells:
        if c is None:
            prev = None
            continue
        if prev is not None:
            for (x, z) in bresenham(prev[0], prev[1], c[0], c[1]):
                img[pad + z, pad + x] = color
        else:
            img[pad + c[1], pad + c[0]] = color
        prev = c


def main():
    ap = argparse.ArgumentParser(description="Top-down 1px/block path map for a Minecraft run.")
    ap.add_argument("--trace", required=True, help="per-tick location trace CSV (ticks.csv)")
    ap.add_argument("--level", help="Minecraft arena CSV (for the gray block layer)")
    ap.add_argument("--width", type=int, help="map width in blocks (default: from level)")
    ap.add_argument("--height", type=int, help="map height in blocks (default: from level)")
    ap.add_argument("--player", default="own", help="column prefix of the player track (default: own)")
    ap.add_argument("--origin-x", type=float, default=0.0, help="world x of grid column 0 (default: 0)")
    ap.add_argument("--origin-z", type=float, default=0.0, help="world z of grid row 0 (default: 0)")
    ap.add_argument("--padding", type=int, default=1, help="white border, in blocks (default: 1)")
    ap.add_argument("--upscale", type=int, default=1, help="enlarge each block to NxN px (default: 1)")
    ap.add_argument("--flip-z", action="store_true", help="put row 0 at the bottom (north up)")
    ap.add_argument("-o", "--output", default="heatmap.png", help="output PNG (default: heatmap.png)")
    args = ap.parse_args()

    # ---- map size + gray block layer ----
    W = H = None
    mask = None
    if args.level:
        H, W, mask = load_block_mask(args.level, args.width, args.height)
    if args.width:
        W = args.width
    if args.height:
        H = args.height
    if W is None or H is None:
        ap.error("map size unknown: pass --width/--height or --level")

    # ---- canvas (white) + gray blocks ----
    pad = args.padding
    img = np.ones((H + 2 * pad, W + 2 * pad, 3), dtype=float)   # white background + border
    if mask is not None:
        zs, xs = np.where(mask[:H, :W])
        img[pad + zs, pad + xs] = GRAY

    # ---- read trace + detect tracks ----
    with open(args.trace, encoding="latin-1") as f:
        reader = csv.DictReader(f)
        rows = list(reader)
        fieldnames = reader.fieldnames
    player_cols, mobs = detect_tracks(fieldnames, args.player)

    # Draw the player first and the mobs on top: where a mob and the player share a
    # cell (same 1-block pixel) the mob wins, so mob paths stay visible even when the
    # player walked over the same spot.
    print("Tracks:")
    if player_cols:
        cells = read_cells(rows, player_cols[0], player_cols[1], args.origin_x, args.origin_z, W, H)
        paint_path(img, cells, PLAYER, pad)
        print(f"  player '{args.player}' -> RGB {PLAYER} (green)")
    else:
        print(f"  ! no player track '{args.player}_x'/'{args.player}_z' found in {args.trace}")
    for i, (name, xcol, zcol) in enumerate(mobs):
        color = MOB_COLORS[i % len(MOB_COLORS)]
        cells = read_cells(rows, xcol, zcol, args.origin_x, args.origin_z, W, H)
        paint_path(img, cells, color, pad)
        print(f"  mob '{name}'  -> RGB {tuple(round(c, 2) for c in color)}")

    # ---- orientation + upscale + save (imsave writes exact pixels, no interpolation) ----
    if args.flip_z:
        img = img[::-1]
    if args.upscale > 1:
        img = np.repeat(np.repeat(img, args.upscale, axis=0), args.upscale, axis=1)
    plt.imsave(args.output, np.clip(img, 0.0, 1.0))
    print(f"wrote {args.output}  ({img.shape[1]}x{img.shape[0]} px; "
          f"{W}x{H} blocks + {pad} pad, upscale {args.upscale})")


if __name__ == "__main__":
    main()
