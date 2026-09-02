package br.com.outorga.application.ports;

import br.com.outorga.domain.identity.Papel;
import br.com.outorga.shared.Result;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Emissao e conferencia dos tokens de sessao. O refresh e rotativo: cada uso
 * queima o anterior, para que um token vazado tenha janela curta e o reuso
 * apareca.
 */
public interface EmissorDeToken {

    Par emitir(UUID tenantId, UUID usuarioId, Set<Papel> papeis);

    Result<Conteudo> validarAcesso(String token);

    Result<Par> renovar(String refreshToken);

    void revogar(UUID usuarioId);

    record Par(String acesso, Instant acessoExpiraEm, String refresh, Instant refreshExpiraEm) {
    }

    record Conteudo(UUID tenantId, UUID usuarioId, Set<Papel> papeis, String jti) {
    }
}
