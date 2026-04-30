#!/usr/bin/env bash
set -e

CONTAINER=hadoop-compiler
IMAGE=hadoop-compiler

echo "Building compiler image..."
docker build -t $IMAGE .

echo "Starting compiler container..."
docker run -d --rm \
  --name $CONTAINER \
  -v "$(pwd):/workspace" \
  -v "$(pwd)/hadoop-data:/hadoop-data" \
  -p 9871:9870 -p 8089:8088 \
  $IMAGE tail -f /dev/null

echo "Waiting for compiler..."
sleep 2

echo "Running build.sh from host..."
bash build.sh

echo "Stopping compiler container..."
docker stop $CONTAINER

echo "✅ Done"
