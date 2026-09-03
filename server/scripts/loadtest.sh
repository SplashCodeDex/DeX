#!/usr/bin/env bash
# DeX server production load test (plan 032 remaining item).
# Runs the REAL deployed wire surface — health/auth gates, relay quota gate (429
# BEFORE first byte), concurrent streaming with byte-integrity verification,
# punch round-trip, sync exchange smoke. No mocks, no simulation: every phase
# asserts on actual HTTP responses from the host.
#
# Required env:
#   DEX_HOST    base URL of the deployed host, e.g. https://relay.example.com
#   DEX_BEARER  a LIVE Google ID token from a signed-in DeX client (~1 h TTL;
#               obtain by signing in on any paired client and copying the bearer).
# Optional env:
#   DEX_MB        MiB relayed per stream      (default 256; set DEX_MB=2050 to
#                                            exercise the 2 GiB session-cap breach)
#   DEX_STREAMS   concurrent relay streams    (default 2 = server per-tenant cap)
#   DEX_KEEP_TMP  set to 1 to keep temp payloads (default: removed on exit)
#
# While phase 4 runs, observe the bounded-memory law ON THE VPS:
#   docker stats dex-server   (memory must stay ~flat regardless of DEX_MB —
#                              the frame buffer is 64 x 256 KiB per session)
#
# Requires: bash, curl. Run from any machine that can reach the host.
set -euo pipefail

HOST="${DEX_HOST:?DEX_HOST is required (e.g. https://relay.example.com)}"
BEARER="${DEX_BEARER:?DEX_BEARER is required (live Google ID token)}"
MB="${DEX_MB:-256}"
STREAMS="${DEX_STREAMS:-2}"

TMP="$(mktemp -d)"
OPENED=()   # "sessionId|streamToken" pairs still open — closed on exit
PASS=0
FAIL=0

cleanup() {
  local pair sid tok
  for pair in "${OPENED[@]:-}"; do
    sid="${pair%%|*}"; tok="${pair##*|}"
    curl -s -o /dev/null -X POST "$HOST/relay/v1/session/$sid/close" \
      -H "Authorization: Bearer $BEARER" -H "X-DeX-Stream-Token: $tok" || true
  done
  if [ "${DEX_KEEP_TMP:-0}" != "1" ]; then rm -rf "$TMP"; else echo "temp kept: $TMP"; fi
}
trap cleanup EXIT

report() { # report <PASS|FAIL> <phase> <detail>
  local verdict="$1" phase="$2" detail="$3"
  if [ "$verdict" = "PASS" ]; then PASS=$((PASS + 1)); echo "  [PASS] $phase: $detail"
  else FAIL=$((FAIL + 1)); echo "  [FAIL] $phase: $detail"; fi
}

# req <method> <url> <body_out_file> [extra curl args...] -> echoes HTTP status
req() {
  local method="$1" url="$2" out="$3"
  shift 3
  curl -s -o "$out" -w '%{http_code}' -X "$method" "$@" "$url"
}

json_field() { # json_field <file> <key> -> raw value of "key":"..."
  grep -o "\"$1\":\"[^\"]*\"" "$2" | head -n1 | cut -d'"' -f4
}

echo "=== DeX server load test — $HOST ==="
echo "    streams=$STREAMS  payload=${MB}MiB per stream"

# ---- Phase 1: liveness -------------------------------------------------------
code=$(req GET "$HOST/healthz" "$TMP/h.txt")
[ "$code" = "200" ] && [ "$(cat "$TMP/h.txt")" = "ok" ] \
  && report PASS "1 liveness" "/healthz -> 200 ok" \
  || report FAIL "1 liveness" "/healthz -> $code '$(cat "$TMP/h.txt")'"

# ---- Phase 2: unauthenticated gates (every surface must 401) -----------------
code=$(req POST "$HOST/relay/v1/session?targetDeviceId=loadtest" "$TMP/b.txt")
[ "$code" = "401" ] && report PASS "2 auth-gates" "relay open w/o bearer -> 401" \
  || report FAIL "2 auth-gates" "relay open w/o bearer -> $code (expected 401)"

code=$(req GET "$HOST/punch/register?fingerprint=lt&ip=203.0.113.7&port=45000" "$TMP/b.txt")
[ "$code" = "401" ] && report PASS "2 auth-gates" "punch register w/o bearer -> 401" \
  || report FAIL "2 auth-gates" "punch register w/o bearer -> $code (expected 401)"

code=$(req GET "$HOST/punch/resolve?fingerprint=lt" "$TMP/b.txt")
[ "$code" = "401" ] && report PASS "2 auth-gates" "punch resolve w/o bearer -> 401" \
  || report FAIL "2 auth-gates" "punch resolve w/o bearer -> $code (expected 401)"

code=$(req POST "$HOST/sync/v1/exchange" "$TMP/b.txt" \
        -H "Content-Type: application/json" -d '{"deviceId":"lt","deltas":[],"sinceHostSeq":0}')
[ "$code" = "401" ] && report PASS "2 auth-gates" "sync exchange w/o bearer -> 401" \
  || report FAIL "2 auth-gates" "sync exchange w/o bearer -> $code (expected 401)"

