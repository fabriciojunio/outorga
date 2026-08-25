package br.com.mirante.domain.identity;

import java.util.Set;

/**
 * Papeis do sistema. O primeiro e o unico que enxerga varios tenants; os
 * demais vivem dentro de um so.
 */
public enum Papel {

    /** Operacao do Mirante. Abre e fecha cliente. */
    ADMIN_PLATAFORMA,
    /** Dono da conta do cliente. Faz tudo dentro do proprio tenant. */
    DONO,
    /** Cadastra catalogo, licenca e canal. Nao mexe em plano nem em cobranca. */
    EDITOR,
    /** Atende assinante. Le, nao publica. */
    SUPORTE,
    /** Espectador que assina o servico do cliente. */
    ASSINANTE;

    private static final Set<Papel> DO_PAINEL = Set.of(ADMIN_PLATAFORMA, DONO, EDITOR, SUPORTE);

    public boolean acessaPainel() {
        return DO_PAINEL.contains(this);
    }

    public boolean podePublicarCatalogo() {
        return this == ADMIN_PLATAFORMA || this == DONO || this == EDITOR;
    }

    public boolean podeMexerEmCobranca() {
        return this == ADMIN_PLATAFORMA || this == DONO;
    }
}
