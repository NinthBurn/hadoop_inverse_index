#!/usr/bin/env bash
set -euo pipefail

echo "Running MapReduce job..."

hadoop jar WordCounter.jar WordCounter /books /output

echo "Job finished"