#!/bin/bash

# Script that runs the tests and checks results on various JDKs.
# Optional arguments (in any order):
#   Major Java version (e.g. 11, 17) - limit to that JDK; can be repeated
#   Test name (e.g. ProcMemHeap, SetAccessible) - limit to that test; can be repeated

### Selection of java versions and tests; command line parsing ###

# see `sdk list java`, https://www.java.com/releases/
#
# Versions 12-16, 18-20 are old short-term-support releases that vendors have dropped from
# SDKMAN. For those we download a JDK straight from Azul's Zulu CDN into jdks/ instead of using
# SDKMAN; see zulu_urls below and the install step in the loop over $java_versions.
java_versions="11.0.32-tem 12.0.2 13.0.14 14.0.2 15.0.10 16.0.2 17.0.20-tem 18.0.2.1 19.0.2 20.0.2 21.0.12-oracle 22.0.2-oracle 23.0.2-oracle 24.0.2-oracle 25.0.4-oracle 26.0.2-oracle 27.0.0-oracle 28.0.0+ea.16-open"

declare -A zulu_urls=(
  [12.0.2]=https://cdn.azul.com/zulu/bin/zulu12.3.11-ca-jdk12.0.2-linux_x64.tar.gz
  [13.0.14]=https://cdn.azul.com/zulu/bin/zulu13.54.17-ca-jdk13.0.14-linux_x64.tar.gz
  [14.0.2]=https://cdn.azul.com/zulu/bin/zulu14.29.23-ca-jdk14.0.2-linux_x64.tar.gz
  [15.0.10]=https://cdn.azul.com/zulu/bin/zulu15.46.17-ca-jdk15.0.10-linux_x64.tar.gz
  [16.0.2]=https://cdn.azul.com/zulu/bin/zulu16.32.15-ca-jdk16.0.2-linux_x64.tar.gz
  [18.0.2.1]=https://cdn.azul.com/zulu/bin/zulu18.32.13-ca-jdk18.0.2.1-linux_x64.tar.gz
  [19.0.2]=https://cdn.azul.com/zulu/bin/zulu19.32.13-ca-jdk19.0.2-linux_x64.tar.gz
  [20.0.2]=https://cdn.azul.com/zulu/bin/zulu20.32.11-ca-jdk20.0.2-linux_x64.tar.gz
)

tests="SetAccessible SetAccessibleFiltered UnsafeGet UnsafePut CtorForSerialization UnsafeNoWarning UseJNI UseNarcissus AttachJavaAgent AttachJvmtiAgent ProcMemHeap ProcMemBytecode FfmLibJvm FfmHeapSimple FfmHeap FfmBytecode FfmLibrary PatchJimage"
# deliberately leaving out ProcMemHeapSimple because it's too slow

filter_versions=""
filter_tests=""
for arg in "$@"; do
  if [[ "$arg" =~ ^[0-9]+$ ]]; then
    matched=false
    for v in $java_versions; do
      if [ "$(echo "$v" | sed -E 's/^([0-9]+).*/\1/')" = "$arg" ]; then
        filter_versions="$filter_versions $v"
        matched=true
        break
      fi
    done
    $matched || { echo "Unknown Java version: $arg"; exit 1; }
  else
    matched=false
    for t in $tests; do
      if [ "$t" = "$arg" ]; then
        filter_tests="$filter_tests $t"
        matched=true
        break
      fi
    done
    $matched || { echo "Unknown test: $arg"; exit 1; }
  fi
done
[ -n "$filter_versions" ] && java_versions="${filter_versions# }"
[ -n "$filter_tests" ] && tests="${filter_tests# }"

### Setup environment ###

source "$HOME/.sdkman/bin/sdkman-init.sh"
set -e -o pipefail

if [ ! -e lib/narcissus.jar ]; then
  mkdir -p lib
  curl -f --retry 5 --retry-delay 10 -o lib/narcissus.jar https://maven-central.storage-download.googleapis.com/maven2/io/github/toolfactory/narcissus/1.0.7/narcissus-1.0.7.jar
fi

# for testing
#if [ ! -e lib/jol-cli.jar ]; then
#  mkdir -p lib
#  curl -o lib/jol-cli.jar https://repo.maven.apache.org/maven2/org/openjdk/jol/jol-cli/0.17/jol-cli-0.17-full.jar
#fi
#java -jar lib/jol-cli.jar internals -cp target 'decapsulation.FfmHeapSimple$Victim'
#java -XX:-UseCompressedOops -jar lib/jol-cli.jar internals -cp target 'decapsulation.FfmHeapSimple$Victim'

mkdir -p results
all_results=()

# Baseline PATH without any JDK this script has put in front of it, so each iteration starts
# clean instead of accumulating every previously-used Zulu JDK's bin dir at the front of PATH
# (which would otherwise keep shadowing the JDK selected by SDKMAN in later iterations).
base_path="$PATH"

### Loop over selected java versions ###

