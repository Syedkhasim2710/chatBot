const backendUrl =
  process.env.CHATBOT_BACKEND_URL || "http://localhost:8081/chatbot/api/v1";

/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/chatbot/:path*",
        destination: `${backendUrl}/:path*`,
      },
    ];
  },
};

export default nextConfig;
