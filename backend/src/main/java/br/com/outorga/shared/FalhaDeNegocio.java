package br.com.outorga.shared;

import java.util.Map;

/**
 * Motivo de uma operação ter sido recusada. O código e estável e serve de
 * contrato para o cliente da API; a mensagem e para gente ler.
 */
public record FalhaDeNegocio(String codigo, String mensagem, Map<String, Object> detalhes) {

    public FalhaDeNegocio {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("código da falha e obrigatório");
        }
        detalhes = detalhes == null ? Map.of() : Map.copyOf(detalhes);
    }

    public FalhaDeNegocio(String codigo, String mensagem) {
        this(codigo, mensagem, Map.of());
    }

    public FalhaDeNegocio com(String chave, Object valor) {
        var novos = new java.util.HashMap<>(detalhes);
        novos.put(chave, valor);
        return new FalhaDeNegocio(codigo, mensagem, novos);
    }
}
