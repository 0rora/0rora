FROM openjdk:11-jre-slim

ENV PLAY_SECRET=changeme
ENV LUXE_ACCOUNT_SEED=changeme

COPY  target/universal/luxe-1.0-SNAPSHOT.zip /tmp
RUN mkdir /app && \
    (cd /tmp && unzip -d /tmp *.zip) && \
    (cd /tmp/luxe-1.0-SNAPSHOT/ && mv * /app) && \
    rm -r /app/share
EXPOSE 9000
CMD ["sh", "-c", "/app/bin/luxe -Dplay.http.secret.key=${PLAY_SECRET} -Dluxe.account.secret=${LUXE_ACCOUNT_SEED}"]
