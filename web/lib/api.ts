/**
 * Cliente da API do Outorga TV.
 *
 * Tudo passa por aqui de proposito. Assim existe um lugar so que conhece o
 * cabecalho de autenticacao, o identificador do aparelho e o formato do erro
 * que o servidor devolve, e as telas ficam falando de tela.
 */

const BASE = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
export const SERVICO = process.env.NEXT_PUBLIC_SERVICO ?? 'cineserra';

const CHAVE_SESSAO = 'outorga.sessao';
const CHAVE_APARELHO = 'outorga.aparelho';
const CHAVE_PERFIL = 'outorga.perfil';

export type Sessao = {
  acesso: string;
  refresh: string;
  expiraEm: string;
  usuarioId: string | null;
  nome: string | null;
  papeis: string[];
};

export type ErroDaApi = {
  codigo: string;
  mensagem: string;
  detalhes?: Record<string, unknown>;
};

export class FalhaDaApi extends Error {
  constructor(
    readonly codigo: string,
    mensagem: string,
    readonly status: number,
    readonly detalhes: Record<string, unknown> = {},
  ) {
    super(mensagem);
    this.name = 'FalhaDaApi';
  }
}

/**
 * Identificador do aparelho. Fica no armazenamento local porque precisa
 * sobreviver ao fechar da aba: se mudasse a cada visita, cada abertura do
 * site gastaria uma vaga no limite de aparelhos da conta.
 */
export function identificadorDoAparelho(): string {
  if (typeof window === 'undefined') return 'servidor';
  let id = window.localStorage.getItem(CHAVE_APARELHO);
  if (!id) {
    id = crypto.randomUUID();
    window.localStorage.setItem(CHAVE_APARELHO, id);
  }
  return id;
}

export function sessaoGuardada(): Sessao | null {
  if (typeof window === 'undefined') return null;
  const cru = window.localStorage.getItem(CHAVE_SESSAO);
  if (!cru) return null;
  try {
    return JSON.parse(cru) as Sessao;
  } catch {
    window.localStorage.removeItem(CHAVE_SESSAO);
    return null;
  }
}

export function guardarSessao(sessao: Sessao) {
  window.localStorage.setItem(CHAVE_SESSAO, JSON.stringify(sessao));
}

export function encerrarSessao() {
  window.localStorage.removeItem(CHAVE_SESSAO);
  window.localStorage.removeItem(CHAVE_PERFIL);
}

export function perfilEscolhido(): string | null {
  if (typeof window === 'undefined') return null;
  return window.localStorage.getItem(CHAVE_PERFIL);
}

export function escolherPerfil(id: string) {
  window.localStorage.setItem(CHAVE_PERFIL, id);
}

type Opcoes = {
  metodo?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  corpo?: unknown;
  autenticado?: boolean;
  cabecalhosExtras?: Record<string, string>;
};

async function chamar<T>(caminho: string, opcoes: Opcoes = {}): Promise<T> {
  const cabecalhos: Record<string, string> = {
    'Content-Type': 'application/json',
    ...opcoes.cabecalhosExtras,
  };

  if (opcoes.autenticado) {
    const sessao = sessaoGuardada();
    if (!sessao) throw new FalhaDaApi('SEM_SESSAO', 'Entre na sua conta', 401);
    cabecalhos.Authorization = `Bearer ${sessao.acesso}`;
  }

  const resposta = await fetch(`${BASE}${caminho}`, {
    method: opcoes.metodo ?? 'GET',
    headers: cabecalhos,
    body: opcoes.corpo === undefined ? undefined : JSON.stringify(opcoes.corpo),
    cache: 'no-store',
  });

  if (resposta.status === 204) return undefined as T;

  const tipo = resposta.headers.get('content-type') ?? '';
  const corpo = tipo.includes('application/json') ? await resposta.json() : await resposta.text();

  if (!resposta.ok) {
    const erro = corpo as ErroDaApi;
    throw new FalhaDaApi(
      erro?.codigo ?? 'ERRO',
      erro?.mensagem ?? 'Nao foi possivel completar a operacao',
      resposta.status,
      erro?.detalhes ?? {},
    );
  }

  return corpo as T;
}

// ---------- Vitrine ----------

