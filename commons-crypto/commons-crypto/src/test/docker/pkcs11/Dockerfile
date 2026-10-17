ARG BASE=maven:3.9-eclipse-temurin-17
FROM ${BASE}
RUN apt-get update && apt-get install -y --no-install-recommends softhsm2 opensc openssl gnupg libnss3-tools && rm -rf /var/lib/apt/lists/*
