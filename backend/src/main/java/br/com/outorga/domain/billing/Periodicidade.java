package br.com.outorga.domain.billing;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public enum Periodicidade {

    MENSAL(30),
    TRIMESTRAL(90),
    SEMESTRAL(180),
    ANUAL(365);

    private final int dias;

    Periodicidade(int dias) {
        this.dias = dias;
    }

    public int dias() {
        return dias;
    }

    public Instant proximoVencimento(Instant referencia) {
        return referencia.plus(dias, ChronoUnit.DAYS);
    }
}
