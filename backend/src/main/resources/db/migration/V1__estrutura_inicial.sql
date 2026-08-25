-- Mirante: estrutura inicial.
--
-- Convencao adotada em todo o esquema:
--   * identificadores sao uuid gerados pela aplicacao, nao pelo banco, porque
--     a entidade ja nasce com id no dominio antes de existir linha;
--   * toda tabela de dado de cliente carrega tenant_id, e todo indice de
--     consulta comeca por ele, porque nenhuma consulta do sistema e global;
--   * dinheiro em bigint de centavos, nunca numeric com casas decimais soltas;
--   * instante em timestamptz, sempre. Horario local so existe na tela.

-- Busca sem acento sem depender da extensao unaccent, que nem todo Postgres
-- gerenciado libera. translate() resolve o portugues e e immutable, entao
-- aceita indice funcional.
create function sem_acento(texto text) returns text as $$
    select translate(lower($1),
        'áàãâäéèêëíìîïóòõôöúùûüçñ',
        'aaaaaeeeeiiiiooooouuuucn')
$$ language sql immutable strict;

create table tenants (
    id                  uuid primary key,
    slug                varchar(32)  not null unique,
    nome                varchar(160) not null,
    documento           varchar(20),
    dominio_proprio     varchar(255) unique,
    marca_nome          varchar(160) not null,
    marca_logo_uri      text,
    marca_cor_primaria  varchar(7)   not null,
    marca_cor_fundo     varchar(7)   not null,
    status              varchar(20)  not null,
    motivo_suspensao    text,
    criado_em           timestamptz  not null,
    fim_do_teste        timestamptz
);

create table usuarios (
    id                  uuid primary key,
    tenant_id           uuid         not null references tenants (id),
    email               varchar(255) not null,
    senha_hash          varchar(255) not null,
    nome                varchar(160) not null,
    papeis              text[]       not null,
    ativo               boolean      not null default true,
    tentativas_seguidas int          not null default 0,
    bloqueado_ate       timestamptz,
    ultimo_acesso       timestamptz,
    criado_em           timestamptz  not null,
    anonimizado_em      timestamptz,
    constraint usuarios_email_por_tenant unique (tenant_id, email)
);

create index usuarios_por_tenant on usuarios (tenant_id);

-- Refresh token com rotacao. Guardado no banco em vez de Redis porque o
-- projeto precisa rodar inteiro em camada gratuita, e um Postgres a mais ja
-- existe. Se o mesmo jti aparecer duas vezes, e sinal de token vazado: a
-- sessao inteira do usuario cai.
create table refresh_tokens (
    jti                 uuid primary key,
    usuario_id          uuid         not null references usuarios (id) on delete cascade,
    tenant_id           uuid         not null,
    emitido_em          timestamptz  not null,
    expira_em           timestamptz  not null,
    usado_em            timestamptz,
    revogado_em         timestamptz
);

create index refresh_por_usuario on refresh_tokens (usuario_id, expira_em);

create table perfis (
    id                  uuid primary key,
    usuario_id          uuid         not null references usuarios (id) on delete cascade,
    nome                varchar(60)  not null,
    teto_classificacao  varchar(20)  not null,
    pin_hash            varchar(255),
    infantil            boolean      not null default false,
    avatar              varchar(255)
);

create index perfis_por_usuario on perfis (usuario_id);

create table dispositivos (
    id                  uuid primary key,
    usuario_id          uuid         not null references usuarios (id) on delete cascade,
    identificador       varchar(128) not null,
    tipo                varchar(20)  not null,
    apelido             varchar(80),
    registrado_em       timestamptz  not null,
    ultimo_uso          timestamptz  not null,
    constraint dispositivo_unico_por_conta unique (usuario_id, identificador)
);

-- Licencas: o coracao do gate de conteudo.
create table licencas (
    id                  uuid primary key,
    tenant_id           uuid         not null references tenants (id),
    titular             varchar(200) not null,
    referencia_contrato varchar(200) not null,
    territorios         text[]       not null,
    dispositivos        text[]       not null,
    janela_inicio       timestamptz  not null,
    janela_fim          timestamptz,
    comprovacao_uri     text,
    status              varchar(20)  not null,
    observacao          text,
    constraint janela_coerente check (janela_fim is null or janela_fim > janela_inicio)
);

create index licencas_por_tenant on licencas (tenant_id);
-- A varredura de direitos e o alerta de vencimento consultam por esta coluna.
create index licencas_por_vencimento on licencas (janela_fim) where janela_fim is not null;

create table titulos (
    id                  uuid primary key,
    tenant_id           uuid         not null references tenants (id),
    tipo                varchar(10)  not null,
    nome                varchar(200) not null,
    sinopse             text,
    ano_producao        int,
    classificacao       varchar(20)  not null,
    duracao_segundos    bigint,
    referencia_video    varchar(255),
    capa_uri            text,
    generos             text[]       not null default '{}',
    licenca_id          uuid references licencas (id),
    status              varchar(30)  not null,
    publicado_em        timestamptz,
    motivo_bloqueio     text
);

create index titulos_por_tenant_status on titulos (tenant_id, status);
create index titulos_por_licenca on titulos (tenant_id, licenca_id);
create index titulos_por_nome on titulos (tenant_id, sem_acento(nome));

