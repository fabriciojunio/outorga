package br.com.outorga.domain.identity;

import java.util.Set;

/**
 * Papeis do sistema. O primeiro e o único que enxerga vários tenants; os
 * demais vivem dentro de um só.
 */
public enum Papel {

    /** Operação do Outorga TV. Abre e fecha cliente. */
    ADMIN_PLATAFORMA,
    /** Dono da conta do cliente. Faz tudo dentro do próprio tenant. */
    DONO,
    /** Cadastra catálogo, licença e canal. Não mexe em plano nem em cobrança. */
    EDITOR,
    /** Atende assinante. Le, não pública. */
    SUPORTE,
    /** Espectador que assina o serviço do cliente. */
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
