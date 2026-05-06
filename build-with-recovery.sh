#!/bin/bash
# Builds the project, recovering stuck transform-cache moves between retries.
# Workaround for Windows Defender holding transient handles on transform workspaces.
#
# Default target: assembleRelease (minified + debug-signed). Override by passing
# any other gradle task as $1, e.g. `bash build-with-recovery.sh assembleDebug`.

export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot"
export ANDROID_HOME="/c/Users/Zach/android-sdk"
export GRADLE_USER_HOME="C:/g"
export PATH="$JAVA_HOME/bin:$PATH"

CACHE_DIR="C:/g/caches/8.11.1/transforms"
MAX_ATTEMPTS=15
ATTEMPT=0
GRADLE_TASK="${1:-assembleRelease}"

cd "$(dirname "$0")"

while [ "$ATTEMPT" -lt "$MAX_ATTEMPTS" ]; do
    ATTEMPT=$((ATTEMPT + 1))
    echo "==== Attempt $ATTEMPT ($GRADLE_TASK) ===="

    LOG_FILE=$(mktemp)
    ./gradlew.bat --no-daemon --no-parallel --max-workers=1 "$GRADLE_TASK" > "$LOG_FILE" 2>&1
    GRADLE_RC=$?
    tail -20 "$LOG_FILE"
    rm -f "$LOG_FILE"

    if [ "$GRADLE_RC" -eq 0 ]; then
        echo "==== BUILD SUCCEEDED on attempt $ATTEMPT ===="
        exit 0
    fi

    echo "==== Build failed (rc=$GRADLE_RC); recovering stuck transforms ===="
    if [ -d "$CACHE_DIR" ]; then
        RECOVERED=0
        for tmp in "$CACHE_DIR"/*-*-*-*-*-*; do
            [ -d "$tmp" ] || continue
            base=$(basename "$tmp")
            hash="${base:0:32}"
            target="$CACHE_DIR/$hash"
            if [ ! -e "$target" ]; then
                echo "Recovering: $base -> $hash"
                powershell -Command "Rename-Item -Path '$tmp' -NewName '$hash' -ErrorAction SilentlyContinue" 2>/dev/null
                if [ -d "$target" ]; then
                    RECOVERED=$((RECOVERED + 1))
                fi
            fi
        done
        echo "Recovered $RECOVERED transforms; retrying"
    fi
done

echo "==== Failed after $MAX_ATTEMPTS attempts ===="
exit 1
