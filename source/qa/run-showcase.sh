#!/bin/bash
# Usage: qa/run-showcase.sh <log-name> '<props ; separated>' '<script | separated>'
# Runs one hidden-window showcase client session (serial; never run two at once).
set -u
cd "$(dirname "$0")/.." || exit 1
export JAVA_HOME=${ACA_QA_JAVA_HOME:-${JAVA_HOME:-$HOME/.local/jdks/jdk-21.0.12.1+1/Contents/Home}}
if ! grep -Eq '^JAVA_VERSION="21([."]|$)' "$JAVA_HOME/release" 2>/dev/null; then
  if [ -n "${ACA_QA_JAVA_HOME:-}" ]; then
    echo "ACA_QA_JAVA_HOME must point to Java 21" >&2
    exit 2
  fi
  export JAVA_HOME=$HOME/.local/jdks/jdk-21.0.12.1+1/Contents/Home
fi
if ! grep -Eq '^JAVA_VERSION="21([."]|$)' "$JAVA_HOME/release" 2>/dev/null; then
  echo "Java 21 is required; set ACA_QA_JAVA_HOME" >&2
  exit 2
fi
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=${GRADLE_USER_HOME:-$HOME/.gradle-aca}
# Keep project logs inside this project after the external-drive migration.
log_dir=${ACA_SHOW_LOG_DIR:-../tmp}
mkdir -p "$log_dir"
show_log="$log_dir/aca-show-$1.log"
# ExFAT metadata companions are not class/resources or mods.
find src build -name '._*' -type f -delete
if [ -d "${ACA_SHOW_DIR:-run-showcase}" ]; then find "${ACA_SHOW_DIR:-run-showcase}" -name '._*' -type f -delete; fi
# Native GLFW requires an awake display. Assertions end with this owned JVM.
launcher=("$JAVA_HOME/bin/java")
if [ -x /usr/bin/caffeinate ]; then launcher=(/usr/bin/caffeinate -d -u "${launcher[@]}"); fi
"${launcher[@]}" -jar gradle/wrapper/gradle-wrapper.jar --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx768m \
  -PshowcaseQA=true -PshowHidden=true ${ACA_SHOW_DIR:+"-PshowDirectory=$ACA_SHOW_DIR"} "-PshowProps=$2" "-PshowScript=$3" runShowcaseClient > "$show_log" 2>&1
result=$?
echo "$1 exit=$result"
grep -E "QA_SHOT_SAVED|QA_OPEN_FAILED|Exception" "$show_log" | cut -c1-160
if ! grep -q 'QA_DONE' "$show_log"; then
  echo "QA script did not finish"
  exit 1
fi
exit "$result"
