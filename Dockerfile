# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 레이어 캐시
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# 보안 규칙 S8: 앱은 root 가 아닌 전용 사용자로 실행한다 (앱이 뚫려도 컨테이너 root 권한을 얻지 못하게)
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
COPY --from=build --chown=root:root /app/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
