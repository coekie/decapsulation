Demonstration of various decapsulation techniques for the JDK.
See https://wouter.coekaerts.be/2026/decapsulation .
That blog post covers most (but not all) classes in here.

## Results
| Test | 11 | 12-15 | 16-20 | 21 | 22-23 | 24-27 | 28ea |
|---|---|---|---|---|---|---|---|
| AttachJavaAgent | ✔️ | ✔️ | ✔️ | ⚠️ | ⚠️ | ⚠️ | ⚠️ |
| AttachJvmtiAgent | ✔️ | ✔️ | ✔️ | ⚠️ | ⚠️ | ⚠️ | ⚠️ |
| CtorForSerialization | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ |
| FfmBytecode |  |  |  |  | ⚠️ | ⚠️ | ⚠️ |
| FfmHeap |  |  |  |  | ⚠️ | ⚠️ | ⚠️ |
| FfmHeapSimple |  |  |  |  | ⚠️ | ⚠️ | ⚠️ |
| FfmLibJvm |  |  |  |  | ⚠️ | ⚠️ | ⚠️ |
| FfmLibrary |  |  |  |  | ⚠️ | ⚠️ | ⚠️ |
| PatchJimage | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ |
| ProcMemBytecode | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ |
| ProcMemHeap | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ |
| SetAccessible | ⚠️ | ⚠️ | ❌ | ❌ | ❌ | ❌ | ❌ |
| SetAccessibleFiltered | ⚠️ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| UnsafeGet | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ⚠️ | ⚠️ |
| UnsafeNoWarning | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ |
| UnsafePut | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ⚠️ | ⚠️ |
| UseJNI | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ⚠️ | ⚠️ |
| UseNarcissus | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ⚠️ | ⚠️ |
| WinMemBytecode | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ |  |

## Architecture

Every test class extends `decapsulation.Decapsulater`, implementing `makeLookup()`.
Tests are ran as standalone programs through their main method.
Tests print their result on stdout, which can be PASS, FAIL (for expected types of failures), or ERROR (not expected).
The test script runs tests with a list of JDK versions, for tests that PASS also checks if warnings were printed on stderr.

## Prepare to run tests

* Install sdkman. JDKs that are available with sdkman will be installed that way, other SDKs are downloaded directly into a `jdks` directory.
* Run `bin/compile-native.sh`

## Run tests

## Running the tests

```bash
bin/run.sh                  # run all tests on all JDK versions
bin/run.sh 25               # run all tests on Java 25 only
bin/run.sh SetAccessible    # run one test on all JDK versions
bin/run.sh 25 SetAccessible # run one test on one JDK version
```

## Tests on Windows

Some platform-specific/sensitive tests can be run on Windows.
These tests assume that we're running in WSL.
This testing setup is more primitive.
* `bin/compile-native-windows.sh`
* `bin/run-windows.sh`