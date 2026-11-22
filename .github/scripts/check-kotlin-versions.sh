#!/usr/bin/env bash
#
# Guards the AGP built-in Kotlin problem.
#
# AGP 9 compiles Kotlin itself and puts its own Kotlin Gradle Plugin (2.2.10 for
# AGP 9.1.0) on the buildscript classpath. katachi is built with the Kotlin
# version of the root catalog, so if a sample ever ends up compiling with the
# bundled KGP instead, its test sources fail with
#   "Module was compiled with an incompatible version of Kotlin".
# The samples avoid this by declaring the Kotlin plugin from the root catalog in
# their own root build.gradle.kts; this script checks, mechanically, that the
# workaround is still doing its job.
#
# It asks every sample build what Kotlin Gradle Plugin actually ends up on its
# buildscript classpath (`gradlew buildEnvironment`) and compares that with
# `kotlin` in gradle/libs.versions.toml.
#
# Usage: .github/scripts/check-kotlin-versions.sh [sample ...]   (default: all)

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

catalog="gradle/libs.versions.toml"
expected="$(sed -n 's/^[[:space:]]*kotlin[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$catalog" | head -n 1)"

if [ -z "$expected" ]; then
  echo "FAIL: no [versions] kotlin entry found in $catalog" >&2
  exit 1
fi
echo "root catalog ($catalog) declares kotlin = $expected"

samples=("$@")
if [ ${#samples[@]} -eq 0 ]; then
  samples=(jvm android kmp custom-processor)
fi

log_dir="$(mktemp -d)"
trap 'rm -rf "$log_dir"' EXIT

failed=0
for sample in "${samples[@]}"; do
  log="$log_dir/$sample-buildEnvironment.log"
  echo "--- sample/$sample: resolving the buildscript classpath"
  # The Gradle Plugin Portal sometimes answers "Could not find kotlin-stdlib:<version>" for an artifact that is on Maven
  # Central (KATACHI-401: all four samples failed in one run, the same commit passed in the next). Gradle remembers such a
  # miss for 24 hours (and the CI restores that cache), so a plain second run would fail the same way: a failure that looks
  # like a resolution problem is run again, up to two more times, with --refresh-dependencies. Any other failure is not
  # retried.
  attempt=1
  refresh=()
  while ! (cd "sample/$sample" && ./gradlew buildEnvironment --console=plain ${refresh[@]+"${refresh[@]}"}) > "$log" 2>&1; do
    if [ "$attempt" -lt 3 ] && grep -Eq 'Could not (find|resolve|GET|HEAD|parse POM)|Read timed out|Connection (reset|timed out)|Premature end of Content-Length' "$log"; then
      echo "  sample/$sample: resolving the buildscript classpath failed (attempt $attempt of 3), trying again with --refresh-dependencies" >&2
      grep -E 'Could not (find|resolve|GET|HEAD|parse POM)|Read timed out|Connection (reset|timed out)' "$log" | head -n 3 >&2
      attempt=$((attempt + 1))
      refresh=(--refresh-dependencies)
      sleep 5
      continue
    fi
    echo "FAIL: sample/$sample: 'gradlew buildEnvironment' failed" >&2
    cat "$log" >&2
    failed=1
    continue 2
  done

  # Lines look like either
  #   \--- org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10
  #   \--- org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10 -> 2.4.10
  # The effective version is the one after the arrow when there is one.
  # `kotlin-gradle-plugin-api` does not match, because the pattern ends in ':'.
  versions="$(
    awk '
      /org\.jetbrains\.kotlin:kotlin-gradle-plugin:/ {
        if (match($0, /-> [0-9][0-9A-Za-z.\-]*/)) {
          print substr($0, RSTART + 3, RLENGTH - 3)
        } else if (match($0, /kotlin-gradle-plugin:[0-9][0-9A-Za-z.\-]*/)) {
          print substr($0, RSTART + 21, RLENGTH - 21)
        }
      }
    ' "$log" | sort -u
  )"

  if [ -z "$versions" ]; then
    echo "FAIL: sample/$sample: no kotlin-gradle-plugin on any buildscript classpath" >&2
    failed=1
    continue
  fi

  for version in $versions; do
    if [ "$version" = "$expected" ]; then
      echo "  ok   sample/$sample uses kotlin-gradle-plugin $version"
    else
      echo "FAIL: sample/$sample uses kotlin-gradle-plugin $version, expected $expected" >&2
      failed=1
    fi
  done
done

if [ "$failed" -ne 0 ]; then
  echo
  echo "Kotlin versions disagree. See the WORKAROUND comment in sample/*/build.gradle.kts." >&2
  exit 1
fi

echo "all samples agree on kotlin $expected"
