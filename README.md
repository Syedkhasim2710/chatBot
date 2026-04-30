# Frontend Module

This is a standalone React frontend built with Next.js. It talks to the existing Spring Boot backend without changing any Java code.

## Run

Use the backend already running at `http://localhost:8081/chatbot`.

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:3000`.

## Proxy

Browser requests go to `/api/chatbot/*` on the Next server, and `next.config.mjs` rewrites them to the backend at:

`http://localhost:8081/chatbot/api/v1`

Override the backend target if needed:

```bash
CHATBOT_BACKEND_URL=http://localhost:8081/chatbot/api/v1 npm run dev
```
