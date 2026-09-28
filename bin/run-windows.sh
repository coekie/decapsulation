#!/bin/bash

# Tests a couple decapsulation implementations that are platform-specific under Windows.
# This script is partly copied from but more primitive/scrappy than `run.sh` which runs more tests under more versions
# and takes CLI arguments etc.
# This assumes we're running in WSL.
# This duplication and difference between Windows and Linux is kind of a mess.

# TODO WinMemHeap doesn't work reliably on all versions
tests="${1:-UseJNI AttachJvmtiAgent WinMemBytecode FfmLibJvm FfmLibrary PatchJimage}"

# JDKs are downloaded from Azul's Zulu CDN into $jdks_dir on the Windows filesystem
jdks_dir=/mnt/c/Apps/jdk-decapsulation
declare -A zulu_urls=(
  [11]=https://cdn.azul.com/zulu/bin/zulu11.90.205-ca-jdk11.0.32.1-win_x64.zip
  [12]=https://cdn.azul.com/zulu/bin/zulu12.3.11-ca-jdk12.0.2-win_x64.zip
  [13]=https://cdn.azul.com/zulu/bin/zulu13.54.17-ca-jdk13.0.14-win_x64.zip
  [14]=https://cdn.azul.com/zulu/bin/zulu14.29.23-ca-jdk14.0.2-win_x64.zip
  [15]=https://cdn.azul.com/zulu/bin/zulu15.46.17-ca-jdk15.0.10-win_x64.zip
  [16]=https://cdn.azul.com/zulu/bin/zulu16.32.15-ca-jdk16.0.2-win_x64.zip
  [17]=https://cdn.azul.com/zulu/bin/zulu17.68.203-ca-jdk17.0.20.1-win_x64.zip
  [18]=https://cdn.azul.com/zulu/bin/zulu18.32.13-ca-jdk18.0.2.1-win_x64.zip
  [19]=https://cdn.azul.com/zulu/bin/zulu19.32.13-ca-jdk19.0.2-win_x64.zip
  [20]=https://cdn.azul.com/zulu/bin/zulu20.32.11-ca-jdk20.0.2-win_x64.zip
  [21]=https://cdn.azul.com/zulu/bin/zulu21.52.203-ca-jdk21.0.12.1-win_x64.zip
  [22]=https://cdn.azul.com/zulu/bin/zulu22.32.15-ca-jdk22.0.2-win_x64.zip
  [23]=https://cdn.azul.com/zulu/bin/zulu23.32.11-ca-jdk23.0.2-win_x64.zip
  [24]=https://cdn.azul.com/zulu/bin/zulu24.32.13-ca-jdk24.0.2-win_x64.zip
  [25]=https://cdn.azul.com/zulu/bin/zulu25.36.205-ca-jdk25.0.4.1-win_x64.zip
  [26]=https://cdn.azul.com/zulu/bin/zulu26.32.203-ca-jdk26.0.2.1-win_x64.zip
  [27]=https://cdn.azul.com/zulu/bin/zulu27.28.101-ca-jdk27.0.0-win_x64.zip
)
java_versions="11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27"

set -e -o pipefail

mkdir -p results
all_results=()

for major_java_version in $java_versions; do
  jdk_dir="$jdks_dir/$major_java_version"
  if [ ! -x "$jdk_dir/bin/java.exe" ]; then
    mkdir -p "$jdks_dir"
    archive="$jdks_dir/zulu-$major_java_version.zip"
    echo "Downloading JDK $major_java_version from Azul Zulu CDN into $jdk_dir ..."
    curl -f --retry 5 --retry-delay 10 -o "$archive" "${zulu_urls[$major_java_version]}"
    # drain the full listing (not just head -1) so it isn't killed by SIGPIPE under pipefail
    extracted_name=$(unzip -Z1 "$archive" | { head -1; cat >/dev/null; } | cut -f1 -d/)
    rm -rf "${jdk_dir:?}" "$jdks_dir/$extracted_name"
    unzip -q "$archive" -d "$jdks_dir"
    mv "$jdks_dir/$extracted_name" "$jdk_dir"
    rm "$archive"
  fi
  java=$jdk_dir/bin/java.exe
  javac=$jdk_dir/bin/javac.exe
  echo "Running with $($java -version 2>&1 | head -1)"

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
  $javac -nowarn -Xlint:none -XDignore.symbol.file -d target $to_compile
  if echo "$version_tests" | grep -qw PatchJimage; then
    mkdir -p target/patch target/decapsulation
    $javac -nowarn -Xlint:none -g:none --patch-module java.base=src/java \
      -d target/patch src/java/lang/invoke/StringConcatException.java
    mv target/patch/java/lang/invoke/StringConcatException.class \
       target/decapsulation/StringConcatException_patch.class
  fi
  echo -ne '\033[0m' # reset color
  echo

  for test in $version_tests; do
    if [ "$test" = "WinMemHeap" ] || [ "$test" = "FfmHeap" ]; then
      variants="normal 64BitOOP"
    elif [ "$test" = "FfmHeapSimple" ]; then
      variants="64BitOOP"
    else
      variants="normal"
    fi
    for variant in $variants; do
      if [ "$variant" = "64BitOOP" ]; then
        opts="-XX:-UseCompressedOops"
        name_suffix="-win-64BitOOP"
      else
        opts=""
        name_suffix="-win"
      fi

      stderr=results/$test-$major_java_version$name_suffix
      out=$($java -cp 'target;lib/*' $opts decapsulation."$test" 2> "$stderr" | tr -d '\r' || true)
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

      echo "On $major_java_version$name_suffix $test: $result"
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