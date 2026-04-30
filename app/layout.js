import "./globals.css";

export const metadata = {
  title: "Chatbot Console",
  description: "Responsive React frontend for the Spring AI chatbot service.",
};

export default function RootLayout({ children }) {
  // SSR-safe theme attribute (default to light, JS will update on client)
  let theme = 'light';
  if (typeof window !== 'undefined') {
    theme = window.localStorage.getItem('theme') || 'light';
  }
  return (
    <html lang="en" data-theme={theme}>
      <body>{children}</body>
    </html>
  );
}
