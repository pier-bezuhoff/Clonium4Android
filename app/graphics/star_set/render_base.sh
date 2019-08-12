#!/bin/bash
# usage: ./render_base.sh base.png holes/ hole_outlines/ set/
# see: https://imagemagick.org/Usage/compose/#dstout
base_file=$1
base_filename=${base_file##*/}
base_name=${base_filename%%.*}
holes_dir=$2
outlines_dir=$3
output_dir=$4
for hole_filename in $(ls $holes_dir)
do
    hole_file=${holes_dir%/}/$hole_filename
    if [[ -f $hole_file ]]
    then
        hole_name=${hole_filename%%.*}
        output_file=${output_dir%/}/$base_name-$hole_name.png
        composite -compose Dst_Out $hole_file $base_file $output_file
    fi
    outline_file=${outlines_dir%/}/$hole_filename
    if [[ -f $outline_file ]]
    then
        composite $outline_file $output_file $output_file
    fi
done
