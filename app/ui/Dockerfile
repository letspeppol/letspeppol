# Multi-stage build for Aurelia/Vite frontend
FROM node:20-alpine AS build
WORKDIR /app

# Install dependencies with caching
COPY package.json package-lock.json* ./
RUN npm install --include=dev

# Copy source
COPY . .
RUN npm run build

# Production image using nginx to serve static assets
FROM nginx:1.27-alpine AS runtime
# Remove default nginx static assets
RUN rm -rf /usr/share/nginx/html/*
COPY --from=build /app/dist /usr/share/nginx/html

# Provide a default nginx config (cache headers etc.)
RUN if [ "$ENVIRONMENT" = "local" ]; then \
      cp nginx.dev.conf /etc/nginx/conf.d/default.conf; \
    else \
      cp nginx.prod.conf /etc/nginx/conf.d/default.conf; \
    fi

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
