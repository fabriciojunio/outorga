/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,

  // Source map em producao entrega o codigo original para qualquer um que
  // abrir o inspetor. Nao ha ganho que compense.
  productionBrowserSourceMaps: false,

  poweredByHeader: false,

  async headers() {
    const api = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
    return [
      {
        source: '/:caminho*',
        headers: [
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'X-Frame-Options', value: 'DENY' },
          {
            key: 'Permissions-Policy',
            value: 'camera=(), microphone=(), geolocation=(), interest-cohort=()',
          },
          {
            key: 'Strict-Transport-Security',
            value: 'max-age=31536000; includeSubDomains',
          },
          {
            // O player precisa buscar manifesto e segmento de video no
            // proprio provedor, por isso media-src e connect-src sao mais
            // largos que o resto. O que nao entra e script de terceiro.
            key: 'Content-Security-Policy',
            value: [
              "default-src 'self'",
              "script-src 'self' 'unsafe-inline'",
              "style-src 'self' 'unsafe-inline'",
              "img-src 'self' data: https:",
              `connect-src 'self' ${api} https:`,
              "media-src 'self' blob: https:",
              "font-src 'self'",
              "frame-ancestors 'none'",
              "base-uri 'self'",
              "form-action 'self'",
            ].join('; '),
          },
        ],
      },
    ];
  },
};

export default nextConfig;
