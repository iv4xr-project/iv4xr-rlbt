#!/bin/bash
#
# generate_minecraft_heatmap.sh — render a top-down path map (1 px = 1 block) for a
# Minecraft baseline run, via src/main/resources/scripts/heatmap_minecraft.py.
#
# White background (+ white border), gray blocks from the level CSV, green player
# path, one distinct color per mob path.
#
# Usage:
#   ./generate_minecraft_heatmap.sh <session_dir> [level_csv] [width] [height] [output_png]
#
#   <session_dir>  run folder that contains ticks.csv, e.g.
#                  rlbt-files/minecraft-results/arena/baseline/<systemtime>
#   [level_csv]    default: src/test/resources/minecraft-levels/arena.csv
#   [width]        map width  in blocks (default: 20)
#   [height]       map height in blocks (default: 20)
#   [output_png]   default: <session_dir>/heatmap.png
#
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
heatmap_script="$repo_dir/src/main/resources/scripts/heatmap_minecraft.py"

session_dir="${1:?usage: $0 <session_dir> [level_csv] [width] [height] [output_png]}"
level_csv="${2:-$repo_dir/src/test/resources/minecraft-levels/arena.csv}"
width="${3:-20}"
height="${4:-20}"
output="${5:-$session_dir/heatmap.png}"

trace="$session_dir/ticks.csv"
if [[ ! -f "$trace" ]]; then
    echo "error: trace not found: $trace" >&2
    exit 1
fi

python_cmd="${PYTHON:-python3}"
pip3 install --quiet matplotlib numpy

echo "Rendering heatmap"
echo "  trace  : $trace"
echo "  level  : $level_csv"
echo "  size   : ${width}x${height} blocks"
echo "  output : $output"
"$python_cmd" "$heatmap_script" \
    --trace "$trace" \
    --level "$level_csv" \
    --width "$width" --height "$height" \
    --padding 1 \
    --upscale 20 \
    -o "$output"
