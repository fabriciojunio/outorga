package br.com.mirante.domain.billing;

/**
 * Teto de qualidade de imagem por plano. A ordem define o que cada plano
 * libera: um plano em FULL_HD tambem entrega HD e SD.
 */
public enum Qualidade {

    SD(480),
    HD(720),
    FULL_HD(1080),
    ULTRA_HD(2160);

    private final int alturaMaxima;

    Qualidade(int alturaMaxima) {
        this.alturaMaxima = alturaMaxima;
    }

    public int alturaMaxima() {
        return alturaMaxima;
    }

    public boolean cobre(Qualidade pedida) {
        return this.alturaMaxima >= pedida.alturaMaxima;
    }
}
