package br.com.outorga.domain.rights;

/**
 * Categoria de dispositivo, do jeito que contrato de licenciamento costuma
 * recortar. Contrato que libera web e celular mas não TV conectada e comum, e
 * o sistema precisa saber diferenciar.
 */
public enum TipoDeDispositivo {
    WEB,
    ANDROID,
    IOS,
    TV_CONECTADA,
    OUTRO
}
