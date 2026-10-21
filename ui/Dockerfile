# Build stage runs on the build machine's own architecture: the output is static files, the same for every platform.
FROM --platform=$BUILDPLATFORM node:24.22.0-alpine AS build
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

# Same base image as the cluster's previous placeholder: non-root (uid 101), listens on 8080, temp files in /tmp.
FROM nginxinc/nginx-unprivileged:1.29.1-alpine
COPY nginx/security-headers.conf /etc/nginx/security-headers.conf
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
