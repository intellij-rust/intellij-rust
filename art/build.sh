#/bin/bash

# Batch rasterize SVG icon file to PNG
# Requires rsvg-convert command.
# Please re-run this script after each source file change.

INPUT_FILE='icon_intellij_rust.svg'
FILE_NAME=$(basename "$INPUT_FILE" .svg)

for SIZE in 16 24 32 48 64 128 256 512; do
    OUTPUT_FILE="${FILE_NAME}_${SIZE}.png"
    rsvg-convert $INPUT_FILE -o $OUTPUT_FILE -w $SIZE -h $SIZE
done

