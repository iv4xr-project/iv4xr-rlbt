# !/bin/bash

python_cmd="python3"
heatmap_script="/Users/kifetew/workspace/projects/iv4xr/RL/WP3/iv4xr-rlbt/src/main/resources/scripts/heatmap.py"
input_dir=$1
base_map=$2
width=$3
height=$4
output=$5

#install required libraries
pip3 install matplotlib

echo "$python_cmd $heatmap_script --dir $input_dir --basemap $base_map --width=$width --height=$height --xname posx --yname posz -o $output"
$python_cmd $heatmap_script --dir $input_dir --basemap $base_map --width=$width --height=$height --xname posx --yname posz -o $output

