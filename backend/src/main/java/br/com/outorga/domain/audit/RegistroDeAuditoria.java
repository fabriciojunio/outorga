package br.com.outorga.domain.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Trilha de auditoria. Guarda quem fez o que, em qual tenant e sobre qual
 * recurso. Nunca guarda senha, token, numero de cartao nem o corpo inteiro de
 * uma requisicao.
 */
public record RegistroDeAuditoria(
        UUID id,
        UUID tenantId,
        UUID autorId,
        String autorDescricao,
        AcaoAuditavel acao,
        String recursoTipo,
        String recursoId,
        String enderecoIp,
        Map<String, String> detalhes,
        Instant ocorridoEm) {

    public RegistroDeAuditoria {
        detalhes = detalhes == null ? Map.of() : Map.copyOf(detalhes);
    }

    public static RegistroDeAuditoria de(UUID tenantId, UUID autorId, String autorDescricao,
                                         AcaoAuditavel acao, String recursoTipo, String recursoId,
                                         String enderecoIp, Map<String, String> detalhes,
                                         Instant agora) {
        return new RegistroDeAuditoria(UUID.randomUUID(), tenantId, autorId, autorDescricao, acao,
                recursoTipo, recursoId, enderecoIp, detalhes, agora);
    }
}
