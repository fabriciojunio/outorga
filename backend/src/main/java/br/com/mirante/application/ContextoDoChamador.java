package br.com.mirante.application;

import br.com.mirante.domain.identity.Papel;

import java.util.Set;
import java.util.UUID;

/**
 * Quem esta pedindo. Todo caso de uso recebe isto explicitamente em vez de ir
 * buscar em variavel de thread: um caso de uso chamado por job, por fila ou
 * por teste continua tendo autor conhecido.
 */
public record ContextoDoChamador(UUID tenantId, UUID usuarioId, String descricao,
                                 Set<Papel> papeis, String enderecoIp) {

    public static ContextoDoChamador doSistema(UUID tenantId) {
        return new ContextoDoChamador(tenantId, null, "sistema", Set.of(), null);
    }

    public boolean tem(Papel papel) {
        return papeis.contains(papel);
    }

    public boolean podePublicarCatalogo() {
        return papeis.stream().anyMatch(Papel::podePublicarCatalogo);
    }

    public boolean podeMexerEmCobranca() {
        return papeis.stream().anyMatch(Papel::podeMexerEmCobranca);
    }
}
