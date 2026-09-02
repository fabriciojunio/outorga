'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import { encerrarSessao, sessaoGuardada, vitrine, type Identidade } from '@/lib/api';

/**
 * Barra de topo. Le a identidade do servico na API para se pintar com a marca
 * do cliente: e o que faz a mesma base servir a servicos diferentes.
 */
export function Cabecalho() {
  const caminho = usePathname();
  const [identidade, setIdentidade] = useState<Identidade | null>(null);
  const [entrou, setEntrou] = useState(false);
  const [operador, setOperador] = useState(false);

  useEffect(() => {
    vitrine.identidade().then(setIdentidade).catch(() => setIdentidade(null));
  }, []);

  useEffect(() => {
    const sessao = sessaoGuardada();
    setEntrou(sessao !== null);
    setOperador(
      sessao?.papeis?.some((p) => ['DONO', 'EDITOR', 'SUPORTE', 'ADMIN_PLATAFORMA'].includes(p)) ??
        false,
    );
  }, [caminho]);

  const nome = identidade?.nome ?? 'Outorga TV';

  return (
    <>
      <header className="cabecalho">
        <div className="envolucro">
          <Link href="/" className="marca">
            <span className="traco" />
            {nome}
          </Link>
          <nav className="navegacao">
            <Link href="/" className={caminho === '/' ? 'ativo' : ''}>
              Catalogo
            </Link>
            {operador && (
              <Link href="/painel" className={caminho.startsWith('/painel') ? 'ativo' : ''}>
                Painel
              </Link>
            )}
            {entrou ? (
              <>
                <Link href="/conta" className={caminho.startsWith('/conta') ? 'ativo' : ''}>
                  Minha conta
                </Link>
                <button
                  className="botao secundario"
                  onClick={() => {
                    encerrarSessao();
                    window.location.href = '/';
                  }}
                >
                  Sair
                </button>
              </>
            ) : (
              <Link href="/entrar" className="botao">
                Entrar
              </Link>
            )}
          </nav>
        </div>
      </header>
      <FaixaDeDemonstracao />
    </>
  );
}

/**
 * A faixa existe para que ninguem confunda a demonstracao com um servico em
 * operacao. Some sozinha quando o ambiente sai do modo de demonstracao.
 */
function FaixaDeDemonstracao() {
  const [mostrar, setMostrar] = useState(false);

  useEffect(() => {
    // O modo aparece no proprio comportamento do checkout, mas para a faixa
    // basta a variavel de ambiente do build.
    setMostrar(process.env.NEXT_PUBLIC_DEMONSTRACAO !== 'false');
  }, []);

  if (!mostrar) return null;

  return (
    <div className="faixa-demonstracao">
      Ambiente de demonstracao. O catalogo e ficticio, o video e um arquivo de teste e nenhuma
      cobranca e feita de verdade.
    </div>
  );
}