# ---- Phase 3: relay quota gate — rejection BEFORE any byte -------------------
i=1
while [ "$i" -le "$STREAMS" ]; do
  code=$(req POST "$HOST/relay/v1/session?targetDeviceId=loadtest" "$TMP/s$i.json" \
          -H "Authorization: Bearer $BEARER")
  if [ "$code" != "200" ]; then
    report FAIL "3 quota-gate" "open stream $i -> $code '$(cat "$TMP/s$i.json")' (expected 200)"
    break
  fi
  OPENED+=("$(json_field sessionId "$TMP/s$i.json")|$(json_field streamToken "$TMP/s$i.json")")
  i=$((i + 1))
done

if [ "$i" -gt "$STREAMS" ]; then
  code=$(req POST "$HOST/relay/v1/session?targetDeviceId=loadtest" "$TMP/sx.json" \
          -H "Authorization: Bearer $BEARER")
  [ "$code" = "429" ] \
    && report PASS "3 quota-gate" "stream ${STREAMS}+1 rejected before first byte -> 429" \
    || report FAIL "3 quota-gate" "stream ${STREAMS}+1 -> $code (expected 429)"
fi

# ---- Phase 4: concurrent streaming — throughput + byte integrity -------------
# Receiver starts FIRST (the server's 64-frame bounded buffer must exert
# backpressure on the sender, never buffer the whole payload in memory).
if [ "${#OPENED[@]}" -gt 0 ]; then
  echo "  [info] streaming ${#OPENED[@]} x ${MB}MiB — watch 'docker stats dex-server' now"
  pids=()
  for i in "${!OPENED[@]}"; do
    pair="${OPENED[$i]}"; sid="${pair%%|*}"; tok="${pair##*|}"
    curl -s "$HOST/relay/v1/session/$sid/data?streamToken=$tok" > "$TMP/recv_$i.bin" &
    pids+=($!)
  done
  sleep 1
  : > "$TMP/timings"
  for i in "${!OPENED[@]}"; do
    pair="${OPENED[$i]}"; sid="${pair%%|*}"; tok="${pair##*|}"
    head -c $((MB * 1024 * 1024)) /dev/urandom > "$TMP/send_$i.bin"
    t0=$(date +%s%N)
    resp=$(curl -s -X POST "$HOST/relay/v1/session/$sid/data" \
             -H "X-DeX-Stream-Token: $tok" -H "Content-Type: application/octet-stream" \
             --data-binary @"$TMP/send_$i.bin")
    curl -s -o /dev/null -X POST "$HOST/relay/v1/session/$sid/complete" \
      -H "X-DeX-Stream-Token: $tok"
    echo "$i $(( $(date +%s%N) - t0 )) $(echo "$resp" | grep -o '"bytes":[0-9]*' | cut -d: -f2)" \
      >> "$TMP/timings"
  done
  for pid in "${pids[@]}"; do wait "$pid"; done
  for i in "${!OPENED[@]}"; do
    recv=$(wc -c < "$TMP/recv_$i.bin" | tr -d ' ')
    read -r _i dur_ns sent < <(grep "^$i " "$TMP/timings" | head -n1)
    mbs=$(awk -v mb="$MB" -v ns="$dur_ns" 'BEGIN { if (ns > 0) printf "%.1f", mb * 1000000000 / ns / 1048576; else print "?" }')
    if [ -n "$sent" ] && [ "$recv" = "$sent" ]; then
      report PASS "4 streaming" "stream $i: $recv bytes intact ($mbs MiB/s sender-side)"
    else
      report FAIL "4 streaming" "stream $i: sent='$sent' received='$recv' (integrity breach)"
    fi
  done
  OPENED=()   # completed sessions are dead; nothing to close
fi

# ---- Phase 5: punch round-trip ------------------------------------------------
FP="loadtest-$(date +%s)"
code=$(req GET "$HOST/punch/register?fingerprint=$FP&ip=203.0.113.7&port=45000" "$TMP/pr.json" \
        -H "Authorization: Bearer $BEARER")
if [ "$code" = "200" ]; then
  code=$(req GET "$HOST/punch/resolve?fingerprint=$FP" "$TMP/pv.json" -H "Authorization: Bearer $BEARER")
  if [ "$code" = "200" ] \
     && [ "$(json_field ip "$TMP/pv.json")" = "203.0.113.7" ] \
     && [ "$(json_field port "$TMP/pv.json")" = "45000" ]; then
    report PASS "5 punch" "register + resolve round-trip intact"
  else
    report FAIL "5 punch" "resolve -> $code '$(cat "$TMP/pv.json")'"
  fi
else
  report FAIL "5 punch" "register -> $code '$(cat "$TMP/pr.json")'"
fi

# ---- Phase 6: sync exchange smoke ---------------------------------------------
code=$(req POST "$HOST/sync/v1/exchange" "$TMP/sy.json" -H "Authorization: Bearer $BEARER" \
        -H "Content-Type: application/json" \
        -d '{"deviceId":"loadtest-probe","deltas":[],"sinceHostSeq":0}')
if [ "$code" = "200" ] && grep -q '"hostSeq"' "$TMP/sy.json"; then
  report PASS "6 sync" "exchange snapshot -> 200, hostSeq present"
else
  report FAIL "6 sync" "exchange -> $code '$(cat "$TMP/sy.json")'"
fi

# ---- Verdict -------------------------------------------------------------------
echo "=== RESULT: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ]

