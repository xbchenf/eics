# EICS SpringBoot 后端 Dockerfile
FROM openjdk:17-slim

WORKDIR /app

# 安装 curl 用于健康检查
RUN apt-get update && apt-get install -y curl --no-install-recommends && \
    rm -rf /var/lib/apt/lists/*

COPY target/*.jar app.jar

# JVM 参数（可在 docker-compose 中覆盖）
ENV JAVA_OPTS="-Xms256m -Xmx512m"
# Spring 配置（docker-compose 注入环境变量覆盖 localhost）
ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=5 \
    CMD curl -f http://localhost:8080/api/v1/health || exit 1

ENTRYPOINT exec java ${JAVA_OPTS} -jar app.jar
