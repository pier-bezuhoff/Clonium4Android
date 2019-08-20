#!/bin/bash
base_file=${1:-"0.png"}
holed_dir=${2:-"holed/"}
holes_dir=${3:-"holes/"}
output_dir=${4:-"set/"}

# for hole_filename in $(ls $holes_dir)
# do
#     hole_file=${holes_dir%/}/$hole_filename
#     if [[ -f $hole_file ]]
#     then
#         n_holes=${hole_filename%%.*}
#         holed_file=${holed_dir%/}/$n_holes.png
#         composite $hole_file $base_file $holed_file
#     fi
# done

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

for color_id in {0..7}
do
    hue_shift=$((color_id * 25))
    output_file=${output_dir%/}/$color_id-0.png
    convert $base_file -modulate 100,100,$hue_shift $output_file
done
