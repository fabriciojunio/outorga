'use client';

import { useEffect, useState } from 'react';
import {
  assinatura as apiAssinatura,
  conta,
  escolherPerfil,
  perfilEscolhido,
  sessaoGuardada,
  type AssinaturaVista,
} from '@/lib/api';

type Perfil = { id: string; nome: string; tetoDeClassificacao: string; infantil: boolean };
type Dispositivo = { id: string; apelido: string; tipo: string; ultimoUso: string };

export default function Conta() {
  const [perfis, setPerfis] = useState<Perfil[]>([]);
  const [dispositivos, setDispositivos] = useState<Dispositivo[]>([]);
  const [assinatura, setAssinatura] = useState<AssinaturaVista | null>(null);
  const [ativo, setAtivo] = useState<string | null>(null);
  const [mensagem, setMensagem] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (!sessaoGuardada()) {
      window.location.href = '/entrar?voltar=/conta';
      return;
    }
    setAtivo(perfilEscolhido());
    void recarregar();
  }, []);

  async function recarregar() {
    const [p, d] = await Promise.all([conta.perfis(), conta.dispositivos()]);
    setPerfis(p);
    setDispositivos(d);
    try {
      setAssinatura(await apiAssinatura.minha());
    } catch {
      setAssinatura(null);
    }
  }

  async function removerAparelho(id: string) {
    try {
      await conta.removerDispositivo(id);
      setMensagem('Aparelho removido. A vaga ja esta livre.');
      await recarregar();
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  async function cancelar() {
    const motivo = window.prompt('Pode contar o motivo? Ajuda a melhorar o servico.');
    if (motivo === null) return;
    try {
      const nova = await apiAssinatura.cancelar(motivo);
      setAssinatura(nova);
      setMensagem('Assinatura cancelada. Voce continua assistindo ate o fim do periodo pago.');
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  async function baixarMeusDados() {
    const dados = await conta.meusDados();
    const arquivo = new Blob([JSON.stringify(dados, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(arquivo);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'meus-dados-mirante.json';
    link.click();
    URL.revokeObjectURL(url);
  }

  async function apagarConta() {
    if (!window.confirm('Apagar a conta e definitivo. Confirma?')) return;
    try {
      const saida = await conta.apagarConta();
      window.alert(saida.resultado);
      window.location.href = '/';
    } catch (e) {
      setErro((e as Error).message);
    }
  }

  return (
    <>
      {mensagem && (
        <div className="envolucro" style={{ paddingTop: 18 }}>
          <div className="aviso ok">{mensagem}</div>
        </div>
      )}
      {erro && (
        <div className="envolucro" style={{ paddingTop: 18 }}>
          <div className="aviso erro">{erro}</div>
        </div>
      )}

      <section className="secao">
        <div className="envolucro">
          <h2 className="titulo-secao">Assinatura</h2>
          {assinatura ? (
            <div className="cartao">
              <div className="espalha">
                <div>
                  <div style={{ fontWeight: 600, fontSize: 18 }}>
                    {assinatura.status.toLowerCase().replaceAll('_', ' ')}
                  </div>
                  <div className="apagado">
                    {assinatura.assistindoAgora
                      ? `Acesso liberado ate ${assinatura.fimDoCiclo.slice(0, 10)}`
                      : 'Sem acesso no momento'}
                  </div>
                </div>
                {assinatura.status !== 'CANCELADA' && assinatura.status !== 'ENCERRADA' && (
                  <button className="botao secundario" onClick={() => void cancelar()}>
                    Cancelar assinatura
                  </button>
                )}
              </div>
            </div>
          ) : (
            <p className="fraco">
              Voce ainda nao tem assinatura. <a href="/#planos">Ver planos</a>
            </p>
          )}
        </div>
      </section>

      <section className="secao">
        <div className="envolucro">
          <h2 className="titulo-secao">Perfis</h2>
          <div className="linha">
            {perfis.map((perfil) => (
              <button
                key={perfil.id}
                className={`cartao ${ativo === perfil.id ? '' : ''}`}
                onClick={() => {
                  escolherPerfil(perfil.id);
                  setAtivo(perfil.id);
                  setMensagem(`Perfil "${perfil.nome}" selecionado.`);
                }}
                style={{
                  minWidth: 170,
                  textAlign: 'left',
                  cursor: 'pointer',
                  borderColor: ativo === perfil.id ? 'var(--ambar)' : undefined,
                }}
              >
                <div style={{ fontWeight: 600 }}>{perfil.nome}</div>
                <div className="apagado">
                  {perfil.infantil ? 'Infantil' : 'Adulto'} · ate{' '}
                  {perfil.tetoDeClassificacao === 'L' ? 'livre' : `${perfil.tetoDeClassificacao} anos`}
                </div>
              </button>
            ))}
          </div>
          <p className="apagado" style={{ marginTop: 12 }}>
            O perfil escolhido filtra o catalogo pela classificacao indicativa.
          </p>
        </div>
      </section>

      <section className="secao">
        <div className="envolucro">
          <h2 className="titulo-secao">Aparelhos</h2>
          <div className="rolagem">
            <table className="tabela">
              <thead>
                <tr>
                  <th>Aparelho</th>
                  <th>Tipo</th>
                  <th>Ultimo uso</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {dispositivos.map((dispositivo) => (
                  <tr key={dispositivo.id}>
                    <td>{dispositivo.apelido}</td>
                    <td className="apagado">{dispositivo.tipo.toLowerCase()}</td>
                    <td className="apagado mono">{dispositivo.ultimoUso.slice(0, 16)}</td>
                    <td style={{ textAlign: 'right' }}>
                      <button
                        className="botao secundario"
                        onClick={() => void removerAparelho(dispositivo.id)}
                      >
                        Remover
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {dispositivos.length === 0 && <p className="fraco">Nenhum aparelho registrado ainda.</p>}
        </div>
      </section>

      <section className="secao">
        <div className="envolucro">
          <h2 className="titulo-secao">Seus dados</h2>
          <div className="cartao">
            <p className="fraco" style={{ marginTop: 0 }}>
              Pela LGPD voce pode ver tudo que guardamos e pedir a exclusao. A exclusao apaga o
              que identifica voce; o registro contabil das cobrancas continua, sem dono, porque a
              lei obriga a guarda-lo.
            </p>
            <div className="linha">
              <button className="botao secundario" onClick={() => void baixarMeusDados()}>
                Baixar meus dados
              </button>
              <button className="botao perigo" onClick={() => void apagarConta()}>
                Apagar minha conta
              </button>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
