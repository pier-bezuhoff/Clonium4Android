#!/bin/bash
# make 1..7 holes in every file in bases/ by hole masks from holes/ adding hole outline from hole_outlines/ and storing in set/
base_dir=${1:-"bases/"}
holes_dir=${2:-"holes/"}
outlines_dir=${3:-"hole_outlines/"}
output_dir=${4:-"set/"}
render_base_script=${5:-"./render_base.sh"}
for base_file in ${base_dir%/}/*.png
do
    if [[ -f $base_file ]]
    then
        $render_base_script $base_file $holes_dir $outlines_dir $output_dir
    fi
done
