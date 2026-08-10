FROM node:20-alpine

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm install

COPY src ./src
COPY eslint.config.mjs ./

EXPOSE 3000

CMD ["node", "src/index.js"]