create table temporadas (
    id                  uuid primary key,
    titulo_id           uuid         not null references titulos (id) on delete cascade,
    numero              int          not null,
    nome                varchar(120) not null,
    constraint temporada_unica unique (titulo_id, numero)
);

create table episodios (
    id                  uuid primary key,
    temporada_id        uuid         not null references temporadas (id) on delete cascade,
    numero              int          not null,
    nome                varchar(200) not null,
    sinopse             text,
    duracao_segundos    bigint       not null,
    referencia_video    varchar(255),
    constraint episodio_unico unique (temporada_id, numero)
);

create table planos (
    id                  uuid primary key,
    tenant_id           uuid         not null references tenants (id),
    nome                varchar(80)  not null,
    descricao           text,
    preco_centavos      bigint       not null check (preco_centavos >= 0),
    moeda               varchar(3)   not null default 'BRL',
    periodicidade       varchar(20)  not null,
    telas_simultaneas   int          not null check (telas_simultaneas between 1 and 6),
    qualidade_maxima    varchar(20)  not null,
    dias_de_teste       int          not null default 0,
    ativo               boolean      not null default true
);

create index planos_por_tenant on planos (tenant_id, ativo);

create table cupons (
    id                  uuid primary key,
    tenant_id           uuid         not null references tenants (id),
    codigo              varchar(40)  not null,
    percentual          int          not null check (percentual between 1 and 100),
    valido_ate          timestamptz,
    usos_maximos        int          not null,
    usos                int          not null default 0,
    ativo               boolean      not null default true,
    constraint cupom_unico_por_tenant unique (tenant_id, codigo)
);

create table assinaturas (
    id                  uuid primary key,
    tenant_id           uuid         not null references tenants (id),
    usuario_id          uuid         not null references usuarios (id),
    plano_id            uuid         not null references planos (id),
    status              varchar(20)  not null,
    iniciada_em         timestamptz  not null,
    fim_do_ciclo        timestamptz,
    fim_da_carencia     timestamptz,
    encerrada_em        timestamptz,
    referencia_gateway  varchar(120) unique
);

create index assinaturas_por_usuario on assinaturas (tenant_id, usuario_id);
-- A rotina de encerramento varre por estas duas colunas.
create index assinaturas_por_vencimento on assinaturas (status, fim_do_ciclo);

create table assinatura_eventos (
    id                  uuid primary key,
    assinatura_id       uuid         not null references assinaturas (id) on delete cascade,
    tipo                varchar(40)  not null,
    detalhe             text,
    ocorrido_em         timestamptz  not null
);

create index eventos_por_assinatura on assinatura_eventos (assinatura_id, ocorrido_em);

create table canais (
    id                  uuid primary key,
    tenant_id           uuid         not null references tenants (id),
    nome                varchar(120) not null,
    logo_uri            text,
    numero              int          not null,
    url_fonte           text,
    classificacao       varchar(20)  not null,
    licenca_id          uuid references licencas (id),
    no_ar               boolean      not null default false,
    motivo_bloqueio     text,
    -- Separa o canal que a varredura tirou por falta de direito do canal que
    -- o operador desligou. So o primeiro volta sozinho.
    bloqueado_por_direito boolean    not null default false,
    constraint canal_numero_unico unique (tenant_id, numero)
);

create index canais_por_tenant on canais (tenant_id, no_ar);
create index canais_por_licenca on canais (tenant_id, licenca_id);

create table epg_programas (
    id                  uuid primary key,
    tenant_id           uuid         not null references tenants (id),
    canal_id            uuid         not null references canais (id) on delete cascade,
    titulo              varchar(200) not null,
    descricao           text,
    inicio              timestamptz  not null,
    fim                 timestamptz  not null,
    classificacao       varchar(20)  not null,
    constraint epg_horario_coerente check (fim > inicio)
);

create index epg_por_canal_e_hora on epg_programas (tenant_id, canal_id, inicio);

create table sessoes_reproducao (
    id                  uuid primary key,
    tenant_id           uuid         not null references tenants (id),
    usuario_id          uuid         not null references usuarios (id),
    perfil_id           uuid,
    titulo_id           uuid,
    dispositivo_id      varchar(128) not null,
    aberta_em           timestamptz  not null,
    ultimo_sinal        timestamptz  not null,
    fechada_em          timestamptz,
    posicao_segundos    bigint       not null default 0
);

-- Contagem de telas simultaneas: e a consulta mais quente do sistema, roda a
-- cada play. Indice parcial so com sessao aberta mantem a arvore pequena.
create index sessoes_abertas on sessoes_reproducao (tenant_id, usuario_id, ultimo_sinal)
    where fechada_em is null;

create table auditoria (
    id                  uuid primary key,
    tenant_id           uuid,
    autor_id            uuid,
    autor_descricao     varchar(160),
    acao                varchar(60)  not null,
    recurso_tipo        varchar(40),
    recurso_id          varchar(64),
    endereco_ip         varchar(64),
    detalhes            jsonb        not null default '{}',
    ocorrido_em         timestamptz  not null
);

create index auditoria_por_tenant_e_data on auditoria (tenant_id, ocorrido_em desc);
create index auditoria_por_acao on auditoria (tenant_id, acao, ocorrido_em desc);