export type Identidade = {
  slug: string;
  nome: string;
  logo: string | null;
  corPrimaria: string;
  corDeFundo: string;
  aceitandoAcesso: boolean;
};

export type TituloResumido = {
  id: string;
  tipo: 'FILME' | 'SERIE';
  nome: string;
  ano: number | null;
  classificacao: string;
  capa: string | null;
  generos: string[];
  duracaoSegundos: number | null;
};

export type EpisodioVisto = {
  id: string;
  numero: number;
  nome: string;
  duracaoSegundos: number | null;
  disponivel: boolean;
};

export type TemporadaVista = {
  id: string;
  numero: number;
  nome: string;
  episodios: EpisodioVisto[];
};

export type TituloDetalhado = TituloResumido & {
  sinopse: string | null;
  temporadas: TemporadaVista[];
};

export type PlanoVisto = {
  id: string;
  nome: string;
  descricao: string | null;
  preco: string;
  precoCentavos: number;
  periodicidade: string;
  telas: number;
  qualidade: string;
  diasDeTeste: number;
};

export type CanalVisto = {
  id: string;
  nome: string;
  numero: number;
  logo: string | null;
  classificacao: string;
  noAr: boolean;
};

export const vitrine = {
  identidade: () => chamar<Identidade>(`/api/v1/publico/${SERVICO}/identidade`),
  catalogo: (perfil?: string | null) =>
    chamar<TituloResumido[]>(
      `/api/v1/publico/${SERVICO}/catalogo?tamanho=40${perfil ? `&perfil=${perfil}` : ''}`,
    ),
  busca: (termo: string, perfil?: string | null) =>
    chamar<TituloResumido[]>(
      `/api/v1/publico/${SERVICO}/busca?q=${encodeURIComponent(termo)}${
        perfil ? `&perfil=${perfil}` : ''
      }`,
    ),
  titulo: (id: string) => chamar<TituloDetalhado>(`/api/v1/publico/${SERVICO}/titulos/${id}`),
  planos: () => chamar<PlanoVisto[]>(`/api/v1/publico/${SERVICO}/planos`),
  canais: (perfil?: string | null) =>
    chamar<CanalVisto[]>(
      `/api/v1/publico/${SERVICO}/canais${perfil ? `?perfil=${perfil}` : ''}`,
    ),
};

// ---------- Conta ----------

export const conta = {
  entrar: (email: string, senha: string) =>
    chamar<Sessao>('/api/v1/auth/login', {
      metodo: 'POST',
      corpo: { servico: SERVICO, email, senha },
    }),

  cadastrar: (nome: string, email: string, senha: string) =>
    chamar<Sessao>('/api/v1/auth/cadastro', {
      metodo: 'POST',
      corpo: { servico: SERVICO, nome, email, senha },
    }),

  perfis: () =>
    chamar<{ id: string; nome: string; tetoDeClassificacao: string; infantil: boolean }[]>(
      '/api/v1/me/perfis',
      { autenticado: true },
    ),

  criarPerfil: (nome: string, infantil: boolean) =>
    chamar('/api/v1/me/perfis', {
      metodo: 'POST',
      autenticado: true,
      corpo: { nome, infantil },
    }),

  dispositivos: () =>
    chamar<{ id: string; apelido: string; tipo: string; ultimoUso: string }[]>(
      '/api/v1/me/dispositivos',
      { autenticado: true },
    ),

  removerDispositivo: (id: string) =>
    chamar(`/api/v1/me/dispositivos/${id}`, { metodo: 'DELETE', autenticado: true }),

  meusDados: () => chamar<Record<string, unknown>>('/api/v1/me/meus-dados', { autenticado: true }),

  apagarConta: () =>
    chamar<{ resultado: string }>('/api/v1/me/minha-conta', {
      metodo: 'DELETE',
      autenticado: true,
    }),
};

// ---------- Assinatura ----------

export type Checkout = {
  assinaturaId: string;
  urlDeCheckout: string | null;
  pixCopiaECola: string | null;
  valorFormatado: string;
};

export type AssinaturaVista = {
  id: string;
  status: string;
  iniciadaEm: string;
  fimDoCiclo: string;
  fimDaCarencia: string;
  assistindoAgora: boolean;
};

