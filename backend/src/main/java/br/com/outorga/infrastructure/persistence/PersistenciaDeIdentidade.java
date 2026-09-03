package br.com.outorga.infrastructure.persistence;

import br.com.outorga.application.ports.Repositorios;
import br.com.outorga.domain.catalog.ClassificacaoIndicativa;
import br.com.outorga.domain.identity.Dispositivo;
import br.com.outorga.domain.identity.Email;
import br.com.outorga.domain.identity.Papel;
import br.com.outorga.domain.identity.Perfil;
import br.com.outorga.domain.identity.Usuario;
import br.com.outorga.domain.rights.TipoDeDispositivo;
import br.com.outorga.domain.tenant.Marca;
import br.com.outorga.domain.tenant.StatusDoTenant;
import br.com.outorga.domain.tenant.Tenant;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persistencia de cliente, conta, perfil e aparelho.
 *
 * O gravar e sempre upsert por id: a entidade já nasce com identidade no
 * dominio, então a camada de dados não precisa saber se e a primeira vez.
 */
public final class PersistenciaDeIdentidade {

    private PersistenciaDeIdentidade() {}

    @Repository
    public static class DeTenant implements Repositorios.DeTenant {

        private static final RowMapper<Tenant> MAPA = (rs, linha) -> Tenant.reconstituir(
                Colunas.uuid(rs, "id"),
                rs.getString("slug"),
                rs.getString("nome"),
                rs.getString("documento"),
                rs.getString("dominio_proprio"),
                new Marca(rs.getString("marca_nome"), rs.getString("marca_logo_uri"),
                        rs.getString("marca_cor_primaria"), rs.getString("marca_cor_fundo")),
                StatusDoTenant.valueOf(rs.getString("status")),
                Colunas.instante(rs, "criado_em"),
                Colunas.instante(rs, "fim_do_teste"),
                rs.getString("motivo_suspensao"));

        private final JdbcClient jdbc;

        public DeTenant(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public Optional<Tenant> porId(UUID id) {
            return jdbc.sql("select * from tenants where id = :id")
                    .param("id", id).query(MAPA).optional();
        }

        @Override
        public Optional<Tenant> porSlug(String slug) {
            return jdbc.sql("select * from tenants where slug = :slug")
                    .param("slug", slug).query(MAPA).optional();
        }

        @Override
        public Optional<Tenant> porDominio(String dominio) {
            return jdbc.sql("select * from tenants where dominio_proprio = :dominio")
                    .param("dominio", dominio).query(MAPA).optional();
        }

        @Override
        public List<Tenant> todos() {
            return jdbc.sql("select * from tenants order by nome").query(MAPA).list();
        }

        @Override
        public Tenant salvar(Tenant tenant) {
            jdbc.sql("""
                    insert into tenants (id, slug, nome, documento, dominio_proprio, marca_nome,
                                         marca_logo_uri, marca_cor_primaria, marca_cor_fundo,
                                         status, motivo_suspensao, criado_em, fim_do_teste)
                    values (:id, :slug, :nome, :documento, :dominio, :marcaNome, :marcaLogo,
                            :corPrimaria, :corFundo, :status, :motivo, :criadoEm, :fimDoTeste)
                    on conflict (id) do update set
                        nome = excluded.nome,
                        documento = excluded.documento,
                        dominio_proprio = excluded.dominio_proprio,
                        marca_nome = excluded.marca_nome,
                        marca_logo_uri = excluded.marca_logo_uri,
                        marca_cor_primaria = excluded.marca_cor_primaria,
                        marca_cor_fundo = excluded.marca_cor_fundo,
                        status = excluded.status,
                        motivo_suspensao = excluded.motivo_suspensao,
                        fim_do_teste = excluded.fim_do_teste
                    """)
                    .param("id", tenant.id())
                    .param("slug", tenant.slug())
                    .param("nome", tenant.nome())
                    .param("documento", tenant.documento())
                    .param("dominio", tenant.dominioProprio())
                    .param("marcaNome", tenant.marca().nomeExibido())
                    .param("marcaLogo", tenant.marca().logoUri())
                    .param("corPrimaria", tenant.marca().corPrimaria())
                    .param("corFundo", tenant.marca().corDeFundo())
                    .param("status", tenant.status().name())
                    .param("motivo", tenant.motivoDaSuspensao())
                    .param("criadoEm", Timestamp.from(tenant.criadoEm()))
                    .param("fimDoTeste", tenant.fimDoTeste() == null
                            ? null : Timestamp.from(tenant.fimDoTeste()))
                    .update();
            return tenant;
        }
    }

