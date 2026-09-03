package br.com.outorga.domain.rights;

import java.time.Duration;
import java.time.Instant;

/**
 * Período contratado de exploracao. O fim e opcional: contrato por prazo
 * indeterminado existe, mas fica marcado como tal em vez de virar uma data
 * distante e mentirosa.
 */
public record JanelaDeLicenca(Instant inicio, Instant fim) {

    public JanelaDeLicenca {
        if (inicio == null) {
            throw new IllegalArgumentException("início da janela e obrigatório");
        }
        if (fim != null && !fim.isAfter(inicio)) {
            throw new IllegalArgumentException("fim da janela precisa ser depois do início");
        }
    }

    public static JanelaDeLicenca aPartirDe(Instant inicio) {
        return new JanelaDeLicenca(inicio, null);
    }

    public boolean indeterminada() {
        return fim == null;
    }

    public boolean contem(Instant momento) {
        if (momento.isBefore(inicio)) {
            return false;
        }
        return fim == null || momento.isBefore(fim);
    }

    public boolean expiradaEm(Instant momento) {
        return fim != null && !momento.isBefore(fim);
    }

    /** Dias que faltam para o vencimento, ou -1 quando indeterminada. */
    public long diasAteVencer(Instant momento) {
        if (fim == null) {
            return -1;
        }
        return Duration.between(momento, fim).toDays();
    }
}
