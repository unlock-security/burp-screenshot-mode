#!/usr/bin/env bash
# Builds the README images into an output directory: the five screenshots (light and dark) and demo.gif.
# Usage: make.sh <output dir> [screenshots|gif|all]
# Burp's jar: $BURP_JAR, or the default macOS install. Windows open on screen while it runs: don't use the mouse.
set -euo pipefail

out=$1
what=${2:-all}
skills=$(cd "$(dirname "$0")/.." && pwd)
repo=$(git rev-parse --show-toplevel)
burp=${BURP_JAR:-"/Applications/Burp Suite.app/Contents/Resources/app/burpsuite.jar"}
if [[ ! -f $burp ]]; then
    echo "Burp's jar not found at $burp: set BURP_JAR to its path." >&2
    exit 1
fi

(cd "$repo" && ./gradlew compileJava processResources -q)
gson=$(find ~/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson -name 'gson-*.jar' ! -name '*sources*' | sort | tail -1)
cp="$repo/build/classes/java/main:$repo/build/resources/main:$gson:$burp"
classes=$(mktemp -d)
mkdir -p "$out"
javac -d "$classes" -cp "$cp" "$skills/render-ui/RenderHarness.java" "$skills"/readme-assets/*.java
run() {
    java -Dapple.awt.UIElement=true -cp "$classes:$cp" "it.unlocksecurity.burp.screenshotmode.ui.$@"
}
if [[ $what == screenshots || $what == all ]]; then
    run Screenshots "$out" light
    run Screenshots "$out" dark
fi
if [[ $what == gif || $what == all ]]; then
    run Demo "$out/demo.gif"
fi
