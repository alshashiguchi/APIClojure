FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/project-catalog-0.0.1-SNAPSHOT-standalone.jar /project-catalog/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/project-catalog/app.jar"]
