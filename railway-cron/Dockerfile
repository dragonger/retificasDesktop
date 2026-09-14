# Liga/desliga o retifica-backend seguindo o horario de funcionamento da
# oficina. Baseado no template github.com/smolpaw/railway-service-cron
# (MIT) - adaptado pra ter horarios diferentes por dia da semana (o
# template original so aceita um horario de abrir/fechar pra semana toda).
FROM alpine:3

RUN apk add --no-cache \
    bash \
    curl \
    jq \
    tzdata

ENV SUPERCRONIC_URL=https://github.com/aptible/supercronic/releases/download/v0.2.34/supercronic-linux-amd64 \
    SUPERCRONIC=supercronic-linux-amd64 \
    SUPERCRONIC_SHA1SUM=e8631edc1775000d119b70fd40339a7238eece14
RUN curl -fsSLO "$SUPERCRONIC_URL" \
    && echo "${SUPERCRONIC_SHA1SUM}  ${SUPERCRONIC}" | sha1sum -c - \
    && chmod +x "$SUPERCRONIC" \
    && mv "$SUPERCRONIC" "/usr/local/bin/${SUPERCRONIC}" \
    && ln -s "/usr/local/bin/${SUPERCRONIC}" /usr/local/bin/supercronic

COPY railway.sh /usr/local/bin/railway.sh
COPY startup.sh /startup.sh
RUN chmod +x /usr/local/bin/railway.sh /startup.sh

# Horario de funcionamento da oficina (America/Sao_Paulo, ver var TZ):
#   seg-sex: liga 6h, desliga 20h
#   sabado:  liga 6h, desliga 14h
#   domingo: fechado (nada liga; continua desligado desde sabado 14h)
RUN mkdir -p /app && { \
      echo "0 6 * * 1-6 /usr/local/bin/railway.sh start"; \
      echo "0 20 * * 1-5 /usr/local/bin/railway.sh stop"; \
      echo "0 14 * * 6 /usr/local/bin/railway.sh stop"; \
    } > /app/crontab

CMD ["/startup.sh"]
