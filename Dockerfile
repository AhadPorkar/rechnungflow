# Deployment template, not locally executed evidence. Pin reviewed image digests for releases.
FROM eclipse-temurin:21-jdk-jammy AS build
RUN apt-get update && apt-get install -y --no-install-recommends python3 && rm -rf /var/lib/apt/lists/*
WORKDIR /source
COPY . .
RUN python3 scripts/build.py

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --gid 10001 rechnungflow && useradd --uid 10001 --gid 10001 --no-create-home rechnungflow && mkdir /data /app && chown 10001:10001 /data /app
COPY --from=build /source/build/rechnungflow-0.1.0.jar /app/rechnungflow.jar
WORKDIR /app
USER 10001:10001
ENV RF_DATA=/data RF_HOST=0.0.0.0 RF_PORT=8090
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/rechnungflow.jar"]
