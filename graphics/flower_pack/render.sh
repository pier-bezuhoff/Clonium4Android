#!/bin/bash
holed_dir=${2:-"nature/"}
output_dir=${4:-"set/"}

for holed_filename in $(ls $holed_dir)
do
    holed_file=${holed_dir%/}/$holed_filename
    for color_id in {0..7}
    do
        hue_shift=$((color_id * 25))
        output_file=${output_dir%/}/$color_id-$holed_filename
        convert $holed_file -modulate 100,100,$hue_shift $output_file
    done
done
