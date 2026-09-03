package br.com.outorga.domain.catalog;

import br.com.outorga.shared.FalhaDeNegocio;
import br.com.outorga.shared.Result;

import java.time.Duration;
import java.util.UUID;

/**
 * Episódio de uma temporada. Carrega a referência do ativo de vídeo no
 * provedor de entrega; o dominio não conhece o provedor, só guarda a chave.
 */
public class Episodio {

    private final UUID id;
    private final int numero;
    private String titulo;
    private String sinopse;
    private Duration duracao;
    private String referenciaDoVideo;

    private Episodio(UUID id, int numero, String titulo, String sinopse, Duration duracao,
                     String referenciaDoVideo) {
        this.id = id;
        this.numero = numero;
        this.titulo = titulo;
        this.sinopse = sinopse;
        this.duracao = duracao;
        this.referenciaDoVideo = referenciaDoVideo;
    }

    public static Result<Episodio> criar(int numero, String titulo, Duration duracao,
                                         String referenciaDoVideo) {
        if (numero < 1) {
            return Result.erro(new FalhaDeNegocio("EPISODIO_NUMERO_INVALIDO",
                    "Número do episódio comeca em 1"));
        }
        if (titulo == null || titulo.isBlank()) {
            return Result.erro(new FalhaDeNegocio("EPISODIO_SEM_TITULO",
                    "Informe o título do episódio"));
        }
        if (duracao == null || duracao.isZero() || duracao.isNegative()) {
            return Result.erro(new FalhaDeNegocio("EPISODIO_SEM_DURACAO",
                    "Informe a duração do episódio"));
        }
        return Result.ok(new Episodio(UUID.randomUUID(), numero, titulo.trim(), null, duracao,
                referenciaDoVideo));
    }

    public boolean reproduzivel() {
        return referenciaDoVideo != null && !referenciaDoVideo.isBlank();
    }

    public void definirReferenciaDoVideo(String referencia) {
        this.referenciaDoVideo = referencia;
    }

    public void definirSinopse(String sinopse) {
        this.sinopse = sinopse;
    }

    public UUID id() { return id; }

    public int numero() { return numero; }

    public String titulo() { return titulo; }

    public String sinopse() { return sinopse; }

    public Duration duracao() { return duracao; }

    public String referenciaDoVideo() { return referenciaDoVideo; }

    public static Episodio reconstituir(UUID id, int numero, String titulo, String sinopse,
                                        Duration duracao, String referenciaDoVideo) {
        return new Episodio(id, numero, titulo, sinopse, duracao, referenciaDoVideo);
    }
}
