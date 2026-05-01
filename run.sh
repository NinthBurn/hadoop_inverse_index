DOCKER_NETWORK=hadoop_inverse_index_default
ENV_FILE=hadoop.env

docker build -t hadoop-wordcounter ./submit --platform=linux/amd64
docker run --rm \
  --network ${DOCKER_NETWORK} \
  --env-file ${ENV_FILE} \
  -v $(pwd):/results \
  hadoop-wordcounter
