package br.com.outorga.infrastructure.persistence;

import br.com.outorga.application.ports.Repositorios;
import br.com.outorga.domain.billing.Assinatura;
import br.com.outorga.domain.billing.Cupom;
import br.com.outorga.domain.billing.Dinheiro;
import br.com.outorga.domain.billing.EventoDaAssinatura;
import br.com.outorga.domain.billing.Periodicidade;
import br.com.outorga.domain.billing.Plano;
import br.com.outorga.domain.billing.Qualidade;
import br.com.outorga.domain.billing.StatusDaAssinatura;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistencia de plano, cupom e assinatura. */
public final class PersistenciaComercial {

    private PersistenciaComercial() {}

    private static Timestamp ts(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }

    @Repository
    public static class DePlano implements Repositorios.DePlano {

        private static final RowMapper<Plano> MAPA = (rs, i) -> Plano.reconstituir(
                Colunas.uuid(rs, "id"),
                Colunas.uuid(rs, "tenant_id"),
                rs.getString("nome"),
                rs.getString("descricao"),
                new Dinheiro(rs.getLong("preco_centavos"), rs.getString("moeda")),
                Periodicidade.valueOf(rs.getString("periodicidade")),
                rs.getInt("telas_simultaneas"),
                Qualidade.valueOf(rs.getString("qualidade_maxima")),
                rs.getInt("dias_de_teste"),
                rs.getBoolean("ativo"));

        private final JdbcClient jdbc;

        public DePlano(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public Optional<Plano> porId(UUID tenantId, UUID id) {
            return jdbc.sql("select * from planos where tenant_id = :tenant and id = :id")
                    .param("tenant", tenantId).param("id", id).query(MAPA).optional();
        }

        @Override
        public List<Plano> ativosDoTenant(UUID tenantId) {
            return jdbc.sql("""
                    select * from planos where tenant_id = :tenant and ativo
                    order by preco_centavos
                    """)
                    .param("tenant", tenantId).query(MAPA).list();
        }

        @Override
        public Plano salvar(Plano plano) {
            jdbc.sql("""
                    insert into planos (id, tenant_id, nome, descricao, preco_centavos, moeda,
                                        periodicidade, telas_simultaneas, qualidade_maxima,
                                        dias_de_teste, ativo)
                    values (:id, :tenant, :nome, :descricao, :preco, :moeda, :periodicidade,
                            :telas, :qualidade, :teste, :ativo)
                    on conflict (id) do update set
                        nome = excluded.nome,
                        descricao = excluded.descricao,
                        preco_centavos = excluded.preco_centavos,
                        periodicidade = excluded.periodicidade,
                        telas_simultaneas = excluded.telas_simultaneas,
                        qualidade_maxima = excluded.qualidade_maxima,
                        dias_de_teste = excluded.dias_de_teste,
                        ativo = excluded.ativo
                    """)
                    .param("id", plano.id())
                    .param("tenant", plano.tenantId())
                    .param("nome", plano.nome())
                    .param("descricao", plano.descricao())
                    .param("preco", plano.preco().centavos())
                    .param("moeda", plano.preco().moeda())
                    .param("periodicidade", plano.periodicidade().name())
                    .param("telas", plano.telasSimultaneas())
                    .param("qualidade", plano.qualidadeMaxima().name())
                    .param("teste", plano.diasDeTeste())
                    .param("ativo", plano.ativo())
                    .update();
            return plano;
        }
    }

    @Repository
    public static class DeCupom implements Repositorios.DeCupom {

        private static final RowMapper<Cupom> MAPA = (rs, i) -> Cupom.reconstituir(
                Colunas.uuid(rs, "id"),
                Colunas.uuid(rs, "tenant_id"),
                rs.getString("codigo"),
                rs.getInt("percentual"),
                Colunas.instante(rs, "valido_ate"),
                rs.getInt("usos_maximos"),
                rs.getInt("usos"),
                rs.getBoolean("ativo"));

        private final JdbcClient jdbc;

        public DeCupom(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public Optional<Cupom> porCodigo(UUID tenantId, String codigo) {
            return jdbc.sql("""
                    select * from cupons where tenant_id = :tenant and codigo = upper(:codigo)
                    """)
                    .param("tenant", tenantId).param("codigo", codigo).query(MAPA).optional();
        }

        @Override
        public Cupom salvar(Cupom cupom) {
            jdbc.sql("""
                    insert into cupons (id, tenant_id, codigo, percentual, valido_ate, usos_maximos,
                                        usos, ativo)
                    values (:id, :tenant, :codigo, :percentual, :validoAte, :maximos, :usos, :ativo)
                    on conflict (id) do update set
                        percentual = excluded.percentual,
                        valido_ate = excluded.valido_ate,
                        usos_maximos = excluded.usos_maximos,
                        usos = excluded.usos,
                        ativo = excluded.ativo
                    """)
                    .param("id", cupom.id())
                    .param("tenant", cupom.tenantId())
                    .param("codigo", cupom.codigo())
                    .param("percentual", cupom.percentual())
                    .param("validoAte", ts(cupom.validoAte()))
                    .param("maximos", cupom.usosMaximos())
                    .param("usos", cupom.usos())
                    .param("ativo", cupom.ativo())
                    .update();
            return cupom;
        }
    }

