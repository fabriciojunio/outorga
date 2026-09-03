'use client';

import Link from 'next/link';
import { use, useEffect, useState } from 'react';
import { Player } from '@/components/Player';
import {
  duracaoLegivel,
  perfilEscolhido,
  reproducao,
  sessaoGuardada,
  vitrine,
  FalhaDaApi,
  type Reproducao,
  type TituloDetalhado,
} from '@/lib/api';

/**
 * Página de reprodução.
 *
 * O tratamento de erro aqui é a razão de a API devolver código estável para
 * cada recusa. Quem chega numa tela dizendo "conteúdo indisponível" abre
 * chamado; quem lê "você atingiu o limite de 2 telas" resolve sozinho.
 */
export default function Assistir({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);

  const [titulo, setTitulo] = useState<TituloDetalhado | null>(null);
  const [play, setPlay] = useState<Reproducao | null>(null);
  const [recusa, setRecusa] = useState<FalhaDaApi | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [episodioAtual, setEpisodioAtual] = useState<{ temporada: number; episodio: number } | null>(
    null,
  );

  useEffect(() => {
    vitrine
      .titulo(id)
      .then(setTitulo)
      .catch(() => setTitulo(null))
      .finally(() => setCarregando(false));
  }, [id]);

  async function darPlay(temporada?: number, episodio?: number) {
    setRecusa(null);
    setPlay(null);
    if (!sessaoGuardada()) {
      window.location.href = `/entrar?voltar=${encodeURIComponent(`/assistir/${id}`)}`;
      return;
    }
    if (temporada && episodio) {
      setEpisodioAtual({ temporada, episodio });
    }
    try {
      setPlay(
        await reproducao.autorizar({
          tituloId: id,
          temporada,
          episodio,
          perfilId: perfilEscolhido(),
        }),
      );
    } catch (e) {
      setRecusa(e as FalhaDaApi);
    }
  }

  if (carregando) return <p className="carregando envolucro">Carregando...</p>;

  if (!titulo) {
    return (
      <section className="secao">
        <div className="envolucro">
          <div className="aviso erro">
            Este título não está disponível. Ou saiu do ar, ou a licença que autorizava a exibição
            deixou de valer.
          </div>
          <Link href="/" className="botao secundario">
            Voltar ao catálogo
          </Link>
        </div>
      </section>
    );
  }

  return (
    <section className="secao">
      <div className="envolucro">
        {play ? (
          <Player manifesto={play.manifesto} sessaoId={play.sessaoId} />
        ) : (
          <div
            className="palco"
            style={{
              aspectRatio: '16 / 9',
              display: 'grid',
              placeItems: 'center',
              background: 'linear-gradient(160deg, #1b2029 0%, #0b0d12 100%)',
            }}
          >
            <button className="botao" onClick={() => void darPlay()} disabled={recusa !== null}>
              {titulo.tipo === 'SERIE' ? 'Escolha um episódio abaixo' : 'Assistir'}
            </button>
          </div>
        )}

        {recusa && <MensagemDeRecusa recusa={recusa} />}

        <div style={{ marginTop: 26 }}>
          <div className="linha">
            <h1 style={{ margin: 0, fontSize: 26, letterSpacing: '-0.02em' }}>{titulo.nome}</h1>
            <span className={`selo ${titulo.classificacao === 'L' ? 'livre' : ''}`}>
              {titulo.classificacao}
            </span>
          </div>
          <p className="apagado" style={{ marginTop: 6 }}>
            {[titulo.ano, duracaoLegivel(titulo.duracaoSegundos), ...titulo.generos]
              .filter(Boolean)
              .join(' · ')}
          </p>
          {titulo.sinopse && (
            <p className="fraco" style={{ maxWidth: '68ch' }}>
              {titulo.sinopse}
            </p>
          )}
        </div>

        {titulo.temporadas.map((temporada) => (
          <div key={temporada.id} style={{ marginTop: 30 }}>
            <h2 className="titulo-secao">{temporada.nome}</h2>
            <div className="rolagem">
              <table className="tabela">
                <tbody>
                  {temporada.episodios.map((episodio) => {
                    const tocando =
                      episodioAtual?.temporada === temporada.numero &&
                      episodioAtual?.episodio === episodio.numero;
                    return (
                      <tr key={episodio.id}>
                        <td className="mono apagado" style={{ width: 60 }}>
                          {String(episodio.numero).padStart(2, '0')}
                        </td>
                        <td>{episodio.nome}</td>
                        <td className="apagado" style={{ width: 90 }}>
                          {duracaoLegivel(episodio.duracaoSegundos)}
                        </td>
                        <td style={{ width: 130, textAlign: 'right' }}>
                          <button
                            className={`botao ${tocando ? '' : 'secundario'}`}
                            disabled={!episodio.disponivel}
                            onClick={() => void darPlay(temporada.numero, episodio.numero)}
                          >
                            {episodio.disponivel ? (tocando ? 'Tocando' : 'Assistir') : 'Em breve'}
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function MensagemDeRecusa({ recusa }: { recusa: FalhaDaApi }) {
  const acao: Record<string, { texto: string; destino: string }> = {
    SEM_ASSINATURA: { texto: 'Ver planos', destino: '/#planos' },
    ASSINATURA_SEM_ACESSO: { texto: 'Ver planos', destino: '/#planos' },
    LIMITE_DE_DISPOSITIVOS: { texto: 'Gerenciar aparelhos', destino: '/conta' },
    LIMITE_DE_TELAS: { texto: 'Ver minha conta', destino: '/conta' },
  };
  const sugestao = acao[recusa.codigo];

  return (
    <div className={`aviso ${recusa.codigo === 'LIMITE_DE_TELAS' ? 'atencao' : 'erro'}`}>
      <div>{recusa.message}</div>
      {sugestao && (
        <Link href={sugestao.destino} className="botao secundario" style={{ marginTop: 12 }}>
          {sugestao.texto}
        </Link>
      )}
      <div className="mono apagado" style={{ marginTop: 10 }}>
        {recusa.codigo}
      </div>
    </div>
  );
}
