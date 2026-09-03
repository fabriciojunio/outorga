package br.com.outorga.domain.catalog;

/**
 * Classificação indicativa brasileira. A ordem do enum importa: e ela que
 * define o que o controle parental libera.
 */
public enum ClassificacaoIndicativa {

    LIVRE(0),
    DEZ_ANOS(10),
    DOZE_ANOS(12),
    QUATORZE_ANOS(14),
    DEZESSEIS_ANOS(16),
    DEZOITO_ANOS(18);

    private final int idadeMinima;

    ClassificacaoIndicativa(int idadeMinima) {
        this.idadeMinima = idadeMinima;
    }

    public int idadeMinima() {
        return idadeMinima;
    }

    /** Verdadeiro quando um perfil limitado a {@code teto} pode ver este conteúdo. */
    public boolean liberadaPara(ClassificacaoIndicativa teto) {
        return this.idadeMinima <= teto.idadeMinima;
    }

    public String rotulo() {
        return this == LIVRE ? "L" : String.valueOf(idadeMinima);
    }
}
