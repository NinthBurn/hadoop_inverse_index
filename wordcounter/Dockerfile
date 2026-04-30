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

# Import the Apache GPG KEYS for Hadoop
RUN curl -fsSL https://dist.apache.org/repos/dist/release/hadoop/common/KEYS -o /tmp/KEYS \
 && gpg --import /tmp/KEYS \
 && rm /tmp/KEYS

ENV HADOOP_VERSION=3.4.1
RUN curl -fSL https://dlcdn.apache.org/hadoop/common/hadoop-3.4.1/hadoop-3.4.1.tar.gz \
    -o /tmp/hadoop.tar.gz \
 && tar -xvf /tmp/hadoop.tar.gz -C /opt/ \
 && rm /tmp/hadoop.tar.gz

# Symlink to standard locations
RUN ln -s /opt/hadoop-$HADOOP_VERSION/etc/hadoop /etc/hadoop \
 && ln -s /opt/hadoop-$HADOOP_VERSION        /opt/hadoop \
 && mkdir -p /opt/hadoop-$HADOOP_VERSION/logs \
 && mkdir /hadoop-data

# Ensure JAVA_HOME is set
ENV JAVA_HOME=/usr/lib/jvm/default-java
RUN if [ ! -d "$JAVA_HOME" ]; then \
      ln -sf "$(readlink -f "$(dirname "$(readlink -f "$(which java)")")/..")" "$JAVA_HOME"; \
    fi

ENV HADOOP_HOME=/opt/hadoop-$HADOOP_VERSION
ENV HADOOP_CONF_DIR=/etc/hadoop
ENV MULTIHOMED_NETWORK=1
ENV USER=root
ENV PATH=$HADOOP_HOME/bin/:$PATH
