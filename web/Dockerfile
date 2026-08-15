# Build stage: produce the static bundle.
FROM node:20-alpine AS build
WORKDIR /build
# Copy the manifests first so the dependency layer is cached when only sources change.
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
