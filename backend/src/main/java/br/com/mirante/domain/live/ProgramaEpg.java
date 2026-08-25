package br.com.mirante.domain.live;

import br.com.mirante.domain.catalog.ClassificacaoIndicativa;
import br.com.mirante.shared.FalhaDeNegocio;
import br.com.mirante.shared.Result;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Item da grade de programacao de um canal.
 */
public record ProgramaEpg(UUID id, UUID tenantId, UUID canalId, String titulo, String descricao,
                          Instant inicio, Instant fim, ClassificacaoIndicativa classificacao) {

    public static Result<ProgramaEpg> criar(UUID tenantId, UUID canalId, String titulo, Instant inicio,
                                            Instant fim, ClassificacaoIndicativa classificacao) {
        if (titulo == null || titulo.isBlank()) {
            return Result.erro(new FalhaDeNegocio("EPG_SEM_TITULO", "Informe o titulo do programa"));
        }
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            return Result.erro(new FalhaDeNegocio("EPG_HORARIO_INVALIDO",
                    "O fim do programa precisa ser depois do inicio"));
        }
        return Result.ok(new ProgramaEpg(UUID.randomUUID(), tenantId, canalId, titulo.trim(), null,
                inicio, fim, classificacao == null ? ClassificacaoIndicativa.LIVRE : classificacao));
    }

    public boolean noArEm(Instant momento) {
        return !momento.isBefore(inicio) && momento.isBefore(fim);
    }

    public boolean conflitaCom(ProgramaEpg outro) {
        return canalId.equals(outro.canalId)
                && inicio.isBefore(outro.fim)
                && outro.inicio.isBefore(fim);
    }

    /** Programa no ar agora, dentro de uma grade ja carregada. */
    public static Optional<ProgramaEpg> agora(List<ProgramaEpg> grade, Instant momento) {
        return grade.stream().filter(p -> p.noArEm(momento)).findFirst();
    }

    public static Optional<ProgramaEpg> aSeguir(List<ProgramaEpg> grade, Instant momento) {
        return grade.stream()
                .filter(p -> p.inicio().isAfter(momento))
                .min((a, b) -> a.inicio().compareTo(b.inicio()));
    }
}
