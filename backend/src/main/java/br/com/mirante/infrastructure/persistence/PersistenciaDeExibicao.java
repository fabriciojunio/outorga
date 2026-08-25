package br.com.mirante.infrastructure.persistence;

import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.domain.audit.AcaoAuditavel;
import br.com.mirante.domain.audit.RegistroDeAuditoria;
import br.com.mirante.domain.catalog.ClassificacaoIndicativa;
import br.com.mirante.domain.live.CanalAoVivo;
import br.com.mirante.domain.live.ProgramaEpg;
import br.com.mirante.domain.playback.SessaoDeReproducao;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistencia de canal, grade, sessao de reproducao e auditoria. */
public final class PersistenciaDeExibicao {

    private PersistenciaDeExibicao() {}

    private static Timestamp ts(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }

    @Repository
    public static class DeCanal implements Repositorios.DeCanal {

        private static final RowMapper<CanalAoVivo> MAPA = (rs, i) -> CanalAoVivo.reconstituir(
                Colunas.uuid(rs, "id"),
                Colunas.uuid(rs, "tenant_id"),
                rs.getString("nome"),
                rs.getString("logo_uri"),
                rs.getInt("numero"),
                rs.getString("url_fonte"),
                ClassificacaoIndicativa.valueOf(rs.getString("classificacao")),
                Colunas.uuid(rs, "licenca_id"),
                rs.getBoolean("no_ar"),
                rs.getString("motivo_bloqueio"),
                rs.getBoolean("bloqueado_por_direito"));

        private final JdbcClient jdbc;

        public DeCanal(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public Optional<CanalAoVivo> porId(UUID tenantId, UUID id) {
            return jdbc.sql("select * from canais where tenant_id = :tenant and id = :id")
                    .param("tenant", tenantId).param("id", id).query(MAPA).optional();
        }

        @Override
        public List<CanalAoVivo> noAr(UUID tenantId) {
            return jdbc.sql("""
                    select * from canais where tenant_id = :tenant and no_ar order by numero
                    """)
                    .param("tenant", tenantId).query(MAPA).list();
        }

        @Override
        public List<CanalAoVivo> doTenant(UUID tenantId) {
            return jdbc.sql("select * from canais where tenant_id = :tenant order by numero")
                    .param("tenant", tenantId).query(MAPA).list();
        }

        @Override
        public List<CanalAoVivo> porLicenca(UUID tenantId, UUID licencaId) {
            return jdbc.sql("""
                    select * from canais where tenant_id = :tenant and licenca_id = :licenca
                    """)
                    .param("tenant", tenantId).param("licenca", licencaId).query(MAPA).list();
        }

        @Override
        public CanalAoVivo salvar(CanalAoVivo canal) {
            jdbc.sql("""
                    insert into canais (id, tenant_id, nome, logo_uri, numero, url_fonte,
                                        classificacao, licenca_id, no_ar, motivo_bloqueio,
                                        bloqueado_por_direito)
                    values (:id, :tenant, :nome, :logo, :numero, :fonte, :classificacao, :licenca,
                            :noAr, :motivo, :porDireito)
                    on conflict (id) do update set
                        nome = excluded.nome,
                        logo_uri = excluded.logo_uri,
                        numero = excluded.numero,
                        url_fonte = excluded.url_fonte,
                        classificacao = excluded.classificacao,
                        licenca_id = excluded.licenca_id,
                        no_ar = excluded.no_ar,
                        motivo_bloqueio = excluded.motivo_bloqueio,
                        bloqueado_por_direito = excluded.bloqueado_por_direito
                    """)
                    .param("id", canal.id())
                    .param("tenant", canal.tenantId())
                    .param("nome", canal.nome())
                    .param("logo", canal.logoUri())
                    .param("numero", canal.numero())
                    .param("fonte", canal.urlDaFonte())
                    .param("classificacao", canal.classificacao().name())
                    .param("licenca", canal.licencaId())
                    .param("noAr", canal.noAr())
                    .param("motivo", canal.motivoDoBloqueio())
                    .param("porDireito", canal.bloqueadoPorDireito())
                    .update();
            return canal;
        }
    }

