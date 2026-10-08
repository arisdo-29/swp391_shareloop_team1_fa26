# Giai đoạn 1: build file jar bằng Maven Wrapper
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Tải dependency trước, để lần build sau không tải lại nếu pom.xml không đổi
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -DskipTests -Dspotless.check.skip=true package

# Giai đoạn 2: chỉ chép jar sang image JRE nhẹ hơn
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Gói free của Render có 512 MB RAM: cho JVM dùng tối đa 75% bộ nhớ của container
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
