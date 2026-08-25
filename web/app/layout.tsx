import type { Metadata } from 'next';
import './globals.css';
import { Cabecalho } from '@/components/Cabecalho';

export const metadata: Metadata = {
  title: 'Mirante',
  description: 'Plataforma de streaming white-label com controle de direitos de exibicao',
  robots: {
    // Enquanto o servico e de demonstracao, nao ha motivo para indexar.
    index: false,
    follow: false,
  },
};

export default function LayoutRaiz({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR">
      <body>
        <Cabecalho />
        <main>{children}</main>
        <footer className="rodape">
          <div className="envolucro espalha">
            <span>
              Mirante. Plataforma de streaming. O catalogo e o direito de exibicao sao do
              cliente.
            </span>
            <span className="mono">v0.1.0</span>
          </div>
        </footer>
      </body>
    </html>
  );
}
