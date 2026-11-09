#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
export JAVA_HOME="$PWD/.tools/jdk-21.0.12.1+1" GRADLE_USER_HOME="$PWD/.gradle-user"
# This is the execution environment's existing sanctioned proxy, not a bypass.
if [[ -n "${HTTPS_PROXY:-}" ]]; then
  proxy="${HTTPS_PROXY#*://}"; host="${proxy%:*}"; port="${proxy##*:}"
  export GRADLE_OPTS="-Dhttp.proxyHost=$host -Dhttp.proxyPort=$port -Dhttps.proxyHost=$host -Dhttps.proxyPort=$port"
fi
# The command sandbox and native cloud desktop have separate /tmp namespaces.
# Keep the verified distribution beside the toolchain in their shared workspace.
exec "$PWD/.tools/gradle-8.10.2/bin/gradle" "$@"
