package br.com.outorga.domain.catalog;

import br.com.outorga.domain.rights.Licenca;
import br.com.outorga.shared.FalhaDeNegocio;
import br.com.outorga.shared.Result;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Raiz do agregado de catalogo. Um titulo e um filme ou uma serie.
 *
 * O metodo que importa aqui e {@link #publicar(Licenca, Instant)}: nao existe
 * outro caminho para o status PUBLICADO. Se a licenca nao estiver vigente, o
 * titulo nao vai ao ar, e nenhum campo publico permite forcar o status por
 * fora. Esse e o "gate de conteudo" do blueprint, escrito como invariante.
 */
public class Titulo {

    private final UUID id;
    private final UUID tenantId;
    private final TipoDeTitulo tipo;
    private String nome;
    private String sinopse;
    private Integer anoDeProducao;
    private ClassificacaoIndicativa classificacao;
    private Duration duracao;
    private String referenciaDoVideo;
    private String capaUri;
    private final Set<String> generos = new LinkedHashSet<>();
    private final List<Temporada> temporadas = new ArrayList<>();
    private UUID licencaId;
    private StatusDePublicacao status;
    private Instant publicadoEm;
    private String motivoDoBloqueio;

    private Titulo(UUID id, UUID tenantId, TipoDeTitulo tipo, String nome,
                   ClassificacaoIndicativa classificacao) {
        this.id = id;
        this.tenantId = tenantId;
        this.tipo = tipo;
        this.nome = nome;
        this.classificacao = classificacao;
        this.status = StatusDePublicacao.RASCUNHO;
    }

    public static Result<Titulo> criarFilme(UUID tenantId, String nome,
                                            ClassificacaoIndicativa classificacao, Duration duracao) {
        var base = validarBase(tenantId, nome, classificacao);
        if (base.isPresent()) {
            return Result.erro(base.get());
        }
        if (duracao == null || duracao.isZero() || duracao.isNegative()) {
            return Result.erro(new FalhaDeNegocio("TITULO_SEM_DURACAO",
                    "Filme precisa de duracao"));
        }
        var titulo = new Titulo(UUID.randomUUID(), tenantId, TipoDeTitulo.FILME, nome.trim(), classificacao);
        titulo.duracao = duracao;
        return Result.ok(titulo);
    }

    public static Result<Titulo> criarSerie(UUID tenantId, String nome,
                                            ClassificacaoIndicativa classificacao) {
        var base = validarBase(tenantId, nome, classificacao);
        if (base.isPresent()) {
            return Result.erro(base.get());
        }
        return Result.ok(new Titulo(UUID.randomUUID(), tenantId, TipoDeTitulo.SERIE, nome.trim(), classificacao));
    }

    private static Optional<FalhaDeNegocio> validarBase(UUID tenantId, String nome,
                                                        ClassificacaoIndicativa classificacao) {
        if (tenantId == null) {
            return Optional.of(new FalhaDeNegocio("TITULO_SEM_TENANT",
                    "Titulo precisa pertencer a um tenant"));
        }
        if (nome == null || nome.isBlank()) {
            return Optional.of(new FalhaDeNegocio("TITULO_SEM_NOME", "Informe o nome do titulo"));
        }
        if (classificacao == null) {
            return Optional.of(new FalhaDeNegocio("TITULO_SEM_CLASSIFICACAO",
                    "Informe a classificacao indicativa"));
        }
        return Optional.empty();
    }

    /**
     * Unica porta para o ar. Recusa quando a licenca nao pertence ao tenant,
     * nao esta vigente na data ou quando nao ha video para reproduzir.
     */
    public Result<Titulo> publicar(Licenca licenca, Instant agora) {
        if (licenca == null) {
            return Result.erro(new FalhaDeNegocio("PUBLICACAO_SEM_LICENCA",
                    "Vincule uma licenca antes de publicar"));
        }
        if (!licenca.tenantId().equals(tenantId)) {
            return Result.erro(new FalhaDeNegocio("LICENCA_DE_OUTRO_TENANT",
                    "A licenca informada pertence a outro tenant"));
        }
        if (!licenca.vigenteEm(agora)) {
            return Result.erro(new FalhaDeNegocio("LICENCA_NAO_VIGENTE",
                    "A licenca nao esta vigente nesta data")
                    .com("statusDaLicenca", licenca.status().name())
                    .com("inicio", licenca.janela().inicio().toString()));
        }
        if (!temConteudoReproduzivel()) {
            return Result.erro(new FalhaDeNegocio("TITULO_SEM_VIDEO",
                    tipo == TipoDeTitulo.FILME
                            ? "Envie o video do filme antes de publicar"
                            : "A serie precisa de ao menos um episodio com video"));
        }
        this.licencaId = licenca.id();
        this.status = StatusDePublicacao.PUBLICADO;
        this.publicadoEm = agora;
        this.motivoDoBloqueio = null;
        return Result.ok(this);
    }

    /**
     * Rodada de conferencia dos direitos. Tira do ar o que perdeu licenca e
     * devolve ao ar o que foi bloqueado e voltou a ter licenca vigente.
     * Retorna verdadeiro quando houve mudanca de status.
     */
    public boolean revisarDireitos(Licenca licenca, Instant agora) {
        boolean vigente = licenca != null && licenca.vigenteEm(agora);

        if (status == StatusDePublicacao.PUBLICADO && !vigente) {
            this.status = StatusDePublicacao.BLOQUEADO_POR_DIREITO;
            this.motivoDoBloqueio = licenca == null
                    ? "Licenca vinculada nao encontrada"
                    : motivoPara(licenca, agora);
            return true;
        }
        if (status == StatusDePublicacao.BLOQUEADO_POR_DIREITO && vigente && temConteudoReproduzivel()) {
            this.status = StatusDePublicacao.PUBLICADO;
            this.publicadoEm = agora;
            this.motivoDoBloqueio = null;
            return true;
        }
        return false;
    }

    private static String motivoPara(Licenca licenca, Instant agora) {
        return switch (licenca.status()) {
            case RESCINDIDA -> "Licenca rescindida";
            case RASCUNHO -> "Licenca sem comprovacao anexada";
            case VIGENTE -> licenca.janela().expiradaEm(agora)
                    ? "Janela de licenciamento vencida"
                    : "Janela de licenciamento ainda nao comecou";
        };
    }

    public Result<Titulo> despublicar(String motivo) {
        if (status != StatusDePublicacao.PUBLICADO) {
            return Result.erro(new FalhaDeNegocio("TITULO_NAO_PUBLICADO",
                    "So da para despublicar um titulo no ar"));
        }
        this.status = StatusDePublicacao.DESPUBLICADO;
        this.motivoDoBloqueio = motivo;
        return Result.ok(this);
    }

    public Result<Titulo> adicionarTemporada(Temporada temporada) {
        if (tipo != TipoDeTitulo.SERIE) {
            return Result.erro(new FalhaDeNegocio("TEMPORADA_EM_FILME",
                    "Filme nao tem temporada"));
        }
        boolean duplicada = temporadas.stream().anyMatch(t -> t.numero() == temporada.numero());
        if (duplicada) {
            return Result.erro(new FalhaDeNegocio("TEMPORADA_DUPLICADA",
                    "Ja existe a temporada " + temporada.numero()));
        }
        temporadas.add(temporada);
        temporadas.sort(Comparator.comparingInt(Temporada::numero));
        return Result.ok(this);
    }

    public Result<Titulo> definirVideoDoFilme(String referencia) {
        if (tipo != TipoDeTitulo.FILME) {
            return Result.erro(new FalhaDeNegocio("VIDEO_DIRETO_EM_SERIE",
                    "Em serie o video fica no episodio"));
        }
        if (referencia == null || referencia.isBlank()) {
            return Result.erro(new FalhaDeNegocio("VIDEO_SEM_REFERENCIA",
                    "Informe a referencia do video no provedor"));
        }
        this.referenciaDoVideo = referencia.trim();
        return Result.ok(this);
    }

    public boolean temConteudoReproduzivel() {
        if (tipo == TipoDeTitulo.FILME) {
            return referenciaDoVideo != null && !referenciaDoVideo.isBlank();
        }
        return temporadas.stream().anyMatch(Temporada::temEpisodioReproduzivel);
    }

    public boolean noAr() {
        return status == StatusDePublicacao.PUBLICADO;
    }

    /** Controle parental: o perfil so ve o que cabe no teto dele. */
    public boolean visivelPara(ClassificacaoIndicativa tetoDoPerfil) {
        return classificacao.liberadaPara(tetoDoPerfil);
    }

    public Optional<Episodio> localizarEpisodio(int temporada, int episodio) {
        return temporadas.stream()
                .filter(t -> t.numero() == temporada)
                .findFirst()
                .flatMap(t -> t.episodio(episodio));
    }

    public void adicionarGenero(String genero) {
        if (genero != null && !genero.isBlank()) {
            generos.add(genero.trim().toLowerCase());
        }
    }

    public void definirSinopse(String sinopse) { this.sinopse = sinopse; }

    public void definirAnoDeProducao(Integer ano) { this.anoDeProducao = ano; }

    public void definirCapa(String capaUri) { this.capaUri = capaUri; }

    public UUID id() { return id; }

    public UUID tenantId() { return tenantId; }

    public TipoDeTitulo tipo() { return tipo; }

    public String nome() { return nome; }

    public String sinopse() { return sinopse; }

    public Integer anoDeProducao() { return anoDeProducao; }

    public ClassificacaoIndicativa classificacao() { return classificacao; }

    public Duration duracao() { return duracao; }

    public String referenciaDoVideo() { return referenciaDoVideo; }

    public String capaUri() { return capaUri; }

    public Set<String> generos() { return Set.copyOf(generos); }

    public List<Temporada> temporadas() { return List.copyOf(temporadas); }

    public UUID licencaId() { return licencaId; }

    public StatusDePublicacao status() { return status; }

    public Instant publicadoEm() { return publicadoEm; }

    public String motivoDoBloqueio() { return motivoDoBloqueio; }

    public static Titulo reconstituir(UUID id, UUID tenantId, TipoDeTitulo tipo, String nome, String sinopse,
                                      Integer anoDeProducao, ClassificacaoIndicativa classificacao,
                                      Duration duracao, String referenciaDoVideo, String capaUri,
                                      Set<String> generos, List<Temporada> temporadas, UUID licencaId,
                                      StatusDePublicacao status, Instant publicadoEm, String motivoDoBloqueio) {
        var titulo = new Titulo(id, tenantId, tipo, nome, classificacao);
        titulo.sinopse = sinopse;
        titulo.anoDeProducao = anoDeProducao;
        titulo.duracao = duracao;
        titulo.referenciaDoVideo = referenciaDoVideo;
        titulo.capaUri = capaUri;
        titulo.generos.addAll(generos);
        titulo.temporadas.addAll(temporadas);
        titulo.licencaId = licencaId;
        titulo.status = status;
        titulo.publicadoEm = publicadoEm;
        titulo.motivoDoBloqueio = motivoDoBloqueio;
        return titulo;
    }
}
