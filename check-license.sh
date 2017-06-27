#!/usr/bin/env bash
set -e
PREAMBLE=$(cat <<-EOF
/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */
EOF
)

NO_LICENSE=$(find . -type f -name "*.kt"  -print0 | xargs -0 grep -L -F "$PREAMBLE")

if [[ $NO_LICENSE ]]; then
    echo "Files without license notice:"
    echo "$NO_LICENSE"
    exit -1
else
    echo "License OK!"
fi
