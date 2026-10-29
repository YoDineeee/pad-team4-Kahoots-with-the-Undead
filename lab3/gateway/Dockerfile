# Small official Node.js image (Alpine Linux)
FROM node:22-alpine

# Production mode: Express runs faster and with less verbose errors
ENV NODE_ENV=production
WORKDIR /app

# Copy only the dependency files first -> this layer is cached until package*.json changes
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY src ./src

# Don't run as root inside the container
USER node

EXPOSE 8080

# Docker marks the container "unhealthy" if the gateway process stops answering
HEALTHCHECK --interval=15s --timeout=3s --retries=3 \
  CMD node -e "fetch('http://localhost:8080/').then(r => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"

CMD ["node", "src/server.js"]
