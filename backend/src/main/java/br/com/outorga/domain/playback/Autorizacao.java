package br.com.outorga.domain.playback;

import br.com.outorga.domain.billing.Qualidade;

import java.time.Instant;
import java.util.UUID;

/**
 * Decisão positiva de reprodução. Não carrega URL: quem transforma a
 * referência em endereço assinado e a infraestrutura, fora do dominio.
 */
public record Autorizacao(
        UUID sessaoId,
        UUID tenantId,
        UUID perfilId,
        UUID tituloId,
        String referenciaDoVideo,
        Qualidade qualidade,
        Instant expiraEm,
        UUID licencaId) {
}
