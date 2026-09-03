package br.com.outorga.infrastructure.security;

import br.com.outorga.application.ports.EmissorDeToken;
import br.com.outorga.domain.identity.Papel;
import br.com.outorga.infrastructure.config.ConfiguracaoDaOutorga;
import br.com.outorga.shared.FalhaDeNegocio;
import br.com.outorga.shared.Result;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tokens de acesso e refresh.
 *
 * O acesso e um JWT curto que ninguém consulta no banco: e essa a razão de ser
 * dele. O refresh e longo, rotaciona a cada uso e tem estado no banco, porque
 * um token de 14 dias sem revogacao possível e um problema esperando data.
 *
 * Deteccao de reuso: se um refresh já marcado como usado voltar, a sessão
 * inteira daquele usuário e derrubada. Ou o token vazou, ou o cliente está com
 * defeito; nos dois casos o certo e cortar e pedir login de novo.
 */
@Component
public class EmissorDeTokenJwt implements EmissorDeToken {

    private static final Logger log = LoggerFactory.getLogger(EmissorDeTokenJwt.class);
    private static final String EMISSOR = "outorga";
    private static final String CLAIM_TENANT = "ten";
    private static final String CLAIM_PAPEIS = "pap";
    private static final String CLAIM_TIPO = "typ";
    private static final String TIPO_ACESSO = "acesso";
    private static final String TIPO_REFRESH = "refresh";

    private final Algorithm algoritmo;
    private final JWTVerifier verificador;
    private final ConfiguracaoDaOutorga.Autenticacao config;
    private final JdbcClient jdbc;
    private final Clock relogio;

    public EmissorDeTokenJwt(ConfiguracaoDaOutorga configuracao, JdbcClient jdbc, Clock relogio) {
        this.config = configuracao.autenticacao();
        this.algoritmo = Algorithm.HMAC256(config.segredo());
        this.verificador = JWT.require(algoritmo).withIssuer(EMISSOR).build();
        this.jdbc = jdbc;
        this.relogio = relogio;
    }

    @Override
    public Par emitir(UUID tenantId, UUID usuarioId, Set<Papel> papeis) {
        var agora = relogio.instant();
        var expiraAcesso = agora.plus(config.validadeAcesso());
        var expiraRefresh = agora.plus(config.validadeRefresh());
        var jtiRefresh = UUID.randomUUID();

        var acesso = JWT.create()
                .withIssuer(EMISSOR)
                .withSubject(usuarioId.toString())
                .withClaim(CLAIM_TENANT, tenantId.toString())
                .withClaim(CLAIM_TIPO, TIPO_ACESSO)
                .withClaim(CLAIM_PAPEIS, papeis.stream().map(Enum::name).toList())
                .withIssuedAt(Date.from(agora))
                .withExpiresAt(Date.from(expiraAcesso))
                .withJWTId(UUID.randomUUID().toString())
                .sign(algoritmo);

        var refresh = JWT.create()
                .withIssuer(EMISSOR)
                .withSubject(usuarioId.toString())
                .withClaim(CLAIM_TENANT, tenantId.toString())
                .withClaim(CLAIM_TIPO, TIPO_REFRESH)
                .withClaim(CLAIM_PAPEIS, papeis.stream().map(Enum::name).toList())
                .withIssuedAt(Date.from(agora))
                .withExpiresAt(Date.from(expiraRefresh))
                .withJWTId(jtiRefresh.toString())
                .sign(algoritmo);

        jdbc.sql("""
                insert into refresh_tokens (jti, usuario_id, tenant_id, emitido_em, expira_em)
                values (:jti, :usuario, :tenant, :emitido, :expira)
                """)
                .param("jti", jtiRefresh)
                .param("usuario", usuarioId)
                .param("tenant", tenantId)
                .param("emitido", Timestamp.from(agora))
                .param("expira", Timestamp.from(expiraRefresh))
                .update();

        return new Par(acesso, expiraAcesso, refresh, expiraRefresh);
    }

