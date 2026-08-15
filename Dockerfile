# 빌드 스테이지
FROM gradle:8.5-jdk21 AS builder
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle gradle
COPY gradlew ./
COPY src src
RUN chmod +x gradlew && ./gradlew clean build -x test

# 실행 스테이지
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080

# 베이스 이미지 기본 타임존(UTC)이면 @Scheduled(cron=...)가 대부분 zone 미지정이라
# "매일 자정" 같은 크론이 한국시간 기준 09:00에 도는 등 전부 9시간 밀려서 실행됐음.
ENV TZ=Asia/Seoul
ENV JAVA_OPTS="-Duser.timezone=Asia/Seoul -Xmx320m -Xss512k -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxDirectMemorySize=32m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]