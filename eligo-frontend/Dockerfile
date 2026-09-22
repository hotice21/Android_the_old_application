# syntax=docker/dockerfile:1

# Stage 1: Build H5 assets
FROM node:20-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./

# Install dependencies (use China mirror for faster local builds)
RUN npm ci --registry=https://registry.npmmirror.com

COPY . .

# Build the H5 bundle (VITE_DEV_SKIP_LOGIN keeps default false; auth decided by backend)
RUN npm run build:h5

# Stage 2: Nginx serving static assets with API proxy
FROM nginx:1.27-alpine

LABEL org.opencontainers.image.title="eligo-frontend" \
      org.opencontainers.image.description="Eligo H5 frontend (nginx)"

COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/build/h5 /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -qO- http://localhost/ > /dev/null || exit 1
