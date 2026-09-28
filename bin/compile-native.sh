#!/bin/bash
set -e
cd "$(dirname "$0")/.."
gcc -shared -fPIC -o native/usejni.so native/usejni.c \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux"
echo "Built native/usejni.so"

gcc -shared -fPIC -o native/jvmtiagent.so native/jvmtiagent.c \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux"
echo "Built native/jvmtiagent.so"

gcc -shared -fPIC -o native/ffmlibrary.so native/ffmlibrary.c \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  -L"$JAVA_HOME/lib/server" -ljvm -Wl,-rpath,"$JAVA_HOME/lib/server"
echo "Built native/ffmlibrary.so"
