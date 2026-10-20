#!/bin/bash
set -euo pipefail

# The canonical app logo is maintained in docs/generative_logo.png.
logo="docs/generative_logo.png"
declare -A sizes=( ["mdpi"]="48x48" ["hdpi"]="72x72" ["xhdpi"]="96x96" ["xxhdpi"]="144x144" ["xxxhdpi"]="192x192" )

for density in "${!sizes[@]}"; do
    size=${sizes[$density]}
    for mode in light night; do
        if [[ "$mode" == "night" ]]; then
            dir="app/src/main/res/mipmap-night-$density"
        else
            dir="app/src/main/res/mipmap-$density"
        fi
        mkdir -p "$dir"
        convert "$logo" -trim +repage -resize '82%x82%' -gravity center -background white -extent "$size" "$dir/ic_launcher.png"
        convert "$logo" -trim +repage -resize '82%x82%' -gravity center -background white -extent "$size" "$dir/ic_launcher_round.png"
    done
done
