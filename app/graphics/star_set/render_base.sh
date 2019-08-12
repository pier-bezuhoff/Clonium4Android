#!/bin/bash
# usage: ./render_base.sh base.png holes/ hole_outlines/ set/
# see: https://imagemagick.org/Usage/compose/#dstout
base_file=$1
base_filename=${base_file##*/}
base_name=${base_filename%%.*}
holes_dir=$2
outlines_dir=$3
output_dir=$4
for outline_filename in $(ls $outlines_dir)
do
    outline_file=${outlines_dir%/}/$outline_filename
    outline_name=${outline_filename%%.*}
    output_file=${output_dir%/}/$base_name-$outline_name.png
    composite $outline_file $base_file $output_file
done
