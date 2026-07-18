#!/usr/bin/env bash
set -euo pipefail

# Build jadx-plugin-method-role-tagger.jar
#
# Usage:
#   JADX_JAR=/path/to/jadx-all.jar ./build.sh          # Use local jadx JAR
#   JADX_VERSION=1.5.1 ./build.sh                       # Download jadx release
#   ./build.sh                                          # Default: JADX_VERSION=1.5.1

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/.build-cache"
OUT_JAR="$SCRIPT_DIR/jadx-plugin-method-role-tagger.jar"

JADX_VERSION="${JADX_VERSION:-1.5.1}"

# Resolve jadx JAR for compilation classpath
if [ -n "${JADX_JAR:-}" ] && [ -f "$JADX_JAR" ]; then
    echo "Using local jadx JAR: $JADX_JAR"
    CP_JAR="$JADX_JAR"
else
    CACHED_JAR="$BUILD_DIR/jadx-$JADX_VERSION-all.jar"
    if [ -f "$CACHED_JAR" ]; then
        echo "Using cached jadx $JADX_VERSION"
    else
        echo "Downloading jadx $JADX_VERSION..."
        mkdir -p "$BUILD_DIR"
        DOWNLOAD_URL="https://github.com/skylot/jadx/releases/download/v${JADX_VERSION}/jadx-${JADX_VERSION}.zip"
        TEMP_ZIP="$BUILD_DIR/jadx-${JADX_VERSION}.zip"
        curl -fSL "$DOWNLOAD_URL" -o "$TEMP_ZIP"
        unzip -o "$TEMP_ZIP" "lib/jadx-*-all.jar" -d "$BUILD_DIR/extract"
        mv "$BUILD_DIR/extract/lib"/jadx-*-all.jar "$CACHED_JAR"
        rm -rf "$BUILD_DIR/extract" "$TEMP_ZIP"
    fi
    CP_JAR="$CACHED_JAR"
fi

# Compile
echo "Compiling with --release 11..."
CLASSES_DIR="$BUILD_DIR/classes"
rm -rf "$CLASSES_DIR"
mkdir -p "$CLASSES_DIR"

javac --release 11 \
    -cp "$CP_JAR" \
    -d "$CLASSES_DIR" \
    "$SCRIPT_DIR/src/main/java/io/github/gmutinel/jadx/methodroletagger/MethodRoleTaggerPlugin.java"

# Package JAR (classes + SPI service file)
echo "Packaging JAR..."
jar cf "$OUT_JAR" \
    -C "$CLASSES_DIR" . \
    -C "$SCRIPT_DIR/src/main/resources" .

echo "Built: $OUT_JAR ($(wc -c < "$OUT_JAR") bytes)"
