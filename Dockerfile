FROM quay.io/quarkus/ubi-quarkus-graalvmce-builder-image:jdk-21 AS build

COPY --chown=quarkus:quarkus gradle gradle
COPY --chown=quarkus:quarkus gradlew .
COPY --chown=quarkus:quarkus gradle.properties .
COPY --chown=quarkus:quarkus settings.gradle .
COPY --chown=quarkus:quarkus build.gradle .

USER quarkus
RUN ./gradlew build -x test

COPY --chown=quarkus:quarkus src src
RUN ./gradlew build -Dquarkus.package.type=native -x test

FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /work/

RUN chown 1001 /work \
    && chmod "g+rwX" /work \
    && chown 1001:root /work

COPY --from=build --chown=1001:root /code/build/*-runner /work/application

EXPOSE 8080
USER 1001

ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]