for java_version in $java_versions; do
  ### Install JDK and compile ###

  PATH="$base_path"

  if [ -n "${zulu_urls[$java_version]:-}" ]; then
    # No longer available via SDKMAN; download from Azul's Zulu CDN into jdks/ instead.
    jdk_dir="jdks/$java_version"
    if [ ! -x "$jdk_dir/bin/java" ]; then
      mkdir -p jdks
      archive="jdks/zulu-$java_version.tar.gz"
      echo "Downloading JDK $java_version from Azul Zulu CDN into $jdk_dir ..."
      curl -f --retry 5 --retry-delay 10 -o "$archive" "${zulu_urls[$java_version]}"
      # drain tar's full listing (not just head -1) so it isn't killed by SIGPIPE under pipefail
      extracted_name=$(tar -tzf "$archive" | { head -1; cat >/dev/null; } | cut -f1 -d/)
      rm -rf "${jdk_dir:?}" "jdks/$extracted_name"
      tar -xzf "$archive" -C jdks
      mv "jdks/$extracted_name" "$jdk_dir"
      rm "$archive"
    fi
    export JAVA_HOME="$PWD/$jdk_dir"
    export PATH="$JAVA_HOME/bin:$PATH"
  else
    if ! sdk use java "$java_version"; then
     echo n | sdk install java "$java_version"
     sdk use java "$java_version"
    fi
  fi

  # major java version, but keep "ea" as a suffix for early access releases
  major_java_version=$(echo $java_version | sed -E 's/^([0-9]+).*/\1/')
  [[ $java_version == *ea* ]] && major_java_version+=ea
  echo major_java_version=$major_java_version

  # Determine tests to run on this java version; Foreign* require Java 22+
  version_tests=""
  for test in $tests; do
    if [[ "$test" == Ffm* ]] && [ "$major_java_version" -lt 22 ]; then
      continue
    fi
    version_tests+=" $test"
  done
  version_tests="${version_tests# }"

  to_compile="src/decapsulation/Decapsulater.java"
  for test in $version_tests; do
    to_compile+=" src/decapsulation/${test}.java"
  done

  rm -rf target
  mkdir target
  echo -ne '\033[1;30m' # compiler output in dark gray, we don't care about it unless it fails
  javac -nowarn -Xlint:none -XDignore.symbol.file -d target -cp 'lib/*' $to_compile
  if echo "$version_tests" | grep -qw PatchJimage; then
    # Compile the replacement class with --patch-module so it can access package-private
    # IMPL_LOOKUP within java.lang.invoke; -g:none for minimal output size.
    # Output to a scratch dir, then copy to a non-conflicting classpath resource path so
    # PatchJimage can load the bytes without ambiguity with the JDK's own class.
    mkdir -p target/patch target/decapsulation
    javac -nowarn -Xlint:none -g:none --patch-module java.base=src/java \
      -d target/patch src/java/lang/invoke/StringConcatException.java
    mv target/patch/java/lang/invoke/StringConcatException.class \
       target/decapsulation/StringConcatException_patch.class
  fi
  echo -ne '\033[0m' # reset color
  echo

  ### Run the tests ###

  for test in $version_tests; do
    # ProcMemHeap and FfmHeap are sensitive to compressed oops; run both with and without
    if [ "$test" = "ProcMemHeap" ] || [ "$test" = "FfmHeap" ]; then
      variants="normal 64BitOOP"
    elif [ "$test" = "ProcMemHeapSimple" ] || [ "$test" = "FfmHeapSimple" ]; then
      variants="64BitOOP"
    else
      variants="normal"
    fi

    for variant in $variants; do
      if [ "$variant" = "64BitOOP" ]; then
        opts="-XX:-UseCompressedOops"
        name_suffix="-64BitOOP"
      else
        opts=""
        name_suffix=""
      fi

      stderr=results/$test-$major_java_version$name_suffix
      out=$(java $opts -cp 'target:lib/*' decapsulation."$test" 2> "$stderr" || true)
      if [ "$out" = PASS ]; then
        if [ -s "$stderr" ]; then
          if grep -q WARNING "$stderr"; then
            result=WARNING
          else
            echo Unexpected output on stderr:
            cat "$stderr"
            exit 1
          fi
        else
          result=PASS
        fi
      elif [ "$out" = FAIL ]; then
        result=FAIL
      else
        echo Unexpected output on stdout:
        echo "$out"
        if [ -s "$stderr" ]; then
          echo stderr:
          cat "$stderr"
        fi
        exit 1
      fi

      echo "On $java_version$name_suffix $test: $result"
      all_results+=("$test $major_java_version$name_suffix $result")

      if [ "$result" != PASS ] && [ "$result" != FAIL ]; then
        cat "$stderr"
      fi
      if [ -s "$stderr" ]; then
        # strip part of output that depends on current dir or is non-deterministic
        sed -E -i "s,\(file:.*(target|lib),(file:/...,; s,decapsulation[0-9]+,decapsulation123,; s,unnamed module @[0-9a-f]{8},unnamed module @abcd1234," "$stderr"
      else
        rm "$stderr"
      fi
    done
  done
done

# Merge results into previous results if they exist;
# so that when running only a subset of the tests previous results are preserved
if [ -e results/all_results ]; then
  old_results="$(cat results/all_results)"
else
  old_results=""
fi
(printf "%s\n" "${all_results[@]}"; echo "$old_results") | grep -vE '^$' | sort | uniq > results/all_results