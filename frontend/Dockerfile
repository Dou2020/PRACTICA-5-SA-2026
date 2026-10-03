# Etapa 1: compila. Las dependencias van en una capa propia (solo los package*.json),
# así un cambio en src/ no vuelve a descargar todo npm.
FROM node:22-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# Etapa 2: solo nginx y los estáticos. nginx además hace de proxy a /api.
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
