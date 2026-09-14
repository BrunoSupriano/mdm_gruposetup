# Imagem de DEV para o docker compose (Vite dev server com hot reload).
# Para produção, o frontend é estático (Render Static Site / Vercel) - veja README.
FROM node:20-alpine
WORKDIR /app
COPY package.json ./
RUN npm install
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
