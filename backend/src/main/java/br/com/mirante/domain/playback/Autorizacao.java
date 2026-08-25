package br.com.mirante.domain.playback;

import br.com.mirante.domain.billing.Qualidade;

import java.time.Instant;
import java.util.UUID;

/**
 * Decisao positiva de reproducao. Nao carrega URL: quem transforma a
 * referencia em endereco assinado e a infraestrutura, fora do dominio.
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