    @Repository
    public static class DeEpg implements Repositorios.DeEpg {

        private static final RowMapper<ProgramaEpg> MAPA = (rs, i) -> new ProgramaEpg(
                Colunas.uuid(rs, "id"),
                Colunas.uuid(rs, "tenant_id"),
                Colunas.uuid(rs, "canal_id"),
                rs.getString("titulo"),
                rs.getString("descricao"),
                Colunas.instante(rs, "inicio"),
                Colunas.instante(rs, "fim"),
                ClassificacaoIndicativa.valueOf(rs.getString("classificacao")));

        private final JdbcClient jdbc;

        public DeEpg(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public List<ProgramaEpg> doCanalEntre(UUID tenantId, UUID canalId, Instant de, Instant ate) {
            return jdbc.sql("""
                    select * from epg_programas
                    where tenant_id = :tenant and canal_id = :canal
                      and fim > :de and inicio < :ate
                    order by inicio
                    """)
                    .param("tenant", tenantId).param("canal", canalId)
                    .param("de", ts(de)).param("ate", ts(ate))
                    .query(MAPA).list();
        }

        @Override
        public void salvarTodos(List<ProgramaEpg> programas) {
            for (var programa : programas) {
                jdbc.sql("""
                        insert into epg_programas (id, tenant_id, canal_id, titulo, descricao,
                                                   inicio, fim, classificacao)
                        values (:id, :tenant, :canal, :titulo, :descricao, :inicio, :fim,
                                :classificacao)
                        on conflict (id) do update set
                            titulo = excluded.titulo,
                            descricao = excluded.descricao,
                            inicio = excluded.inicio,
                            fim = excluded.fim,
                            classificacao = excluded.classificacao
                        """)
                        .param("id", programa.id())
                        .param("tenant", programa.tenantId())
                        .param("canal", programa.canalId())
                        .param("titulo", programa.titulo())
                        .param("descricao", programa.descricao())
                        .param("inicio", ts(programa.inicio()))
                        .param("fim", ts(programa.fim()))
                        .param("classificacao", programa.classificacao().name())
                        .update();
            }
        }
    }

    @Repository
    public static class DeSessao implements Repositorios.DeSessao {

        private static final RowMapper<SessaoDeReproducao> MAPA = (rs, i) ->
                SessaoDeReproducao.reconstituir(
                        Colunas.uuid(rs, "id"),
                        Colunas.uuid(rs, "tenant_id"),
                        Colunas.uuid(rs, "usuario_id"),
                        Colunas.uuid(rs, "perfil_id"),
                        Colunas.uuid(rs, "titulo_id"),
                        rs.getString("dispositivo_id"),
                        Colunas.instante(rs, "aberta_em"),
                        Colunas.instante(rs, "ultimo_sinal"),
                        Colunas.instante(rs, "fechada_em"),
                        rs.getLong("posicao_segundos"));

        private final JdbcClient jdbc;

