#!/bin/bash
set -euo pipefail

# Keep adaptive foreground artwork inside Android's safe area.
logo="docs/generative_logo.png"
declare -A sizes=( ["mdpi"]="108x108" ["hdpi"]="162x162" ["xhdpi"]="216x216" ["xxhdpi"]="324x324" ["xxxhdpi"]="432x432" )

for density in "${!sizes[@]}"; do
    size=${sizes[$density]}
    for mode in light night; do
        if [[ "$mode" == "night" ]]; then
            dir="app/src/main/res/mipmap-night-$density"
        else
            dir="app/src/main/res/mipmap-$density"
        fi
        mkdir -p "$dir"
        convert "$logo" -trim +repage -resize '72%x72%' -gravity center -background none -extent "$size" "$dir/ic_launcher_foreground.png"
    done
done