    @Repository
    public static class DeUsuario implements Repositorios.DeUsuario {

        private static final RowMapper<Usuario> MAPA = (rs, linha) -> Usuario.reconstituir(
                Colunas.uuid(rs, "id"),
                Colunas.uuid(rs, "tenant_id"),
                new Email(rs.getString("email")),
                rs.getString("senha_hash"),
                rs.getString("nome"),
                Colunas.conjunto(rs, "papeis").stream().map(Papel::valueOf)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(Papel.class))),
                rs.getBoolean("ativo"),
                rs.getInt("tentativas_seguidas"),
                Colunas.instante(rs, "bloqueado_ate"),
                Colunas.instante(rs, "ultimo_acesso"),
                Colunas.instante(rs, "criado_em"),
                Colunas.instante(rs, "anonimizado_em"));

        private final JdbcClient jdbc;

        public DeUsuario(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public Optional<Usuario> porId(UUID tenantId, UUID id) {
            return jdbc.sql("select * from usuarios where tenant_id = :tenant and id = :id")
                    .param("tenant", tenantId).param("id", id).query(MAPA).optional();
        }

        @Override
        public Optional<Usuario> porEmail(UUID tenantId, Email email) {
            return jdbc.sql("select * from usuarios where tenant_id = :tenant and email = :email")
                    .param("tenant", tenantId).param("email", email.valor()).query(MAPA).optional();
        }

        @Override
        public List<Usuario> doTenant(UUID tenantId) {
            return jdbc.sql("select * from usuarios where tenant_id = :tenant order by nome")
                    .param("tenant", tenantId).query(MAPA).list();
        }

        @Override
        public boolean existeEmail(UUID tenantId, Email email) {
            return jdbc.sql("""
                    select exists (select 1 from usuarios
                                   where tenant_id = :tenant and email = :email)
                    """)
                    .param("tenant", tenantId).param("email", email.valor())
                    .query(Boolean.class).single();
        }

        @Override
        public Usuario salvar(Usuario usuario) {
            jdbc.sql("""
                    insert into usuarios (id, tenant_id, email, senha_hash, nome, papeis, ativo,
                                          tentativas_seguidas, bloqueado_ate, ultimo_acesso,
                                          criado_em, anonimizado_em)
                    values (:id, :tenant, :email, :senha, :nome, cast(:papeis as text[]), :ativo,
                            :tentativas, :bloqueadoAte, :ultimoAcesso, :criadoEm, :anonimizadoEm)
                    on conflict (id) do update set
                        email = excluded.email,
                        senha_hash = excluded.senha_hash,
                        nome = excluded.nome,
                        papeis = excluded.papeis,
                        ativo = excluded.ativo,
                        tentativas_seguidas = excluded.tentativas_seguidas,
                        bloqueado_ate = excluded.bloqueado_ate,
                        ultimo_acesso = excluded.ultimo_acesso,
                        anonimizado_em = excluded.anonimizado_em
                    """)
                    .param("id", usuario.id())
                    .param("tenant", usuario.tenantId())
                    .param("email", usuario.email().valor())
                    .param("senha", usuario.senhaHash())
                    .param("nome", usuario.nome())
                    .param("papeis", Colunas.literalDeArranjo(usuario.papeis()))
                    .param("ativo", usuario.ativo())
                    .param("tentativas", usuario.tentativasSeguidas())
                    .param("bloqueadoAte", usuario.bloqueadoAte() == null
                            ? null : Timestamp.from(usuario.bloqueadoAte()))
                    .param("ultimoAcesso", usuario.ultimoAcesso() == null
                            ? null : Timestamp.from(usuario.ultimoAcesso()))
                    .param("criadoEm", Timestamp.from(usuario.criadoEm()))
                    .param("anonimizadoEm", usuario.anonimizadoEm() == null
                            ? null : Timestamp.from(usuario.anonimizadoEm()))
                    .update();
            return usuario;
        }
    }

    @Repository
    public static class DePerfil implements Repositorios.DePerfil {