    @Repository
    public static class DeAssinatura implements Repositorios.DeAssinatura {

        private final JdbcClient jdbc;

        public DeAssinatura(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        private final RowMapper<Assinatura> mapa = (rs, i) -> {
            var id = Colunas.uuid(rs, "id");
            return Assinatura.reconstituir(
                    id,
                    Colunas.uuid(rs, "tenant_id"),
                    Colunas.uuid(rs, "usuario_id"),
                    Colunas.uuid(rs, "plano_id"),
                    StatusDaAssinatura.valueOf(rs.getString("status")),
                    Colunas.instante(rs, "iniciada_em"),
                    Colunas.instante(rs, "fim_do_ciclo"),
                    Colunas.instante(rs, "fim_da_carencia"),
                    Colunas.instante(rs, "encerrada_em"),
                    rs.getString("referencia_gateway"),
                    List.of());
        };

        @Override
        public Optional<Assinatura> porId(UUID tenantId, UUID id) {
            return jdbc.sql("select * from assinaturas where tenant_id = :tenant and id = :id")
                    .param("tenant", tenantId).param("id", id).query(mapa).optional();
        }

        /**
         * A conta pode ter histórico de assinaturas encerradas. A que vale e a
         * mais recente que ainda não encerrou; se todas encerraram, devolve a
         * última, porque a tela precisa mostrar o que aconteceu.
         */
        @Override
        public Optional<Assinatura> vigenteDoUsuario(UUID tenantId, UUID usuarioId) {
            return jdbc.sql("""
                    select * from assinaturas
                    where tenant_id = :tenant and usuario_id = :usuario
                    order by (status <> 'ENCERRADA') desc, iniciada_em desc
                    limit 1
                    """)
                    .param("tenant", tenantId).param("usuario", usuarioId).query(mapa).optional();
        }

        @Override
        public Optional<Assinatura> porReferenciaNoGateway(String referencia) {
            return jdbc.sql("select * from assinaturas where referencia_gateway = :referencia")
                    .param("referencia", referencia).query(mapa).optional();
        }

        @Override
        public List<Assinatura> vencendoAte(Instant limite) {
            return jdbc.sql("""
                    select * from assinaturas
                    where status <> 'ENCERRADA'
                      and coalesce(fim_da_carencia, fim_do_ciclo) is not null
                      and coalesce(fim_da_carencia, fim_do_ciclo) <= :limite
                    """)
                    .param("limite", ts(limite)).query(mapa).list();
        }

        @Override
        public Assinatura salvar(Assinatura assinatura) {
            jdbc.sql("""
                    insert into assinaturas (id, tenant_id, usuario_id, plano_id, status, iniciada_em,
                                             fim_do_ciclo, fim_da_carencia, encerrada_em,
                                             referencia_gateway)
                    values (:id, :tenant, :usuario, :plano, :status, :iniciadaEm, :fimCiclo,
                            :fimCarencia, :encerradaEm, :referencia)
                    on conflict (id) do update set
                        plano_id = excluded.plano_id,
                        status = excluded.status,
                        fim_do_ciclo = excluded.fim_do_ciclo,
                        fim_da_carencia = excluded.fim_da_carencia,
                        encerrada_em = excluded.encerrada_em,
                        referencia_gateway = excluded.referencia_gateway
                    """)
                    .param("id", assinatura.id())
                    .param("tenant", assinatura.tenantId())
                    .param("usuario", assinatura.usuarioId())
                    .param("plano", assinatura.planoId())
                    .param("status", assinatura.status().name())
                    .param("iniciadaEm", ts(assinatura.iniciadaEm()))
                    .param("fimCiclo", ts(assinatura.fimDoCicloAtual()))
                    .param("fimCarencia", ts(assinatura.fimDaCarencia()))
                    .param("encerradaEm", ts(assinatura.encerradaEm()))
                    .param("referencia", assinatura.referenciaNoGateway())
                    .update();

            gravarEventos(assinatura);
            return assinatura;
        }

        /**
         * Evento e append-only: nada de update. O conflito por id existe só
         * porque a entidade em memória carrega os eventos já gravados e uma
         * segunda gravação passaria por eles de novo.
         */
        private void gravarEventos(Assinatura assinatura) {
            for (EventoDaAssinatura evento : assinatura.eventos()) {
                jdbc.sql("""
                        insert into assinatura_eventos (id, assinatura_id, tipo, detalhe, ocorrido_em)
                        values (:id, :assinatura, :tipo, :detalhe, :ocorridoEm)
                        on conflict (id) do nothing
                        """)
                        .param("id", evento.id())
                        .param("assinatura", assinatura.id())
                        .param("tipo", evento.tipo().name())
                        .param("detalhe", evento.detalhe())
                        .param("ocorridoEm", ts(evento.ocorridoEm()))
                        .update();
            }
        }
    }
}
