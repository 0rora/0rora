FROM openjdk:11-jre-slim

COPY  target/universal/luxe-1.0-SNAPSHOT.zip /tmp
RUN mkdir /app && \
    (cd /tmp && unzip -d /tmp *.zip) && \
    (cd /tmp/luxe-1.0-SNAPSHOT/ && mv * /app) && \
    rm -r /app/share
EXPOSE 9000 9443
CMD /app/bin/luxe -Dhttps.port=9443 -Dplay.http.secret.key=secret