    @Override
    public Result<Conteudo> validarAcesso(String token) {
        try {
            var decodificado = verificador.verify(token);
            if (!TIPO_ACESSO.equals(decodificado.getClaim(CLAIM_TIPO).asString())) {
                return Result.erro(new FalhaDeNegocio("TOKEN_DE_TIPO_ERRADO",
                        "Use o token de acesso neste cabeçalho"));
            }
            return Result.ok(new Conteudo(
                    UUID.fromString(decodificado.getClaim(CLAIM_TENANT).asString()),
                    UUID.fromString(decodificado.getSubject()),
                    papeisDe(decodificado.getClaim(CLAIM_PAPEIS).asList(String.class)),
                    decodificado.getId()));
        } catch (JWTVerificationException | IllegalArgumentException e) {
            return Result.erro(new FalhaDeNegocio("TOKEN_INVALIDO", "Sessão inválida ou expirada"));
        }
    }

    @Override
    public Result<Par> renovar(String refreshToken) {
        com.auth0.jwt.interfaces.DecodedJWT decodificado;
        try {
            decodificado = verificador.verify(refreshToken);
        } catch (JWTVerificationException e) {
            return Result.erro(new FalhaDeNegocio("TOKEN_INVALIDO", "Sessão inválida ou expirada"));
        }
        if (!TIPO_REFRESH.equals(decodificado.getClaim(CLAIM_TIPO).asString())) {
            return Result.erro(new FalhaDeNegocio("TOKEN_DE_TIPO_ERRADO",
                    "Este endereço espera o token de renovação"));
        }

        var jti = UUID.fromString(decodificado.getId());
        var usuarioId = UUID.fromString(decodificado.getSubject());
        var agora = relogio.instant();

        record Estado(Instant usadoEm, Instant revogadoEm, Instant expiraEm) {
        }
        var estado = jdbc.sql("""
                select usado_em, revogado_em, expira_em from refresh_tokens where jti = :jti
                """)
                .param("jti", jti)
                .query((rs, i) -> new Estado(
                        rs.getTimestamp("usado_em") == null ? null : rs.getTimestamp("usado_em").toInstant(),
                        rs.getTimestamp("revogado_em") == null ? null : rs.getTimestamp("revogado_em").toInstant(),
                        rs.getTimestamp("expira_em").toInstant()))
                .optional();

        if (estado.isEmpty()) {
            return Result.erro(new FalhaDeNegocio("TOKEN_INVALIDO", "Sessão inválida ou expirada"));
        }
        if (estado.get().revogadoEm() != null || !agora.isBefore(estado.get().expiraEm())) {
            return Result.erro(new FalhaDeNegocio("TOKEN_INVALIDO", "Sessão inválida ou expirada"));
        }
        if (estado.get().usadoEm() != null) {
            log.warn("Refresh reaproveitado para o usuário {}. Derrubando todas as sessões dele.",
                    usuarioId);
            revogar(usuarioId);
            return Result.erro(new FalhaDeNegocio("TOKEN_REAPROVEITADO",
                    "Sessão encerrada por segurança. Entre de novo"));
        }

        jdbc.sql("update refresh_tokens set usado_em = :agora where jti = :jti")
                .param("agora", Timestamp.from(agora)).param("jti", jti).update();

        var tenantId = UUID.fromString(decodificado.getClaim(CLAIM_TENANT).asString());
        var papeis = papeisDe(decodificado.getClaim(CLAIM_PAPEIS).asList(String.class));
        return Result.ok(emitir(tenantId, usuarioId, papeis));
    }

    @Override
    public void revogar(UUID usuarioId) {
        jdbc.sql("""
                update refresh_tokens set revogado_em = :agora
                where usuario_id = :usuario and revogado_em is null
                """)
                .param("agora", Timestamp.from(relogio.instant()))
                .param("usuario", usuarioId)
                .update();
    }

    /** Limpeza do que já venceu, para a tabela não crescer para sempre. */
    public int limparVencidos() {
        return jdbc.sql("delete from refresh_tokens where expira_em < :agora")
                .param("agora", Timestamp.from(relogio.instant()))
                .update();
    }

    private static Set<Papel> papeisDe(java.util.List<String> nomes) {
        if (nomes == null || nomes.isEmpty()) {
            return Set.of();
        }
        return nomes.stream().map(Papel::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Papel.class)));
    }
}
