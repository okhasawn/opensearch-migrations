# syntax=docker/dockerfile:1

#FROM openjdk:17-jdk-slim AS builder

#RUN apt-get update && apt-get install -y git

# Be wary that this layer can be cached and not capture recent commits. Run docker build --no-cache to avoid this
#RUN git clone https://github.com/opensearch-project/opensearch-migrations.git

#WORKDIR /opensearch-migrations/KafkaPrinter
#RUN ./gradlew build


FROM openjdk:17-jdk-slim

ARG KAFKA_BOOTSTRAP_SERVER
ENV KAFKA_BOOTSTRAP_SERVER ${KAFKA_BOOTSTRAP_SERVER}

ARG KAFKA_TOPIC_NAME
ENV KAFKA_TOPIC_NAME ${KAFKA_TOPIC_NAME}
RUN apt-get update && apt-get install -y git
RUN apt-get update && apt-get install -y netcat
RUN apt-get install -y expect

COPY . /KafkaPrinter/
WORKDIR /KafkaPrinter/

CMD ./KafkaPrinter/gradlew build
#COPY ./run_app.sh .

CMD ./run_app.sh ${KAFKA_BOOTSTRAP_SERVER} ${KAFKA_TOPIC_NAME}

