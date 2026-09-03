'use client';

import { useEffect, useState } from 'react';
import {
  painel,
  sessaoGuardada,
  type LicencaAVencer,
  type LicencaVista,
  type TituloNoPainel,
} from '@/lib/api';

/**
 * Painel do operador.
 *
 * A ordem das seções na tela é a ordem do trabalho: primeiro o que está
 * vencendo, depois as licenças, depois o catálogo. Quem abre o painel de
 * manhã precisa ver o problema antes de ver a lista.
 */
export default function Painel() {
  const [licencas, setLicencas] = useState<LicencaVista[]>([]);
  const [titulos, setTitulos] = useState<TituloNoPainel[]>([]);
  const [aVencer, setAVencer] = useState<LicencaAVencer[]>([]);
  const [erro, setErro] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(true);

  useEffect(() => {
    if (!sessaoGuardada()) {
      window.location.href = '/entrar?voltar=/painel';
      return;
    }
    void recarregar();
  }, []);

  async function recarregar() {
    setCarregando(true);
    try {
      const [l, t, v] = await Promise.all([painel.licencas(), painel.titulos(), painel.aVencer()]);
      setLicencas(l);
      setTitulos(t);
      setAVencer(v);
      setErro(null);
    } catch (e) {
      setErro((e as Error).message);
    } finally {
      setCarregando(false);
    }
  }

  async function publicar(tituloId: string, licencaId: string) {
    try {
      await painel.publicar(tituloId, licencaId);
      await recarregar();
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  async function rescindir(licencaId: string) {
    const motivo = window.prompt('Motivo da rescisão:');
    if (motivo === null) return;
    try {
      const saida = await painel.rescindir(licencaId, motivo);
      setErro(null);
      await recarregar();
      window.alert(
        `Licença rescindida. ${saida.titulosBloqueados} título(s) e ${saida.canaisTirados} canal(is) saíram do ar agora.`,
      );
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  if (carregando) return <p className="carregando envolucro">Carregando o painel...</p>;

  const vigentes = licencas.filter((l) => l.status === 'VIGENTE');

  return (
    <>
      {erro && (
        <div className="envolucro" style={{ paddingTop: 20 }}>
          <div className="aviso erro">{erro}</div>
        </div>
      )}

      <section className="secao">
        <div className="envolucro">
          <h2 className="titulo-secao">Direitos vencendo nos proximos 60 dias</h2>
          {aVencer.length === 0 ? (
            <div className="aviso ok">
              Nenhuma licença vence nos proximos 60 dias. Tudo que está no ar tem contrato em dia.
            </div>
          ) : (
            <div className="rolagem">
              <table className="tabela">
                <thead>
                  <tr>
                    <th>Titular</th>
                    <th>Contrato</th>
                    <th>Faltam</th>
                    <th>Títulos afetados</th>
                  </tr>
                </thead>
                <tbody>
                  {aVencer.map((item) => (
                    <tr key={item.licencaId}>
                      <td>{item.titular}</td>
                      <td className="mono">{item.contrato}</td>
                      <td>
                        <span
                          className={`selo ${item.diasRestantes <= 7 ? 'ruim' : 'atencao'}`}
                        >
                          {item.diasRestantes} dias
                        </span>
                      </td>
                      <td>{item.titulosAfetados}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>

      <section className="secao">
        <div className="envolucro">
          <h2 className="titulo-secao">Licenças</h2>
          <div className="rolagem">
            <table className="tabela">
              <thead>
                <tr>
                  <th>Titular</th>
                  <th>Contrato</th>
                  <th>Território</th>
                  <th>Aparelhos</th>
                  <th>Vigencia</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {licencas.map((licenca) => (
                  <tr key={licenca.id}>
                    <td>{licenca.titular}</td>
                    <td className="mono">{licenca.contrato}</td>
                    <td>{licenca.territorios.join(', ')}</td>
                    <td className="apagado">{licenca.dispositivos.join(', ')}</td>
                    <td className="apagado mono">
                      {licenca.inicio.slice(0, 10)} ate{' '}
                      {licenca.fim ? licenca.fim.slice(0, 10) : 'indeterminado'}
                    </td>
                    <td>
                      <span
                        className={`selo ${
                          licenca.status === 'VIGENTE'
                            ? 'ok'
                            : licenca.status === 'RESCINDIDA'
                              ? 'ruim'
                              : 'atencao'
                        }`}
                      >
                        {licenca.status.toLowerCase()}
                      </span>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      {licenca.status === 'VIGENTE' && (
                        <button
                          className="botao perigo"
                          onClick={() => void rescindir(licenca.id)}
                        >
                          Rescindir
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="apagado" style={{ marginTop: 12 }}>
            Rescindir tira do ar, na mesma hora, todo título e canal que dependia daquela licença.
          </p>
        </div>
      </section>

      <section className="secao">
        <div className="envolucro">
          <h2 className="titulo-secao">Catálogo</h2>
          <div className="rolagem">
            <table className="tabela">
              <thead>
                <tr>
                  <th>Título</th>
                  <th>Tipo</th>
                  <th>Classificação</th>
                  <th>Situação</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {titulos.map((titulo) => (
                  <tr key={titulo.id}>
                    <td>{titulo.nome}</td>
                    <td className="apagado">{titulo.tipo.toLowerCase()}</td>
                    <td>
                      <span className="selo">{titulo.classificacao}</span>
                    </td>
                    <td>
                      <span
                        className={`selo ${
                          titulo.status === 'PUBLICADO'
                            ? 'ok'
                            : titulo.status === 'BLOQUEADO_POR_DIREITO'
                              ? 'ruim'
                              : 'atencao'
                        }`}
                      >
                        {titulo.status.replaceAll('_', ' ').toLowerCase()}
                      </span>
                      {titulo.motivoDoBloqueio && (
                        <div className="apagado" style={{ marginTop: 4 }}>
                          {titulo.motivoDoBloqueio}
                        </div>
                      )}
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      {titulo.status !== 'PUBLICADO' && vigentes.length > 0 && (
                        <select
                          defaultValue=""
                          onChange={(e) => {
                            if (e.target.value) void publicar(titulo.id, e.target.value);
                          }}
                          style={{
                            padding: '8px 10px',
                            background: 'var(--fundo)',
                            border: '1px solid var(--borda-clara)',
                            borderRadius: 'var(--raio-pequeno)',
                          }}
                        >
                          <option value="">Publicar com a licenca...</option>
                          {vigentes.map((licenca) => (
                            <option key={licenca.id} value={licenca.id}>
                              {licenca.titular} · {licenca.contrato}
                            </option>
                          ))}
                        </select>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {titulos.length === 0 && (
            <p className="fraco">Nenhum titulo publicado ou bloqueado por direito.</p>
          )}
        </div>
      </section>
    </>
  );
}
