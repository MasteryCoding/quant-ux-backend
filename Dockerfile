FROM maven:3-openjdk-18 AS builder

WORKDIR /app

COPY ["pom.xml", "./"]
COPY ["src/", "./src"]
RUN mvn -B package --file pom.xml

FROM eclipse-temurin:18-jdk AS runner

WORKDIR /app

COPY ["matc.conf", "./"]
COPY --from=builder ["/app/release/qux-server.jar", "./"]

CMD [ "java", "-jar",  "qux-server.jar", "-Xmx2g", "-conf", "matc.conf", "-instances 1" ]
