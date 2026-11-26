#!/usr/bin/env bash
#
# dry_run.sh — complete local dry run of the auto-marker's steps (spec Appendix C.1).
#
# Runs, from your project root, the exact sequence the marker performs:
#   1. build the application and the image        (mvn, docker build)
#   2. export the image as the submission tar     (docker save)
#   3. simulate a clean daemon                    (docker rmi + docker load)
#   4. stand up the external environment          (LocalStack + aws-init + tariff service)
#   5. start your container on the Compose network with a DYNAMIC host port
#   6. poll GET /actuator/health until UP
#   7. drive marked request P3 (POST /dispatch/manifests) and check the response
#   8. query the indexed shipment (GET /carriers/{carrierId}/shipments) and check it
#   9. tear everything down again
#
# Usage:
#   ./dry_run.sh [options]
#
# Options:
#   --timeout SEC     how long to wait for the service to become healthy (default 90)
#   --keep            leave the container and environment running afterwards
#
# Requires: docker (with Compose v2), mvn (or the ./mvnw wrapper), curl.
# Runs on macOS/Linux; on Windows use Git Bash or WSL.

set -u

IMAGE="ilp:cw2"
TAR="ilp_submission_image.tar"
CONTAINER="ilp-marker-test"
TIMEOUT=90
KEEP=0

RED=""; GREEN=""; YELLOW=""; BOLD=""; RESET=""
if [ -t 1 ]; then
    RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; BOLD=$'\033[1m'; RESET=$'\033[0m'
fi

ok()   { echo "${GREEN}  OK${RESET}    $*"; }
warn() { echo "${YELLOW}  WARN${RESET}  $*"; WARNINGS=$((WARNINGS + 1)); }
fail() { echo "${RED}  FAIL${RESET}  $*"; }
step() { echo; echo "${BOLD}[$1/9]${RESET} $2"; }

usage() { sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }

while [ $# -gt 0 ]; do
    case "$1" in
        --timeout) TIMEOUT="$2"; shift 2 ;;
        --keep)    KEEP=1; shift ;;
        -h|--help) usage ;;
        *)         echo "unknown option: $1"; usage ;;
    esac
done

WARNINGS=0

cleanup() {
    if [ "$KEEP" -eq 0 ]; then
        docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
        docker compose down >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

if [ ! -f pom.xml ] || [ ! -f Dockerfile ] || [ ! -f docker-compose.yml ]; then
    fail "run this from your project root (pom.xml, Dockerfile and docker-compose.yml)"
    exit 1
fi
if ! docker version >/dev/null 2>&1; then
    fail "docker is not running / not installed"
    exit 1
fi
MVN=mvn
[ -x ./mvnw ] && MVN=./mvnw

# request LABEL EXPECTED_CODE MUST_CONTAIN.. -- CURL_ARGS..
# status != expected -> FAIL; expected status but a MUST_CONTAIN fragment
# missing from the whitespace-stripped body -> WARN.
request() {
    LABEL="$1"; EXPECT="$2"; shift 2
    MUST=()
    while [ "$1" != "--" ]; do MUST+=("$1"); shift; done
    shift
    RESPONSE=$(curl -s -w '\n%{http_code}' "$@" 2>/dev/null)
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    BODY=$(echo "$RESPONSE" | sed '$d')
    if [ "$HTTP_CODE" != "$EXPECT" ]; then
        fail "$LABEL answered ${HTTP_CODE:-nothing} (expected $EXPECT) with: ${BODY:-<empty>}"
        return 1
    fi
    FLAT=$(echo "$BODY" | tr -d '[:space:]')
    MISSING=0
    for M in "${MUST[@]+"${MUST[@]}"}"; do
        case "$FLAT" in
            *"$M"*) ;;
            *) warn "$LABEL: $EXPECT but the response is missing $M"; MISSING=1 ;;
        esac
    done
    if [ "$MISSING" -eq 0 ]; then
        ok "$LABEL: $EXPECT — response matches the spec sample"
    else
        echo "        got: $BODY"
    fi
}

# ------------------------------------------------- 1: build app and image
step 1 "$MVN clean package && docker build"
if ! "$MVN" clean package; then
    fail "the Maven build failed"
    exit 1
fi
if ! docker build -t "$IMAGE" .; then
    fail "docker build failed"
    exit 1
fi
ok "image $IMAGE built"

# ------------------------------------------------- 2: export the image
step 2 "docker save (the tar that goes into your submission zip)"
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
if ! docker save "$IMAGE" -o "$TAR"; then
    fail "docker save failed"
    exit 1
fi
ok "$TAR written ($(du -h "$TAR" | cut -f1))"

# ------------------------------------------------- 3: clean-daemon simulation
step 3 "docker rmi + docker load (the marker only ever sees the tar)"
docker rmi "$IMAGE" >/dev/null
if ! docker load -i "$TAR" >/dev/null; then
    fail "the image could not be loaded back from $TAR"
    exit 1
