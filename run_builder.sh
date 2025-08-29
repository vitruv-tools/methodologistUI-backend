#!/usr/bin/env bash
set -euo pipefail

# usage: ./run_builder.sh /path/to/model.ecore /path/to/model.genmodel [/tmp/mm-out]
ECORE="${1:?ecore missing}"
GENMODEL="${2:?genmodel missing}"
OUTDIR="${3:-/tmp/mm-out}"

JAR="/opt/methodologist/methodologist-build.jar"  # change if you put it elsewhere
IMAGE="eclipse-temurin:21-jre"

JOB="$(mktemp -d /tmp/mm-job-XXXXXX)"
mkdir -p "$JOB/input" "$JOB/output"

# copy inputs into the job folder (NOT Desktop)
cp -f "$ECORE"     "$JOB/input/model.ecore"
cp -f "$GENMODEL"  "$JOB/input/model.genmodel"
cp -f "$JAR"       "$JOB/methodologist-build.jar"

# run with classpath to avoid manifest issues
docker run --rm --read-only --network none --cpus 1 --memory 1g \
  --cap-drop ALL --security-opt no-new-privileges --pids-limit 256 \
  --tmpfs /tmp:rw,noexec,nosuid,size=256m \
  -v "$JOB":/work:rw "$IMAGE" \
  java -cp /work/methodologist-build.jar tools.vitruv.methodologist.builder.Main \
    --ecore /work/input/model.ecore \
    --genmodel /work/input/model.genmodel \
    --out /work/output \
    --run-mwe2 true

mkdir -p "$OUTDIR"
cp -R "$JOB/output/." "$OUTDIR/" || true
cat "$OUTDIR/result.json" || true

rm -rf "$JOB"