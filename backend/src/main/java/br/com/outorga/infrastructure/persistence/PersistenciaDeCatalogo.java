package br.com.outorga.infrastructure.persistence;

import br.com.outorga.application.ports.Repositorios;
import br.com.outorga.domain.catalog.ClassificacaoIndicativa;
import br.com.outorga.domain.catalog.Episodio;
import br.com.outorga.domain.catalog.StatusDePublicacao;
import br.com.outorga.domain.catalog.Temporada;
import br.com.outorga.domain.catalog.TipoDeTitulo;
import br.com.outorga.domain.catalog.Titulo;
import br.com.outorga.domain.rights.JanelaDeLicenca;
import br.com.outorga.domain.rights.Licenca;
import br.com.outorga.domain.rights.StatusDaLicenca;
import br.com.outorga.domain.rights.Territorio;
import br.com.outorga.domain.rights.TipoDeDispositivo;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persistencia de catalogo e de licenca.
 *
 * Serie e agregado: titulo, temporada e episodio sao lidos e gravados juntos.
 * O custo disso e uma consulta a mais por titulo; o beneficio e que nunca
 * existe temporada orfa nem episodio de serie que ninguem sabe de quem e.
 */
public final class PersistenciaDeCatalogo {

    private PersistenciaDeCatalogo() {}

    @Repository
    public static class DeLicenca implements Repositorios.DeLicenca {

