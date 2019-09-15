#!/bin/bash
extension="png"
dir_order="common skelet city end steve animals water fire zombie"
color_id=0
for dir in $dir_order
do
    if [ -d $dir ]
    then
        echo 1
        for filename in $(ls $dir)
        do
            level=${filename%.$extension}
            path=$dir/$filename
            new_path=$color_id-$level.$extension
            mv $path $new_path
        done
        $(( color_id++ ))
    fi
done
