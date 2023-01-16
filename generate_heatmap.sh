# !/bin/bash

# recursively look for position trace files in 'rlbt-files' and generate a heatmap for each set of runs
# Note that the script should be launched from the same directory that contains 'rlbt-files'
# the heatmap will be stored in the root output folder for the particular run, i.e. where episodeSummary.txt file is located

python_cmd="python3"
heatmap_script="src/main/resources/scripts/heatmap.py"

for f in $(find rlbt-files -name episodeSummary.txt)
do
    base_dir=$(dirname $f)
    $python_cmd $heatmap_script --dir $base_dir --width=100 --height=100 --xname posx --yname posz -o $base_dir/heatmap.png
done

