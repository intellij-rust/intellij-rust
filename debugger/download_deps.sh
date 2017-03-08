#!/bin/sh

set -e

mkdir -p lib
cd lib
wget --no-clobber https://download.jetbrains.com/cpp/CLion-2016.3.3.tar.gz
tar -xzvf CLion-2016.3.3.tar.gz
