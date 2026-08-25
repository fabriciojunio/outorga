package br.com.mirante.domain.catalog;

import br.com.mirante.shared.FalhaDeNegocio;
import br.com.mirante.shared.Result;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Temporada {

    private final UUID id;
    private final int numero;
    private String titulo;
    private final List<Episodio> episodios = new ArrayList<>();

    private Temporada(UUID id, int numero, String titulo) {
        this.id = id;
        this.numero = numero;
        this.titulo = titulo;
    }

    public static Result<Temporada> criar(int numero, String titulo) {
        if (numero < 1) {
            return Result.erro(new FalhaDeNegocio("TEMPORADA_NUMERO_INVALIDO",
                    "Numero da temporada comeca em 1"));
        }
        return Result.ok(new Temporada(UUID.randomUUID(), numero,
                titulo == null || titulo.isBlank() ? "Temporada " + numero : titulo.trim()));
    }

    public Result<Temporada> adicionar(Episodio episodio) {
        boolean duplicado = episodios.stream().anyMatch(e -> e.numero() == episodio.numero());
        if (duplicado) {
            return Result.erro(new FalhaDeNegocio("EPISODIO_DUPLICADO",
                    "Ja existe episodio " + episodio.numero() + " na temporada " + numero));
        }
        episodios.add(episodio);
        episodios.sort(Comparator.comparingInt(Episodio::numero));
        return Result.ok(this);
    }

    public Optional<Episodio> episodio(int numero) {
        return episodios.stream().filter(e -> e.numero() == numero).findFirst();
    }

    public boolean temEpisodioReproduzivel() {
        return episodios.stream().anyMatch(Episodio::reproduzivel);
    }

    public UUID id() { return id; }

    public int numero() { return numero; }

    public String titulo() { return titulo; }

    public List<Episodio> episodios() { return List.copyOf(episodios); }

    public static Temporada reconstituir(UUID id, int numero, String titulo, List<Episodio> episodios) {
        var temporada = new Temporada(id, numero, titulo);
        temporada.episodios.addAll(episodios);
        temporada.episodios.sort(Comparator.comparingInt(Episodio::numero));
        return temporada;
    }
}
