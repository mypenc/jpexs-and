#!/usr/bin/env bash
#builds app/libs/ffdec_android_lib.jar from jpexs ffdec_lib with java.awt remapped to a shim
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PIN=${FFDEC_REF:-de7efbf4754865f46c1c5bd36c9db9cc4ef2fa0b}
W=$ROOT/build/lib
JH=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")
JSRC=$JH/lib/src.zip
[ -f "$JSRC" ] || { echo "missing $JSRC (need a jdk that ships src.zip)"; exit 1; }
EXP="--add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.commons=ALL-UNNAMED"

rm -rf "$W" && mkdir -p "$W"/{tools,shimgen,shimsrc,shimcls,cls,res}
if [ ! -d "$ROOT/build/jpexs/.git" ]; then
    mkdir -p "$ROOT/build/jpexs" && cd "$ROOT/build/jpexs"
    git init -q && git remote add origin https://github.com/jindrapetrik/jpexs-decompiler.git
fi
cd "$ROOT/build/jpexs"
git fetch -q --depth 1 origin "$PIN" && git checkout -q -f FETCH_HEAD
L=$ROOT/build/jpexs/libsrc/ffdec_lib

echo "compile ffdec_lib"
CP=$(ls "$L"/lib/*.jar | tr '\n' ':')
find "$L/src" -name '*.java' > "$W/srcs.txt"
javac -nowarn -encoding UTF-8 --release 8 -proc:none -cp "$CP" -d "$W/cls" @"$W/srcs.txt" 2>&1 | grep -v '^Note' || true
(cd "$L/src" && find . -type f ! -name '*.java' -exec cp --parents {} "$W/res" \;)

echo "generate and compile shim"
javac -d "$W/shimgen" "$ROOT/tools/ShimGen.java"
java -cp "$W/shimgen" ShimGen "$ROOT/tools/shim-classes.txt" "$W/shimsrc"
python3 "$ROOT/tools/overlay.py" "$JSRC" "$W/shimsrc" "$ROOT/tools/real-classes.txt"
cp -r "$ROOT"/shim/. "$W/shimsrc/"
find "$W/shimsrc" -name '*.java' > "$W/shimlist.txt"
javac -nowarn -proc:none --release 17 -d "$W/shimcls" @"$W/shimlist.txt"

echo "remap"
javac $EXP -d "$W/tools" "$ROOT/tools/Remap.java" 2>&1 | grep -v warning || true
JARS=$(ls "$L"/lib/*.jar | grep -vE 'jna|vlcj|flashdebugger' | tr '\n' ' ')
mkdir -p "$ROOT/app/libs"
java $EXP -cp "$W/tools" Remap "$ROOT/app/libs/ffdec_android_lib.jar" "$W/shimcls" "$ROOT/build/shim-report.txt" "$W/cls" "$W/res" $JARS
echo "core refs missing from shim:"
grep -E '^com/jpexs/(decompiler|helpers)' "$ROOT/build/shim-report.txt" || echo none
ls -la "$ROOT/app/libs/ffdec_android_lib.jar"