export const assinatura = {
  contratar: (planoId: string, cupom?: string, documento?: string) =>
    chamar<Checkout>('/api/v1/assinaturas', {
      metodo: 'POST',
      autenticado: true,
      corpo: {
        planoId,
        cupom: cupom || null,
        documento: documento || null,
        urlDeRetorno: typeof window === 'undefined' ? null : window.location.origin,
      },
    }),

  minha: () => chamar<AssinaturaVista>('/api/v1/assinaturas/minha', { autenticado: true }),

  cancelar: (motivo: string) =>
    chamar<AssinaturaVista>('/api/v1/assinaturas/minha', {
      metodo: 'DELETE',
      autenticado: true,
      corpo: { motivo },
    }),
};

// ---------- Reproducao ----------

export type Reproducao = {
  sessaoId: string;
  manifesto: string;
  formato: string;
  qualidade: string;
  expiraEm: string;
};

export const reproducao = {
  autorizar: (params: {
    tituloId: string;
    temporada?: number;
    episodio?: number;
    perfilId?: string | null;
    qualidade?: string;
  }) =>
    chamar<Reproducao>('/api/v1/reproducao/token', {
      metodo: 'POST',
      autenticado: true,
      cabecalhosExtras: { 'X-Outorga-Dispositivo': identificadorDoAparelho() },
      corpo: {
        tituloId: params.tituloId,
        temporada: params.temporada ?? null,
        episodio: params.episodio ?? null,
        perfilId: params.perfilId ?? null,
        tipoDeDispositivo: 'WEB',
        apelidoDoDispositivo: 'Navegador',
        territorio: 'BR',
        qualidade: params.qualidade ?? 'FULL_HD',
      },
    }),

  sinalDeVida: (sessaoId: string, posicaoEmSegundos: number) =>
    chamar(`/api/v1/reproducao/sessoes/${sessaoId}/sinal`, {
      metodo: 'POST',
      autenticado: true,
      corpo: { posicaoEmSegundos },
    }),

  encerrar: (sessaoId: string, posicaoEmSegundos: number) =>
    chamar(`/api/v1/reproducao/sessoes/${sessaoId}`, {
      metodo: 'DELETE',
      autenticado: true,
      corpo: { posicaoEmSegundos },
    }),
};

// ---------- Painel ----------

export type LicencaVista = {
  id: string;
  titular: string;
  contrato: string;
  territorios: string[];
  dispositivos: string[];
  inicio: string;
  fim: string | null;
  status: string;
  temComprovacao: boolean;
};

export type TituloNoPainel = {
  id: string;
  tipo: string;
  nome: string;
  status: string;
  classificacao: string;
  licencaId: string | null;
  motivoDoBloqueio: string | null;
  temVideo: boolean;
};

export type LicencaAVencer = {
  licencaId: string;
  titular: string;
  contrato: string;
  diasRestantes: number;
  titulosAfetados: number;
};

export const painel = {
  licencas: () => chamar<LicencaVista[]>('/api/v1/painel/licencas', { autenticado: true }),
  titulos: () => chamar<TituloNoPainel[]>('/api/v1/painel/titulos', { autenticado: true }),
  aVencer: (dias = 60) =>
    chamar<LicencaAVencer[]>(`/api/v1/painel/licencas/a-vencer?dias=${dias}`, {
      autenticado: true,
    }),
  publicar: (tituloId: string, licencaId: string) =>
    chamar<TituloNoPainel>(`/api/v1/painel/titulos/${tituloId}/publicacao`, {
      metodo: 'POST',
      autenticado: true,
      corpo: { licencaId },
    }),
  rescindir: (licencaId: string, motivo: string) =>
    chamar<{ licencaId: string; titulosBloqueados: number; canaisTirados: number }>(
      `/api/v1/painel/licencas/${licencaId}/rescisao`,
      { metodo: 'POST', autenticado: true, corpo: { motivo } },
    ),
};

export function duracaoLegivel(segundos: number | null): string {
  if (!segundos) return '';
  const horas = Math.floor(segundos / 3600);
  const minutos = Math.round((segundos % 3600) / 60);
  return horas > 0 ? `${horas}h${String(minutos).padStart(2, '0')}` : `${minutos} min`;
}
