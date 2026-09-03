import type { Metadata } from 'next';
import './globals.css';
import { Cabecalho } from '@/components/Cabecalho';

export const metadata: Metadata = {
  title: 'Outorga TV',
  description: 'Plataforma de streaming white-label com controle de direitos de exibição',
  // O arquivo app/icon.svg vira o favicon sozinho no Next; declarar aqui
  // serve para o caminho não sumir numa limpeza de pasta sem ninguém notar.
  icons: { icon: '/icon.svg' },
  robots: {
    // Enquanto o serviço é de demonstração, não há motivo para indexar.
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
              Outorga TV. Plataforma de streaming. O catálogo e o direito de exibição são do
              cliente.
            </span>
            <span className="mono">v0.1.0</span>
          </div>
        </footer>
      </body>
    </html>
  );
}
