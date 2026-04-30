FROM hadoop-base:latest

WORKDIR /opt/job

COPY WordCounter.jar .
COPY run.sh .

RUN chmod +x run.sh

CMD ["/opt/job/run.sh"]