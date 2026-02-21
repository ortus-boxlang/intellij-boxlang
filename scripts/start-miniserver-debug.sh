#!/bin/bash
# Starts the BoxLang MiniServer with JDWP debugging enabled.
#
# Usage: ./scripts/start-miniserver-debug.sh [--port 5005] [--webroot ./debug-webroot]
#
# Environment variables:
#   JDWP_PORT      - JDWP debug port (default: 5005)
#   WEBROOT        - Path to the webroot directory (default: ./debug-webroot)
#   MINISERVER_JAR - Explicit path to the miniserver JAR
#   BOXLANG_HOME   - BoxLang home directory
#
# Prerequisites:
#   - BoxLang MiniServer JAR must exist (checks sibling repo or ~/.boxlang)
#   - The --debug flag enables BoxLang's DebuggerExternalConnectionUtil
#
# After starting, create an "Attach to BoxLang" debug config in IntelliJ
# with port 5005 and click Debug.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Parse command-line arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --port)
            JDWP_PORT="$2"
            shift 2
            ;;
        --webroot)
            WEBROOT="$2"
            shift 2
            ;;
        --jar)
            MINISERVER_JAR="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: $0 [--port PORT] [--webroot DIR] [--jar JAR_PATH]"
            echo ""
            echo "Options:"
            echo "  --port PORT      JDWP debug port (default: 5005)"
            echo "  --webroot DIR    Webroot directory (default: ./debug-webroot)"
            echo "  --jar JAR_PATH   Explicit path to miniserver JAR"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

JDWP_PORT="${JDWP_PORT:-5005}"
WEBROOT="${WEBROOT:-$PROJECT_DIR/debug-webroot}"

# Resolve webroot to absolute path
WEBROOT="$(cd "$WEBROOT" 2>/dev/null && pwd || echo "$WEBROOT")"

# Try to find the miniserver JAR if not explicitly set
if [ -z "$MINISERVER_JAR" ]; then
    # 1. Sibling repo (development)
    for jar in "$PROJECT_DIR"/../boxlang-miniserver/build/distributions/boxlang-miniserver-*-snapshot.jar; do
        if [ -f "$jar" ]; then
            MINISERVER_JAR="$jar"
            break
        fi
    done
fi

if [ -z "$MINISERVER_JAR" ]; then
    # 2. BoxLang home lib directory
    BL_HOME="${BOXLANG_HOME:-$HOME/.boxlang}"
    for jar in "$BL_HOME"/lib/boxlang-miniserver*.jar; do
        if [ -f "$jar" ]; then
            MINISERVER_JAR="$jar"
            break
        fi
    done
fi

if [ -z "$MINISERVER_JAR" ]; then
    echo "ERROR: Could not find boxlang-miniserver JAR."
    echo ""
    echo "Looked in:"
    echo "  1. $PROJECT_DIR/../boxlang-miniserver/build/distributions/"
    echo "  2. ${BOXLANG_HOME:-$HOME/.boxlang}/lib/"
    echo ""
    echo "Options:"
    echo "  - Build it: cd ../boxlang-miniserver && ./gradlew build"
    echo "  - Set explicitly: $0 --jar /path/to/miniserver.jar"
    echo "  - Set MINISERVER_JAR environment variable"
    exit 1
fi

if [ ! -d "$WEBROOT" ]; then
    echo "ERROR: Webroot directory not found: $WEBROOT"
    exit 1
fi

echo "======================================="
echo "  BoxLang MiniServer Debug Mode"
echo "======================================="
echo "  JDWP Port:  $JDWP_PORT"
echo "  Webroot:    $WEBROOT"
echo "  JAR:        $MINISERVER_JAR"
echo ""
echo "  Web server: http://localhost:8080/"
echo ""
echo "  To debug in IntelliJ:"
echo "    1. Create 'Attach to BoxLang' run config"
echo "    2. Set Host=localhost, Port=$JDWP_PORT"
echo "    3. Set Local Root=$WEBROOT"
echo "    4. Set Remote Root=$WEBROOT"
echo "    5. Click Debug"
echo "    6. Open http://localhost:8080/ in browser"
echo "======================================="
echo ""

# Resolve java executable: prefer JAVA_HOME, then fall back to PATH
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    JAVA_CMD="java"
fi

exec "$JAVA_CMD" \
    -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:"$JDWP_PORT" \
    -jar "$MINISERVER_JAR" \
    --webroot "$WEBROOT" \
    --debug
