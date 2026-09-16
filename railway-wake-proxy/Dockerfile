# Proxy leve (sem dependencias externas, so Node built-in) que fica sempre
# ligado na frente do dominio publico e acorda o retifica-backend sob
# demanda fora do horario de funcionamento. Ver server.js.
FROM node:22-alpine
WORKDIR /app
COPY server.js .
ENV NODE_ENV=production
CMD ["node", "server.js"]
