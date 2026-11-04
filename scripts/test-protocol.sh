#!/bin/bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TASK_JAVA="${LAB_JAVA_HOME:-${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}}"
mkdir -p "$TASK_ROOT/build/protocol-tests"
"$TASK_JAVA/bin/javac" -d "$TASK_ROOT/build/protocol-tests" "$TASK_ROOT/probe/src/com/rlv/ivilab/LabProtocol.java" "$TASK_ROOT/probe/src/com/rlv/ivilab/NativePolicy.java" "$TASK_ROOT/scripts/ProtocolCheck.java" "$TASK_ROOT/scripts/NativePolicyCheck.java"
"$TASK_JAVA/bin/java" -cp "$TASK_ROOT/build/protocol-tests" ProtocolCheck
"$TASK_JAVA/bin/java" -cp "$TASK_ROOT/build/protocol-tests" NativePolicyCheck
