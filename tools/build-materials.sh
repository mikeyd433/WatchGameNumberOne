#!/usr/bin/env bash
# Recompiles app/src/main/materials/*.mat into app/src/main/assets/materials/*.filamat.
#
# matc must come from the SAME Filament release as the filament-android
# dependency in gradle/libs.versions.toml, or the runtime rejects the material.
# Download it from https://github.com/google/filament/releases
# (filament-v<version>-linux.tgz / -mac.tgz, binary at filament/bin/matc).
#
# Usage: MATC=/path/to/matc tools/build-materials.sh
set -euo pipefail
cd "$(dirname "$0")/.."
MATC="${MATC:-matc}"
mkdir -p app/src/main/assets/materials
for src in app/src/main/materials/*.mat; do
    name="$(basename "$src" .mat)"
    "$MATC" --platform mobile --api opengl -o "app/src/main/assets/materials/$name.filamat" "$src"
    echo "compiled $name"
done
