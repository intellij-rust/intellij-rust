FROM ubuntu:16.04
MAINTAINER Arseniy Pendryak <arseniy.pendryak@jtebrains.com>

RUN apt-get update
RUN apt-get -y install curl

# Install Open JDK 8
RUN apt-get -y install openjdk-8-jdk

ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64
ENV PATH $JAVA_HOME/bin:$PATH

RUN java -version

ENV USER root
ENV HOME /root

# Intall rustup
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
ENV PATH $HOME/.cargo/bin:$PATH
RUN echo $PATH

RUN rustup component add rust-src
RUN rustup component add rustfmt-preview
RUN rustup component add clippy-preview
