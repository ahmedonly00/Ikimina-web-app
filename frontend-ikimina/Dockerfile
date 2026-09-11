# ---- build stage ----------------------------------------------------------
FROM node:26-alpine AS build
WORKDIR /build

# npm ci needs the lockfile and honours it exactly, so builds are reproducible.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- runtime stage --------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

# nginx's default config runs the master as root; use the unprivileged variant
# by pointing pid/temp paths at writable locations and dropping to nginx user.
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

RUN touch /var/run/nginx.pid \
 && chown -R nginx:nginx /var/run/nginx.pid /var/cache/nginx /usr/share/nginx/html
USER nginx

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1