#!/usr/bin/env bash
set -euo pipefail

NETWORK="hadoop_default"
DATA_DIR="$(pwd)"

echo "=== Building job image ==="
docker build -t hadoop-job-runner .

echo "=== Waiting for HDFS ==="
until docker exec namenode hdfs dfsadmin -report >/dev/null 2>&1; do
  sleep 5
done

docker exec namenode hdfs dfsadmin -safemode leave || true

echo "=== Preparing HDFS ==="
docker exec namenode hdfs dfs -rm -r -f /books || true
docker exec namenode hdfs dfs -rm -r -f /output || true
docker exec namenode hdfs dfs -mkdir -p /books

echo "=== Uploading data to HDFS ==="

for file in "$DATA_DIR"/*.txt; do
  fname=$(basename "$file")
  docker cp "$file" namenode:/tmp/"$fname"
  docker exec namenode hdfs dfs -put -f /tmp/"$fname" /books/
done

echo "=== Running job ==="

docker run --rm \
  --network "$NETWORK" \
  --env-file ../hadoop.env \
  hadoop-job-runner

echo "=== Output ==="
docker exec namenode hdfs dfs -cat /output/* || true