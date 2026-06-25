#!/usr/bin/env bash
#
# Health check for the hosted Hacker News search API contract.
#
# Search and the search date-range filter depend on the hosted search API at hn.algolia.com. This is
# a small, low-frequency contract check that gives early warning if that behavior changes, so a
# silent break is caught before users hit it. It is NOT part of the normal build: run it manually
# (the workflow's workflow_dispatch), on the documented weekly schedule, or locally.
#
# It calls the two endpoints the app uses, with the app's parameters, and asserts the contract:
#   - HTTP 200
#   - a valid JSON body
#   - a non-empty "hits" array
#   - every hit has an "objectID"
#   - the created_at_i> date filter (numericFilters) is accepted
#
# Usage:
#   .github/scripts/algolia-health-check.sh
#
# Negative test (must fail loudly, non-zero): point it at a base URL that does not serve the
# contract, e.g. a wrong path on the host or an unreachable host:
#   ALGOLIA_BASE_URL=https://hn.algolia.com/api/v1/does-not-exist .github/scripts/algolia-health-check.sh
#
# Note: GitHub scheduled and manual (workflow_dispatch) runs require this workflow file to exist on
# the default branch. While this change lives only on the development branch, run the script directly
# from a branch checkout. Once the workflow reaches the default branch at a release gate, manual runs
# can use the branch/ref selector.
#
# Configuration (environment variables):
#   ALGOLIA_BASE_URL   base URL, default https://hn.algolia.com/api/v1
#   QUERY              search term, default "android"
#
# Exit code is 0 only if every assertion passes; otherwise it is non-zero and the failing assertion
# is named on stderr. Requires curl and python3 (both present on the CI runner and on most dev
# machines).
set -euo pipefail

BASE_URL="${ALGOLIA_BASE_URL:-https://hn.algolia.com/api/v1}"
QUERY="${QUERY:-android}"
# The app's parameters for these endpoints. hitsPerPage=1 keeps the check light (one hit is enough
# to assert the contract).
COMMON="tags=story&hitsPerPage=1&attributesToRetrieve=objectID&attributesToHighlight=none"
# A fixed lower bound, well in the past, so the date filter is exercised deterministically. The ">"
# is URL-encoded as %3E.
DATE_FILTER="numericFilters=created_at_i%3E1700000000"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

check() {
  local label="$1" url="$2" resp http body
  echo "checking ${label}: ${url}"
  if ! resp="$(curl -sS -m 20 -w '\n%{http_code}' "$url")"; then
    fail "${label}: request errored (network, DNS, or timeout)"
  fi
  http="$(printf '%s' "$resp" | tail -n1)"
  body="$(printf '%s' "$resp" | sed '$d')"
  [ "$http" = "200" ] || fail "${label}: expected HTTP 200, got ${http}"
  # The body is piped to python on stdin; the validator program is passed with -c (so stdin stays the
  # response body), and the assertion label is passed via the environment.
  printf '%s' "$body" | BODY_LABEL="$label" python3 -c '
import json, os, sys

label = os.environ["BODY_LABEL"]
raw = sys.stdin.read()
try:
    data = json.loads(raw)
except Exception as exc:
    sys.exit(f"FAIL: {label}: response was not valid JSON ({exc})")
hits = data.get("hits")
if not isinstance(hits, list) or not hits:
    sys.exit(f"FAIL: {label}: expected a non-empty hits array")
for hit in hits:
    if not hit.get("objectID"):
        sys.exit(f"FAIL: {label}: a hit is missing objectID")
print(f"ok: {label}: HTTP 200, {len(hits)} hit(s), objectID present")
'
}

# 1) search_by_date with the app's parameters.
check "search_by_date" "${BASE_URL}/search_by_date?query=${QUERY}&${COMMON}"
# 2) search with the same parameters plus the created_at_i> date filter (the date-range feature).
check "search (date filter accepted)" "${BASE_URL}/search?query=${QUERY}&${COMMON}&${DATE_FILTER}"

echo "PASS: Hacker News search API contract healthy at ${BASE_URL}"
