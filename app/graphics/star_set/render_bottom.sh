#!/bin/bash
base_file=${1:-"chip-bottom.png"}
base_filename=${base_file##*/}
base_name=${base_filename%%.*}
holes_dir=${2:-"holes/"}
outlines_dir=${3:-"hole_outlines/"}
output_dir=${4:-"set/"}
prefix=${5:-"chip"}
for player_id in {0..7}
do
    hue_shift=$((player_id * 25))
    output_file=${output_dir%/}/$prefix-$player_id-0.png
    convert $base_file -modulate 100,100,$hue_shift $output_file
    hole_file=${holes_dir%/}/1.png
    composite -compose Dst_Out $hole_file $output_file $output_file
    outline_file=${outlines_dir%/}/1.png
    composite $outline_file $output_file $output_file
done
