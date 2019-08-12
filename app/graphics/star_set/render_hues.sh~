#!/bin/bash
# usage: ./render_hues.sh base.png output_dir/
base_file=$1
base_filename=${base_file##*/}
base_name=${base_filename%%.*}
output_dir=$2
for player_id in {0..7}
do
    hue_shift=$((player_id * 25))
    output_file=${output_dir%/}/$base_name-$player_id.png
    convert $base_file -modulate 100,100,$hue_shift $output_file
done
