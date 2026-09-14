#!/bin/sh

echo "Retifica cron liga/desliga iniciando..."
echo "Timezone: $TZ"
echo "Horario configurado:"
cat /app/crontab
echo "Servicos gerenciados: $SERVICES_ID"
echo "Iniciando supercronic..."

exec /usr/local/bin/supercronic /app/crontab