        private static final RowMapper<Licenca> MAPA = (rs, linha) -> Licenca.reconstituir(
                Colunas.uuid(rs, "id"),
                Colunas.uuid(rs, "tenant_id"),
                rs.getString("titular"),
                rs.getString("referencia_contrato"),
                Colunas.conjunto(rs, "territorios").stream().map(Territorio::new)
                        .collect(Collectors.toCollection(java.util.LinkedHashSet::new)),
                new JanelaDeLicenca(Colunas.instante(rs, "janela_inicio"),
                        Colunas.instante(rs, "janela_fim")),
                Colunas.conjunto(rs, "dispositivos").stream().map(TipoDeDispositivo::valueOf)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(TipoDeDispositivo.class))),
                rs.getString("comprovacao_uri"),
                StatusDaLicenca.valueOf(rs.getString("status")),
                rs.getString("observacao"));

        private final JdbcClient jdbc;

        public DeLicenca(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public Optional<Licenca> porId(UUID tenantId, UUID id) {
            return jdbc.sql("select * from licencas where tenant_id = :tenant and id = :id")
                    .param("tenant", tenantId).param("id", id).query(MAPA).optional();
        }

        @Override
        public List<Licenca> doTenant(UUID tenantId) {
            return jdbc.sql("""
                    select * from licencas where tenant_id = :tenant
                    order by janela_fim nulls last, titular
                    """)
                    .param("tenant", tenantId).query(MAPA).list();
        }

        @Override
        public List<Licenca> vencendoAte(Instant limite) {
            return jdbc.sql("""
                    select * from licencas
                    where status = 'VIGENTE' and janela_fim is not null and janela_fim <= :limite
                    order by janela_fim
                    """)
                    .param("limite", Timestamp.from(limite)).query(MAPA).list();
        }

        @Override
        public Licenca salvar(Licenca licenca) {
            jdbc.sql("""
                    insert into licencas (id, tenant_id, titular, referencia_contrato, territorios,
                                          dispositivos, janela_inicio, janela_fim, comprovacao_uri,
                                          status, observacao)
                    values (:id, :tenant, :titular, :contrato, cast(:territorios as text[]),
                            cast(:dispositivos as text[]), :inicio, :fim, :comprovacao, :status,
                            :observacao)
                    on conflict (id) do update set
                        titular = excluded.titular,
                        referencia_contrato = excluded.referencia_contrato,
                        territorios = excluded.territorios,
                        dispositivos = excluded.dispositivos,
                        janela_inicio = excluded.janela_inicio,
                        janela_fim = excluded.janela_fim,
                        comprovacao_uri = excluded.comprovacao_uri,
                        status = excluded.status,
                        observacao = excluded.observacao
                    """)
                    .param("id", licenca.id())
                    .param("tenant", licenca.tenantId())
                    .param("titular", licenca.titular())
                    .param("contrato", licenca.referenciaDoContrato())
                    .param("territorios", Colunas.literalDeArranjo(
                            licenca.territorios().stream().map(Territorio::codigo).toList()))
                    .param("dispositivos", Colunas.literalDeArranjo(licenca.dispositivosAutorizados()))
                    .param("inicio", Timestamp.from(licenca.janela().inicio()))
                    .param("fim", licenca.janela().fim() == null
                            ? null : Timestamp.from(licenca.janela().fim()))
                    .param("comprovacao", licenca.comprovacaoUri())
                    .param("status", licenca.status().name())
                    .param("observacao", licenca.observacao())
                    .update();
            return licenca;
        }
    }

    @Repository
    public static class DeTitulo implements Repositorios.DeTitulo {

        private final JdbcClient jdbc;

        public DeTitulo(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        /** Linha crua do titulo, sem as temporadas. */
        private record Linha(UUID id, UUID tenantId, TipoDeTitulo tipo, String nome, String sinopse,
                             Integer ano, ClassificacaoIndicativa classificacao,
                             java.time.Duration duracao, String referenciaVideo, String capa,
                             java.util.Set<String> generos, UUID licencaId, StatusDePublicacao status,
                             Instant publicadoEm, String motivo) {
        }

        private static final RowMapper<Linha> MAPA = (rs, i) -> new Linha(
                Colunas.uuid(rs, "id"),
                Colunas.uuid(rs, "tenant_id"),
                TipoDeTitulo.valueOf(rs.getString("tipo")),
                rs.getString("nome"),
                rs.getString("sinopse"),
                Colunas.inteiroOuNulo(rs, "ano_producao"),
                ClassificacaoIndicativa.valueOf(rs.getString("classificacao")),
                Colunas.duracao(rs, "duracao_segundos"),
                rs.getString("referencia_video"),
                rs.getString("capa_uri"),
                Colunas.conjunto(rs, "generos"),
                Colunas.uuid(rs, "licenca_id"),
                StatusDePublicacao.valueOf(rs.getString("status")),
                Colunas.instante(rs, "publicado_em"),
                rs.getString("motivo_bloqueio"));

        @Override
        public Optional<Titulo> porId(UUID tenantId, UUID id) {
            return jdbc.sql("select * from titulos where tenant_id = :tenant and id = :id")
                    .param("tenant", tenantId).param("id", id)
                    .query(MAPA).optional()
                    .map(this::comTemporadas);
        }

        @Override
        public List<Titulo> publicados(UUID tenantId, int pagina, int tamanho) {
            var linhas = jdbc.sql("""
                    select * from titulos
                    where tenant_id = :tenant and status = 'PUBLICADO'
                    order by publicado_em desc nulls last, nome
                    limit :limite offset :salto
                    """)
                    .param("tenant", tenantId)
                    .param("limite", tamanho)
                    .param("salto", (long) pagina * tamanho)
                    .query(MAPA).list();
            return montar(linhas);
        }

        @Override
        public List<Titulo> buscar(UUID tenantId, String termo, int limite) {
            var linhas = jdbc.sql("""
                    select * from titulos
                    where tenant_id = :tenant and status = 'PUBLICADO'
                      and sem_acento(nome) like sem_acento(:termo)
                    order by nome
                    limit :limite
                    """)
                    .param("tenant", tenantId)
                    .param("termo", "%" + termo + "%")
                    .param("limite", limite)
                    .query(MAPA).list();
            return montar(linhas);
        }

        @Override
        public List<Titulo> porLicenca(UUID tenantId, UUID licencaId) {
            var linhas = jdbc.sql("""
                    select * from titulos where tenant_id = :tenant and licenca_id = :licenca
                    """)
                    .param("tenant", tenantId).param("licenca", licencaId)
                    .query(MAPA).list();
            return montar(linhas);
        }

        @Override
        public List<Titulo> sujeitosARevisaoDeDireitos(UUID tenantId) {
            var linhas = jdbc.sql("""
                    select * from titulos
                    where tenant_id = :tenant
                      and status in ('PUBLICADO', 'BLOQUEADO_POR_DIREITO')
                    """)
                    .param("tenant", tenantId).query(MAPA).list();
            return montar(linhas);
        }

        @Override
        public Titulo salvar(Titulo titulo) {
            jdbc.sql("""
                    insert into titulos (id, tenant_id, tipo, nome, sinopse, ano_producao,
                                         classificacao, duracao_segundos, referencia_video, capa_uri,
                                         generos, licenca_id, status, publicado_em, motivo_bloqueio)
                    values (:id, :tenant, :tipo, :nome, :sinopse, :ano, :classificacao, :duracao,
                            :video, :capa, cast(:generos as text[]), :licenca, :status, :publicadoEm,
                            :motivo)
                    on conflict (id) do update set
                        nome = excluded.nome,
                        sinopse = excluded.sinopse,
                        ano_producao = excluded.ano_producao,
                        classificacao = excluded.classificacao,
                        duracao_segundos = excluded.duracao_segundos,
                        referencia_video = excluded.referencia_video,
                        capa_uri = excluded.capa_uri,
                        generos = excluded.generos,
                        licenca_id = excluded.licenca_id,
                        status = excluded.status,
                        publicado_em = excluded.publicado_em,
                        motivo_bloqueio = excluded.motivo_bloqueio
                    """)
                    .param("id", titulo.id())
                    .param("tenant", titulo.tenantId())
                    .param("tipo", titulo.tipo().name())
                    .param("nome", titulo.nome())
                    .param("sinopse", titulo.sinopse())
                    .param("ano", titulo.anoDeProducao())
                    .param("classificacao", titulo.classificacao().name())
                    .param("duracao", Colunas.segundos(titulo.duracao()))
                    .param("video", titulo.referenciaDoVideo())
                    .param("capa", titulo.capaUri())
                    .param("generos", Colunas.literalDeArranjo(titulo.generos()))
                    .param("licenca", titulo.licencaId())
                    .param("status", titulo.status().name())
                    .param("publicadoEm", titulo.publicadoEm() == null
                            ? null : Timestamp.from(titulo.publicadoEm()))
                    .param("motivo", titulo.motivoDoBloqueio())
                    .update();

            gravarTemporadas(titulo);
            return titulo;
        }

        private void gravarTemporadas(Titulo titulo) {
            for (var temporada : titulo.temporadas()) {
                jdbc.sql("""
                        insert into temporadas (id, titulo_id, numero, nome)
                        values (:id, :titulo, :numero, :nome)
                        on conflict (titulo_id, numero) do update set nome = excluded.nome
                        """)
                        .param("id", temporada.id())
                        .param("titulo", titulo.id())
                        .param("numero", temporada.numero())
                        .param("nome", temporada.titulo())
                        .update();

                for (var episodio : temporada.episodios()) {
                    jdbc.sql("""
                            insert into episodios (id, temporada_id, numero, nome, sinopse,
                                                   duracao_segundos, referencia_video)
                            values (:id, :temporada, :numero, :nome, :sinopse, :duracao, :video)
                            on conflict (temporada_id, numero) do update set
                                nome = excluded.nome,
                                sinopse = excluded.sinopse,
                                duracao_segundos = excluded.duracao_segundos,
                                referencia_video = excluded.referencia_video
                            """)
                            .param("id", episodio.id())
                            .param("temporada", temporada.id())
                            .param("numero", episodio.numero())
                            .param("nome", episodio.titulo())
                            .param("sinopse", episodio.sinopse())
                            .param("duracao", episodio.duracao().toSeconds())
                            .param("video", episodio.referenciaDoVideo())
                            .update();
                }
            }
        }

        private List<Titulo> montar(List<Linha> linhas) {
            if (linhas.isEmpty()) {
                return List.of();
            }
            var series = linhas.stream().filter(l -> l.tipo() == TipoDeTitulo.SERIE)
                    .map(Linha::id).toList();
            Map<UUID, List<Temporada>> porTitulo = series.isEmpty()
                    ? Map.of()
                    : carregarTemporadas(series);
            return linhas.stream()
                    .map(l -> reconstituir(l, porTitulo.getOrDefault(l.id(), List.of())))
                    .toList();
        }

        private Titulo comTemporadas(Linha linha) {
            var temporadas = linha.tipo() == TipoDeTitulo.SERIE
                    ? carregarTemporadas(List.of(linha.id())).getOrDefault(linha.id(), List.of())
                    : List.<Temporada>of();
            return reconstituir(linha, temporadas);
        }

        /**
         * Uma consulta para todas as temporadas e uma para todos os episodios,
         * independente de quantos titulos vieram. Carregar episodio dentro de
         * laco de temporada e o caminho mais curto para uma tela de catalogo
         * com trezentas consultas.
         */
        private Map<UUID, List<Temporada>> carregarTemporadas(List<UUID> tituloIds) {
            record LinhaTemporada(UUID id, UUID tituloId, int numero, String nome) {
            }
            var temporadas = jdbc.sql("""
                    select id, titulo_id, numero, nome from temporadas
                    where titulo_id = any (cast(:ids as uuid[])) order by numero
                    """)
                    .param("ids", Colunas.literalDeArranjo(tituloIds))
                    .query((rs, i) -> new LinhaTemporada(Colunas.uuid(rs, "id"),
                            Colunas.uuid(rs, "titulo_id"), rs.getInt("numero"), rs.getString("nome")))
                    .list();

            if (temporadas.isEmpty()) {
                return Map.of();
            }

            var episodiosPorTemporada = jdbc.sql("""
                    select * from episodios
                    where temporada_id = any (cast(:ids as uuid[])) order by numero
                    """)
                    .param("ids", Colunas.literalDeArranjo(temporadas.stream()
                            .map(LinhaTemporada::id).toList()))
                    .query((rs, i) -> Map.entry(
                            Colunas.uuid(rs, "temporada_id"),
                            Episodio.reconstituir(Colunas.uuid(rs, "id"), rs.getInt("numero"),
                                    rs.getString("nome"), rs.getString("sinopse"),
                                    Colunas.duracao(rs, "duracao_segundos"),
                                    rs.getString("referencia_video"))))
                    .list().stream()
                    .collect(Collectors.groupingBy(Map.Entry::getKey,
                            Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

            var saida = new java.util.LinkedHashMap<UUID, List<Temporada>>();
            for (var linha : temporadas) {
                saida.computeIfAbsent(linha.tituloId(), k -> new ArrayList<>())
                        .add(Temporada.reconstituir(linha.id(), linha.numero(), linha.nome(),
                                episodiosPorTemporada.getOrDefault(linha.id(), List.of())));
            }
            return saida;
        }

        private static Titulo reconstituir(Linha l, List<Temporada> temporadas) {
            return Titulo.reconstituir(l.id(), l.tenantId(), l.tipo(), l.nome(), l.sinopse(), l.ano(),
                    l.classificacao(), l.duracao(), l.referenciaVideo(), l.capa(), l.generos(),
                    temporadas, l.licencaId(), l.status(), l.publicadoEm(), l.motivo());
        }
    }
}
