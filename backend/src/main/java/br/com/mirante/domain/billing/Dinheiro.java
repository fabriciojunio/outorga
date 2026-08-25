package br.com.mirante.domain.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Valor monetario em centavos. Nao existe double em dinheiro neste projeto.
 */
public record Dinheiro(long centavos, String moeda) implements Comparable<Dinheiro> {

    public static final String REAL = "BRL";

    public Dinheiro {
        if (centavos < 0) {
            throw new IllegalArgumentException("valor nao pode ser negativo");
        }
        if (moeda == null || moeda.length() != 3) {
            throw new IllegalArgumentException("moeda em codigo ISO de 3 letras");
        }
        moeda = moeda.toUpperCase(Locale.ROOT);
    }

    public static Dinheiro reais(long centavos) {
        return new Dinheiro(centavos, REAL);
    }

    public static Dinheiro deReais(String valor) {
        var decimal = new BigDecimal(valor).setScale(2, RoundingMode.HALF_UP);
        return new Dinheiro(decimal.movePointRight(2).longValueExact(), REAL);
    }

    public static final Dinheiro ZERO = reais(0);

    public Dinheiro mais(Dinheiro outro) {
        exigirMesmaMoeda(outro);
        return new Dinheiro(centavos + outro.centavos, moeda);
    }

    public Dinheiro menos(Dinheiro outro) {
        exigirMesmaMoeda(outro);
        return new Dinheiro(Math.max(0, centavos - outro.centavos), moeda);
    }

    public Dinheiro vezes(int fator) {
        if (fator < 0) {
            throw new IllegalArgumentException("fator nao pode ser negativo");
        }
        return new Dinheiro(centavos * fator, moeda);
    }

    /** Desconto percentual com arredondamento para o centavo mais proximo. */
    public Dinheiro comDescontoDe(int percentual) {
        if (percentual < 0 || percentual > 100) {
            throw new IllegalArgumentException("percentual entre 0 e 100");
        }
        long desconto = Math.round(centavos * (percentual / 100.0));
        return new Dinheiro(centavos - desconto, moeda);
    }

    public BigDecimal emUnidades() {
        return BigDecimal.valueOf(centavos, 2);
    }

    public String formatado() {
        return String.format(Locale.of("pt", "BR"), "R$ %,.2f", emUnidades());
    }

    private void exigirMesmaMoeda(Dinheiro outro) {
        if (!moeda.equals(outro.moeda)) {
            throw new IllegalArgumentException("moedas diferentes: " + moeda + " e " + outro.moeda);
        }
    }

    @Override
    public int compareTo(Dinheiro outro) {
        exigirMesmaMoeda(outro);
        return Long.compare(centavos, outro.centavos);
    }
}