        private static final RowMapper<Perfil> MAPA = (rs, linha) -> Perfil.reconstituir(
                Colunas.uuid(rs, "id"),
                Colunas.uuid(rs, "usuario_id"),
                rs.getString("nome"),
                ClassificacaoIndicativa.valueOf(rs.getString("teto_classificacao")),
                rs.getString("pin_hash"),
                rs.getBoolean("infantil"),
                rs.getString("avatar"));

        private final JdbcClient jdbc;

        public DePerfil(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public Optional<Perfil> porId(UUID id) {
            return jdbc.sql("select * from perfis where id = :id")
                    .param("id", id).query(MAPA).optional();
        }

        @Override
        public List<Perfil> doUsuario(UUID usuarioId) {
            return jdbc.sql("select * from perfis where usuario_id = :usuario order by nome")
                    .param("usuario", usuarioId).query(MAPA).list();
        }

        @Override
        public int quantidadeDoUsuario(UUID usuarioId) {
            return jdbc.sql("select count(*) from perfis where usuario_id = :usuario")
                    .param("usuario", usuarioId).query(Integer.class).single();
        }

        @Override
        public Perfil salvar(Perfil perfil) {
            jdbc.sql("""
                    insert into perfis (id, usuario_id, nome, teto_classificacao, pin_hash,
                                        infantil, avatar)
                    values (:id, :usuario, :nome, :teto, :pin, :infantil, :avatar)
                    on conflict (id) do update set
                        nome = excluded.nome,
                        teto_classificacao = excluded.teto_classificacao,
                        pin_hash = excluded.pin_hash,
                        avatar = excluded.avatar
                    """)
                    .param("id", perfil.id())
                    .param("usuario", perfil.usuarioId())
                    .param("nome", perfil.nome())
                    .param("teto", perfil.tetoDeClassificacao().name())
                    .param("pin", perfil.pinHash())
                    .param("infantil", perfil.infantil())
                    .param("avatar", perfil.avatar())
                    .update();
            return perfil;
        }

        @Override
        public void remover(UUID id) {
            jdbc.sql("delete from perfis where id = :id").param("id", id).update();
        }
    }

    @Repository
    public static class DeDispositivo implements Repositorios.DeDispositivo {

        private static final RowMapper<Dispositivo> MAPA = (rs, linha) -> Dispositivo.reconstituir(
                Colunas.uuid(rs, "id"),
                Colunas.uuid(rs, "usuario_id"),
                rs.getString("identificador"),
                TipoDeDispositivo.valueOf(rs.getString("tipo")),
                rs.getString("apelido"),
                Colunas.instante(rs, "registrado_em"),
                Colunas.instante(rs, "ultimo_uso"));

        private final JdbcClient jdbc;

        public DeDispositivo(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public Optional<Dispositivo> porIdentificador(UUID usuarioId, String identificador) {
            return jdbc.sql("""
                    select * from dispositivos
                    where usuario_id = :usuario and identificador = :identificador
                    """)
                    .param("usuario", usuarioId).param("identificador", identificador)
                    .query(MAPA).optional();
        }

        @Override
        public List<Dispositivo> doUsuario(UUID usuarioId) {
            return jdbc.sql("""
                    select * from dispositivos where usuario_id = :usuario
                    order by ultimo_uso desc
                    """)
                    .param("usuario", usuarioId).query(MAPA).list();
        }

        @Override
        public Dispositivo salvar(Dispositivo dispositivo) {
            jdbc.sql("""
                    insert into dispositivos (id, usuario_id, identificador, tipo, apelido,
                                              registrado_em, ultimo_uso)
                    values (:id, :usuario, :identificador, :tipo, :apelido, :registradoEm, :ultimoUso)
                    on conflict (usuario_id, identificador) do update set
                        apelido = excluded.apelido,
                        ultimo_uso = excluded.ultimo_uso
                    """)
                    .param("id", dispositivo.id())
                    .param("usuario", dispositivo.usuarioId())
                    .param("identificador", dispositivo.identificador())
                    .param("tipo", dispositivo.tipo().name())
                    .param("apelido", dispositivo.apelido())
                    .param("registradoEm", Timestamp.from(dispositivo.registradoEm()))
                    .param("ultimoUso", Timestamp.from(dispositivo.ultimoUso()))
                    .update();
            return dispositivo;
        }

        @Override
        public void remover(UUID id) {
            jdbc.sql("delete from dispositivos where id = :id").param("id", id).update();
        }
    }
}
