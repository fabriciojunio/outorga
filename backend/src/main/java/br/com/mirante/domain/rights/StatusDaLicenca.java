package br.com.mirante.domain.rights;

public enum StatusDaLicenca {
    /** Cadastrada, ainda sem comprovacao anexada. */
    RASCUNHO,
    /** Comprovacao anexada e conferida. Unica que autoriza publicacao. */
    VIGENTE,
    /** Rescindida antes do fim da janela, por qualquer motivo. */
    RESCINDIDA
}
