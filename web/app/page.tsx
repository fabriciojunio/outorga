'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import {
  assinatura as apiAssinatura,
  duracaoLegivel,
  perfilEscolhido,
  sessaoGuardada,
  vitrine,
  type CanalVisto,
  type PlanoVisto,
  type TituloResumido,
} from '@/lib/api';

export default function Vitrine() {
  const [titulos, setTitulos] = useState<TituloResumido[]>([]);
  const [planos, setPlanos] = useState<PlanoVisto[]>([]);
  const [canais, setCanais] = useState<CanalVisto[]>([]);
  const [busca, setBusca] = useState('');
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    const perfil = perfilEscolhido();
    Promise.all([vitrine.catalogo(perfil), vitrine.planos(), vitrine.canais(perfil)])
      .then(([catalogo, tabela, grade]) => {
        setTitulos(catalogo);
        setPlanos(tabela);
        setCanais(grade);
      })
      .catch((e: Error) => setErro(e.message))
      .finally(() => setCarregando(false));
  }, []);

  async function procurar(termo: string) {
    setBusca(termo);
    if (termo.trim().length < 2) {
      setTitulos(await vitrine.catalogo(perfilEscolhido()));
      return;
    }
    try {
      setTitulos(await vitrine.busca(termo, perfilEscolhido()));
    } catch {
      setTitulos([]);
    }
  }

  return (
    <>
      <section className="abertura">
        <div className="envolucro">
          <h1>Seu catálogo no ar, com o direito de exibição no lugar certo</h1>
          <p>
            Cada título aqui só aparece porque existe uma licença vigente por trás dele. Quando o
            contrato vence, o sistema tira do ar sozinho, sem depender de alguém lembrar.
          </p>
          <div className="acoes">
            <a href="#planos" className="botao">
              Ver planos
            </a>
            <Link href="/entrar" className="botao secundario">
              Já sou assinante
            </Link>
          </div>
        </div>
      </section>

      <section className="secao">
        <div className="envolucro">
          <div className="espalha" style={{ marginBottom: 20 }}>
            <h2 className="titulo-secao" style={{ margin: 0 }}>
              Catálogo
            </h2>
            <input
              className="campo"
              style={{
                padding: '9px 13px',
                background: 'var(--fundo)',
                border: '1px solid var(--borda-clara)',
                borderRadius: 'var(--raio-pequeno)',
                minWidth: 240,
              }}
              placeholder="Buscar por nome"
              value={busca}
              onChange={(e) => void procurar(e.target.value)}
            />
          </div>

          {erro && <div className="aviso erro">{erro}</div>}
          {carregando && <p className="carregando">Carregando o catalogo...</p>}

          {!carregando && titulos.length === 0 && (
            <p className="fraco">
              Nada por aqui ainda. Se você e o operador, cadastre a licença e publique o primeiro
              título pelo painel.
            </p>
          )}

          <div className="grade-titulos">
            {titulos.map((titulo) => (
              <Link key={titulo.id} href={`/assistir/${titulo.id}`} className="capa">
                {titulo.capa && <img src={titulo.capa} alt="" />}
                <div className="conteudo">
                  <div className="nome">{titulo.nome}</div>
                  <div className="detalhe">
                    {[
                      titulo.ano,
                      titulo.tipo === 'SERIE' ? 'Serie' : duracaoLegivel(titulo.duracaoSegundos),
                    ]
                      .filter(Boolean)
                      .join(' · ')}
                  </div>
                  <div style={{ marginTop: 8 }}>
                    <span
                      className={`selo ${
                        titulo.classificacao === 'L'
                          ? 'livre'
                          : titulo.classificacao === '18'
                            ? 'adulto'
                            : ''
                      }`}
                    >
                      {titulo.classificacao}
                    </span>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {canais.length > 0 && (
        <section className="secao">
          <div className="envolucro">
            <h2 className="titulo-secao">Canais ao vivo</h2>
            <div className="linha">
              {canais.map((canal) => (
                <div key={canal.id} className="cartao" style={{ minWidth: 190 }}>
                  <div className="apagado mono">Canal {canal.numero}</div>
                  <div style={{ fontWeight: 600, marginTop: 4 }}>{canal.nome}</div>
                  <div style={{ marginTop: 8 }}>
                    <span className="selo ok">no ar</span>{' '}
                    <span className="selo">{canal.classificacao}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>
      )}

      <section className="secao" id="planos">
        <div className="envolucro">
          <h2 className="titulo-secao">Planos</h2>
          <div className="grade-planos">
            {planos.map((plano, indice) => (
              <CartaoDePlano key={plano.id} plano={plano} destaque={indice === 1} />
            ))}
          </div>
          <p className="apagado" style={{ marginTop: 18 }}>
            Precos definidos pelo operador do serviço. Cancelamento a qualquer momento, com acesso
            garantido até o fim do período já pago.
          </p>
        </div>
      </section>
    </>
  );
}

function CartaoDePlano({ plano, destaque }: { plano: PlanoVisto; destaque: boolean }) {
  const [processando, setProcessando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function assinar() {
    if (!sessaoGuardada()) {
      window.location.href = `/entrar?voltar=${encodeURIComponent('/#planos')}`;
      return;
    }
    setProcessando(true);
    setErro(null);
    try {
      const checkout = await apiAssinatura.contratar(plano.id);
      if (checkout.urlDeCheckout) {
        window.location.href = checkout.urlDeCheckout;
      } else {
        setErro('A cobrança foi aberta, mas o gateway ainda não devolveu o link de pagamento.');
      }
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setProcessando(false);
    }
  }

  return (
    <div className={`plano ${destaque ? 'destaque' : ''}`}>
      <div className="apagado mono">{plano.nome}</div>
      <div className="preco">{plano.preco}</div>
      <div className="periodo">
        {plano.periodicidade === 'ANUAL' ? 'por ano' : 'por mês'}
        {plano.diasDeTeste > 0 ? ` · ${plano.diasDeTeste} dias de teste` : ''}
      </div>
      <ul>
        <li>{plano.telas === 1 ? '1 tela por vez' : `${plano.telas} telas ao mesmo tempo`}</li>
        <li>Até {plano.qualidade.replace('_', ' ')}</li>
        <li>{plano.telas * 2} aparelhos registrados</li>
        {plano.descricao && <li>{plano.descricao}</li>}
      </ul>
      {erro && <div className="aviso erro">{erro}</div>}
      <button className="botao largo" onClick={() => void assinar()} disabled={processando}>
        {processando ? 'Abrindo cobrança...' : 'Assinar'}
      </button>
    </div>
  );
}