        public DeSessao(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        /**
         * Conta so o que esta vivo: aberto e com sinal recente. Sessao que
         * ficou sem sinal nao entra na conta mesmo antes da limpeza rodar,
         * senao o espectador que perdeu a rede fica sem assistir ate o job
         * passar.
         */
        @Override
        public int abertasDoUsuario(UUID tenantId, UUID usuarioId, Instant agora) {
            return jdbc.sql("""
                    select count(*) from sessoes_reproducao
                    where tenant_id = :tenant and usuario_id = :usuario
                      and fechada_em is null and ultimo_sinal > :limite
                    """)
                    .param("tenant", tenantId)
                    .param("usuario", usuarioId)
                    .param("limite", ts(agora.minus(SessaoDeReproducao.TOLERANCIA)))
                    .query(Integer.class).single();
        }

        @Override
        public Optional<SessaoDeReproducao> porId(UUID id) {
            return jdbc.sql("select * from sessoes_reproducao where id = :id")
                    .param("id", id).query(MAPA).optional();
        }

        @Override
        public SessaoDeReproducao salvar(SessaoDeReproducao sessao) {
            jdbc.sql("""
                    insert into sessoes_reproducao (id, tenant_id, usuario_id, perfil_id, titulo_id,
                                                    dispositivo_id, aberta_em, ultimo_sinal,
                                                    fechada_em, posicao_segundos)
                    values (:id, :tenant, :usuario, :perfil, :titulo, :dispositivo, :abertaEm,
                            :ultimoSinal, :fechadaEm, :posicao)
                    on conflict (id) do update set
                        ultimo_sinal = excluded.ultimo_sinal,
                        fechada_em = excluded.fechada_em,
                        posicao_segundos = excluded.posicao_segundos
                    """)
                    .param("id", sessao.id())
                    .param("tenant", sessao.tenantId())
                    .param("usuario", sessao.usuarioId())
                    .param("perfil", sessao.perfilId())
                    .param("titulo", sessao.tituloId())
                    .param("dispositivo", sessao.dispositivoId())
                    .param("abertaEm", ts(sessao.abertaEm()))
                    .param("ultimoSinal", ts(sessao.ultimoSinal()))
                    .param("fechadaEm", ts(sessao.fechadaEm()))
                    .param("posicao", sessao.posicaoEmSegundos())
                    .update();
            return sessao;
        }

        @Override
        public int fecharAbandonadas(Instant limite) {
            return jdbc.sql("""
                    update sessoes_reproducao set fechada_em = ultimo_sinal
                    where fechada_em is null and ultimo_sinal <= :limite
                    """)
                    .param("limite", ts(limite)).update();
        }
    }

    @Repository
    public static class DeAuditoria implements Repositorios.DeAuditoria {

        private static final RowMapper<RegistroDeAuditoria> MAPA = (rs, i) -> new RegistroDeAuditoria(
                Colunas.uuid(rs, "id"),
                Colunas.uuid(rs, "tenant_id"),
                Colunas.uuid(rs, "autor_id"),
                rs.getString("autor_descricao"),
                AcaoAuditavel.valueOf(rs.getString("acao")),
                rs.getString("recurso_tipo"),
                rs.getString("recurso_id"),
                rs.getString("endereco_ip"),
                Colunas.jsonParaMapa(rs.getString("detalhes")),
                Colunas.instante(rs, "ocorrido_em"));

        private final JdbcClient jdbc;

        public DeAuditoria(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public void registrar(RegistroDeAuditoria registro) {
            jdbc.sql("""
                    insert into auditoria (id, tenant_id, autor_id, autor_descricao, acao,
                                           recurso_tipo, recurso_id, endereco_ip, detalhes,
                                           ocorrido_em)
                    values (:id, :tenant, :autor, :descricao, :acao, :recursoTipo, :recursoId,
                            :ip, cast(:detalhes as jsonb), :ocorridoEm)
                    """)
                    .param("id", registro.id())
                    .param("tenant", registro.tenantId())
                    .param("autor", registro.autorId())
                    .param("descricao", registro.autorDescricao())
                    .param("acao", registro.acao().name())
                    .param("recursoTipo", registro.recursoTipo())
                    .param("recursoId", registro.recursoId())
                    .param("ip", registro.enderecoIp())
                    .param("detalhes", Colunas.literalDeJson(registro.detalhes()))
                    .param("ocorridoEm", ts(registro.ocorridoEm()))
                    .update();
        }

        @Override
        public List<RegistroDeAuditoria> doTenant(UUID tenantId, Instant de, Instant ate, int limite) {
            return jdbc.sql("""
                    select * from auditoria
                    where tenant_id = :tenant and ocorrido_em between :de and :ate
                    order by ocorrido_em desc
                    limit :limite
                    """)
                    .param("tenant", tenantId).param("de", ts(de)).param("ate", ts(ate))
                    .param("limite", limite)
                    .query(MAPA).list();
        }
    }
}
