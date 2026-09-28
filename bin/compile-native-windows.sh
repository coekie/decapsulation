#!/bin/bash
set -e
cd "$(dirname "$0")/.."

WINDOWS_JDK="${1:-/mnt/c/Apps/jdk-25}"

x86_64-w64-mingw32-gcc -shared \
  -o native/usejni.dll \
  native/usejni.c \
  -I"$WINDOWS_JDK/include" \
  -I"$WINDOWS_JDK/include/win32"
echo "Built native/usejni.dll"

x86_64-w64-mingw32-gcc -shared \
  -o native/jvmtiagent.dll \
  native/jvmtiagent.c \
  -I"$WINDOWS_JDK/include" \
  -I"$WINDOWS_JDK/include/win32"
echo "Built native/jvmtiagent.dll"

x86_64-w64-mingw32-gcc -shared \
  -o native/ffmlibrary.dll \
  native/ffmlibrary.c \
  -I"$WINDOWS_JDK/include" \
  -I"$WINDOWS_JDK/include/win32" \
  "$WINDOWS_JDK/lib/jvm.lib"
echo "Built native/ffmlibrary.dll"
