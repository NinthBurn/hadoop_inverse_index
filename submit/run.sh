#!/bin/bash
set -e

echo "==== Formatting HDFS (only for fresh runs) ===="
hdfs namenode -format || true

echo "==== Preparing HDFS directories ===="
hdfs dfs -rm -r -f /output || true
hdfs dfs -rm -r -f /books || true
hdfs dfs -mkdir -p /books

echo "==== Uploading input files ===="
for i in {1..10}; do
  hdfs dfs -copyFromLocal -f /opt/hadoop/book${i}.txt /books
done

echo "==== Verifying upload ===="
hdfs dfs -ls /books

echo "==== Running MapReduce job ===="
$HADOOP_HOME/bin/hadoop jar $JAR_FILEPATH $CLASS_TO_RUN $PARAMS

echo "==== Fetching results ===="
hdfs dfs -cat /output/* > /results/result.txt

echo "==== Cleaning up HDFS ===="
hdfs dfs -rm -r /output
hdfs dfs -rm -r /books

echo "==== Done ===="
