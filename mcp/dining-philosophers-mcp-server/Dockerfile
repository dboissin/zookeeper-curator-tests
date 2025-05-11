FROM node:24-bookworm-slim as builder

WORKDIR /app
COPY . .

RUN --mount=type=cache,target=/root/.npm npm install

RUN npm run build

FROM node:24-bookworm-slim AS release

COPY --from=builder /app/dist/index.js /app/dist/index.js
COPY --from=builder /app/package.json /app/package.json
COPY --from=builder /app/package-lock.json /app/package-lock.json

ENV NODE_ENV=production

WORKDIR /app

RUN npm ci --ignore-scripts --omit-dev

ENTRYPOINT ["node", "dist/index.js"]