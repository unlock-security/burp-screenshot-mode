#!/usr/bin/env bash
# Compiles a copy of RenderHarness.java against the project and Burp's jar, then runs it in the light and dark themes.
# Usage: render.sh <RenderHarness.java> <output dir> [font scale, e.g. 1.5]
# Burp's jar: $BURP_JAR, or the default macOS install.
set -euo pipefail

harness=$1
out=$2
scale=${3:-1}
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
javac -d "$classes" -cp "$cp" "$harness"
for theme in light dark; do
    java -Dapple.awt.UIElement=true -cp "$classes:$cp" it.unlocksecurity.burp.screenshotmode.ui.RenderHarness \
        "$out" "$theme" "$scale"
done
