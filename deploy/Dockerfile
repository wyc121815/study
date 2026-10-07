# 三个后端服务共用的镜像定义，构建时用 --build-arg MODULE 指定要打包哪个服务。
#
# 发布包在服务器上构建镜像时只做一次 COPY，不需要 Maven/JDK，
# 因为 jar 已经在 CI 里构建好了。
#
#   docker build -f Dockerfile --build-arg MODULE=auth-service -t conn-platform/auth-service .

# 国内服务器拉不动 Docker Hub 时，用 --build-arg BASE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21-jre
ARG BASE_IMAGE=eclipse-temurin:21-jre
FROM ${BASE_IMAGE}

ARG MODULE

ENV TZ=Asia/Shanghai \
    # 2C2G 机器上要给 Nacos / MySQL 留余量，堆内存统一压到 320m
    JAVA_OPTS="-Xms96m -Xmx320m -XX:+UseSerialGC -XX:MaxMetaspaceSize=160m"

WORKDIR /app

COPY backend/${MODULE}.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
