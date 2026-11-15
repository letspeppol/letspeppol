FROM node:20-alpine AS source
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install --include=dev

COPY . .

FROM source AS build
RUN npm run build

FROM source AS storybook-build
RUN npm run build-storybook

FROM nginx:1.27-alpine AS storybook
RUN rm -rf /usr/share/nginx/html/*
COPY --from=storybook-build /app/storybook-static /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

FROM nginx:1.27-alpine AS runtime

RUN rm -rf /usr/share/nginx/html/*
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.prod.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
