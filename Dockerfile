# 빌드 스테이지
FROM gradle:8.5-jdk21 AS builder
WORKDIR /app

# Gradle 설정 파일 복사
COPY build.gradle settings.gradle ./
COPY gradle gradle
COPY gradlew ./

# 소스 코드 복사
COPY src src

# 빌드 실행
RUN chmod +x gradlew && ./gradlew clean build -x test

# 실행 스테이지
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 빌드 스테이지에서 JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
