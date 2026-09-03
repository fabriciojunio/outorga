package br.com.outorga.domain.rights;

public enum StatusDaLicenca {
    /** Cadastrada, ainda sem comprovação anexada. */
    RASCUNHO,
    /** Comprovacao anexada e conferida. Única que autoriza publicação. */
    VIGENTE,
    /** Rescindida antes do fim da janela, por qualquer motivo. */
    RESCINDIDA
}
