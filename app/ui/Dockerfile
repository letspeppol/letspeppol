FROM node:20-alpine AS source
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install --include=dev

COPY . .

FROM source AS build
RUN npm run build

FROM nginx:1.27-alpine AS runtime

RUN rm -rf /usr/share/nginx/html/*
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.prod.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
