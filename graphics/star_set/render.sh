#!/bin/bash
# recolor $base_file in 8 hues (as in rainbow) and store in $base_dir
# make 1..7 holes in every file in $base_dir by hole masks from $holes_dir adding hole outline from $outlines_dir and storing in $output_dir
base_file=${1:-"chip.png"}
base_dir=${2:-"bases/"}
holes_dir=${3:-"holes/"}
outlines_dir=${4:-"hole_outlines/"}
output_dir=${5:-"set/"}
render_hues_script=${6:-"./render_hues.sh"}
render_base_script=${7:-"./render_base.sh"}
render_bottom_script=${8:-"./render_bottom.sh"}

$render_hues_script $base_file $base_dir
for base_file in ${base_dir%/}/*.png
do
    if [[ -f $base_file ]]
    then
        $render_base_script $base_file $holes_dir $outlines_dir $output_dir
    fi
done
$render_bottom_script "chip-bottom.png" $holes_dir $outlines_dir $output_dir "chip"
