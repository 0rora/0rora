FROM openjdk:11-jre-slim

ENV APPLICATION_SECRET=changeme
ENV ACCOUNT_SEED=changeme

COPY  target/universal/0rora-1.0-SNAPSHOT.zip /tmp
RUN mkdir /app && \
    (cd /tmp && unzip -d /tmp *.zip) && \
    (cd /tmp/0rora-1.0-SNAPSHOT/ && mv * /app) && \
    rm -r /app/share
EXPOSE 9000
CMD ["sh", "-c", "/app/bin/0rora -J-Xmx512m -DAPPLICATION_SECRET=${APPLICATION_SECRET} -DACCOUNT_SEED=${ACCOUNT_SEED} -Dconfig.resource=in-memory-db.conf"]
