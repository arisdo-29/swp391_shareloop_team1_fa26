# Giai đoạn 1: build file jar bằng Maven Wrapper
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Tải dependency trước, để lần build sau không tải lại nếu pom.xml không đổi
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -DskipTests -Dspotless.check.skip=true package

# Giai đoạn 2: chỉ chép jar sang image JRE alpine nhẹ nhất
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar

# Cờ JVM (heap, GC...) đặt bằng biến môi trường JAVA_TOOL_OPTIONS trên Render, không viết cứng ở đây
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
