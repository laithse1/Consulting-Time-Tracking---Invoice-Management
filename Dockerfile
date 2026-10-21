FROM node:22-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build/backend
COPY backend/pom.xml ./
RUN mvn -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /build/frontend/dist ./src/main/resources/static
RUN mvn -B package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --gid 10001 consulting && useradd --uid 10001 --gid 10001 --create-home --shell /usr/sbin/nologin consulting && mkdir -p /app/data && chown -R consulting:consulting /app
WORKDIR /app
COPY --from=backend /build/backend/target/invoice-manager-1.0.0.jar ./app.jar
USER consulting
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