fi
ok "image restored from the tar alone"

# --------------------------------------- 4: external environment (compose)
step 4 "docker compose up localstack + aws-init + tariff-service (marking mode)"
if ! TARIFF_SERVICE_MODE=marking docker compose up -d localstack aws-init tariff-service; then
    fail "the compose environment could not be started"
    exit 1
fi
if ! docker compose wait aws-init >/dev/null 2>&1; then
    fail "aws-init did not complete — bucket/table were not created"
    exit 1
fi
ok "LocalStack up, bucket + table created, tariff service running"

# --------------------------------- 5: run on the compose net, dynamic port
step 5 "docker run on the Compose network (marker-style env vars)"
NET=$(docker inspect -f \
  '{{ range $k, $v := .NetworkSettings.Networks }}{{ $k }}{{ end }}' \
  "$(docker compose ps -q localstack)")
if ! docker run -d --name "$CONTAINER" --network "$NET" \
        -e SERVER_PORT=8080 \
        -e SPRING_PROFILES_ACTIVE=marker \
        -e TARIFF_SERVICE_URL=http://tariff-service:8082 \
        -e AWS_ENDPOINT=http://localstack:4566 \
        -e AWS_REGION=eu-central-1 \
        -e AWS_ACCESS_KEY_ID=test \
        -e AWS_SECRET_ACCESS_KEY=test \
        -e MANIFEST_ARCHIVE_BUCKET=campusshop-dispatch-manifests \
        -e SHIPMENT_TABLE=campusshop-shipments \
        -p 0:8080 "$IMAGE" >/dev/null; then
    fail "the container could not be started"
    exit 1
fi
HOST_PORT=$(docker port "$CONTAINER" 8080 | head -n 1 | cut -d: -f2)
if [ -z "$HOST_PORT" ]; then
    fail "Docker did not publish container port 8080"
    exit 1
fi
BASE="http://localhost:$HOST_PORT"
ok "container $CONTAINER running, service under test: $BASE"

# ------------------------------------------------- 6: readiness
step 6 "waiting for GET /actuator/health = UP (timeout ${TIMEOUT}s)"
DEADLINE=$(( $(date +%s) + TIMEOUT ))
UP=0
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
    if curl -s -m 2 "$BASE/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
        UP=1
        break
    fi
    sleep 1
done
if [ "$UP" -ne 1 ]; then
    fail "service not UP within ${TIMEOUT}s — last container log lines:"
    docker logs --tail 25 "$CONTAINER" 2>&1 | sed 's/^/        /'
    exit 1
fi
ok "service is UP"

# ------------------------------------------------- 7: marked POST (P3)
step 7 "POST /dispatch/manifests (marked request P3)"
request "P3" 201 \
    '"state":"REGISTERED"' \
    '"rating":"RATED"' \
    '"parcelCount":2' \
    '"totalCharge":' \
    '"manifestNo":"4b1d2c3e-5f60-4a71-8b92-a3b4c5d6e7f8"' \
    -- -X POST "$BASE/dispatch/manifests" \
    -H "Content-Type: application/json" \
    -d '{
      "manifestNo": "4b1d2c3e-5f60-4a71-8b92-a3b4c5d6e7f8",
      "carrier": { "carrierId": "9c8b7a65-4d3e-4f21-9a0b-1c2d3e4f5a6b",
                   "name": "Campus Couriers", "contactEmail": "ops@campus-couriers.example" },
      "dispatchDate": "2026-11-20",
      "originWarehouse": "EDI-01",
      "parcels": [ { "tariffCode": "TRF-1", "weightKg": 2.5 },
                   { "tariffCode": "TRF-2", "weightKg": 0.8 } ],
      "carrierInternalNotes": "must be preserved in the archived manifest"
    }'

# ------------------------------------------------- 8: metadata queryable
step 8 "GET /carriers/{carrierId}/shipments (the shipment is indexed, P7)"
request "P7" 200 \
    '"manifestNo":"4b1d2c3e-5f60-4a71-8b92-a3b4c5d6e7f8"' \
    -- "$BASE/carriers/9c8b7a65-4d3e-4f21-9a0b-1c2d3e4f5a6b/shipments"

# ------------------------------------------------- 9: teardown / summary
step 9 "teardown"
echo
if [ "$KEEP" -eq 1 ]; then
    echo "kept running: $BASE and the compose environment"
    echo "stop with: docker rm -f $CONTAINER && docker compose down"
else
    docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
    docker compose down >/dev/null 2>&1 || true
    ok "container and environment removed (the marker discards them too)"
fi
if [ "$WARNINGS" -gt 0 ]; then
    echo "${YELLOW}${BOLD}RESULT: PASSED WITH ${WARNINGS} WARNING(S)${RESET} — read the WARN lines above before submitting."
else
    echo "${GREEN}${BOLD}RESULT: PASSED${RESET} — $TAR reproduces the marker's run end to end; finish with the checklist in Appendix H."
fi
exit 0
