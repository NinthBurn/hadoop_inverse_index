FROM debian:11

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      ca-certificates-java \
      openjdk-11-jdk \
      net-tools \
      curl \
      netcat-openbsd \
      gnupg \
      libsnappy-dev \
      libssl-dev \
    && rm -rf /var/lib/apt/lists/*
      
RUN curl -O https://dist.apache.org/repos/dist/release/hadoop/common/KEYS

RUN gpg --import KEYS

ENV HADOOP_VERSION=3.4.1
RUN curl -fSL https://dlcdn.apache.org/hadoop/common/hadoop-3.4.1/hadoop-3.4.1.tar.gz \
    -o /tmp/hadoop.tar.gz \
 && tar -xvf /tmp/hadoop.tar.gz -C /opt/ \
 && rm /tmp/hadoop.tar.gz

RUN ln -s /opt/hadoop-$HADOOP_VERSION/etc/hadoop /etc/hadoop
RUN ln -s /opt/hadoop-$HADOOP_VERSION /opt/hadoop

RUN mkdir /opt/hadoop-$HADOOP_VERSION/logs

RUN mkdir /hadoop-data

ENV JAVA_HOME=/usr/lib/jvm/default-java
# create the symlink "/usr/lib/jvm/default-java" in case
# it is not already there (cf. package "default-jre-headless")
RUN if ! test -d $JAVA_HOME; then \
      ln -sf $(readlink -f $(dirname $(readlink -f $(which java)))/..) $JAVA_HOME; \
    fi

ENV HADOOP_HOME=/opt/hadoop-$HADOOP_VERSION
ENV HADOOP_CONF_DIR=/etc/hadoop
ENV MULTIHOMED_NETWORK=1
ENV USER=root
ENV PATH=$HADOOP_HOME/bin/:$PATH

ADD entrypoint.sh /entrypoint.sh

RUN chmod a+x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
