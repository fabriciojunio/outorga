package br.com.outorga.api;

import br.com.outorga.domain.billing.Assinatura;
import br.com.outorga.domain.billing.Plano;
import br.com.outorga.domain.catalog.Titulo;
import br.com.outorga.domain.live.CanalAoVivo;
import br.com.outorga.domain.rights.Licenca;
import br.com.outorga.domain.tenant.Tenant;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Projecoes para a API.
 *
 * Entidade de dominio nao vira JSON direto. O motivo pratico aparece aqui:
 * {@code Titulo} carrega a URL da fonte do video e o id da licenca, que o
 * espectador nao tem nada que ver. Separar a vista da entidade e o que impede
 * que um campo novo no dominio vaze para a resposta sem ninguem decidir.
 */
public final class Vistas {

    private Vistas() {}

    public record TituloResumido(UUID id, String tipo, String nome, Integer ano, String classificacao,
                                 String capa, Set<String> generos, Long duracaoSegundos) {

        public static TituloResumido de(Titulo titulo) {
            return new TituloResumido(titulo.id(), titulo.tipo().name(), titulo.nome(),
                    titulo.anoDeProducao(), titulo.classificacao().rotulo(), titulo.capaUri(),
                    titulo.generos(),
                    titulo.duracao() == null ? null : titulo.duracao().toSeconds());
        }
    }

    public record EpisodioVisto(UUID id, int numero, String nome, Long duracaoSegundos,
                                boolean disponivel) {
    }

    public record TemporadaVista(UUID id, int numero, String nome, List<EpisodioVisto> episodios) {
    }

    public record TituloDetalhado(UUID id, String tipo, String nome, String sinopse, Integer ano,
                                  String classificacao, String capa, Set<String> generos,
                                  Long duracaoSegundos, List<TemporadaVista> temporadas) {

        public static TituloDetalhado de(Titulo titulo) {
            return new TituloDetalhado(titulo.id(), titulo.tipo().name(), titulo.nome(),
                    titulo.sinopse(), titulo.anoDeProducao(), titulo.classificacao().rotulo(),
                    titulo.capaUri(), titulo.generos(),
                    titulo.duracao() == null ? null : titulo.duracao().toSeconds(),
                    titulo.temporadas().stream()
                            .map(t -> new TemporadaVista(t.id(), t.numero(), t.titulo(),
                                    t.episodios().stream()
                                            .map(e -> new EpisodioVisto(e.id(), e.numero(), e.titulo(),
                                                    e.duracao() == null ? null : e.duracao().toSeconds(),
                                                    e.reproduzivel()))
                                            .toList()))
                            .toList());
        }
    }

    /** Vista do painel: mostra o que o espectador nao ve, inclusive o motivo do bloqueio. */
    public record TituloNoPainel(UUID id, String tipo, String nome, String status,
                                 String classificacao, UUID licencaId, String motivoDoBloqueio,
                                 boolean temVideo) {

        public static TituloNoPainel de(Titulo titulo) {
            return new TituloNoPainel(titulo.id(), titulo.tipo().name(), titulo.nome(),
                    titulo.status().name(), titulo.classificacao().rotulo(), titulo.licencaId(),
                    titulo.motivoDoBloqueio(), titulo.temConteudoReproduzivel());
        }
    }

    public record PlanoVisto(UUID id, String nome, String descricao, String preco, long precoCentavos,
                             String periodicidade, int telas, String qualidade, int diasDeTeste) {

        public static PlanoVisto de(Plano plano) {
            return new PlanoVisto(plano.id(), plano.nome(), plano.descricao(),
                    plano.preco().formatado(), plano.preco().centavos(),
                    plano.periodicidade().name(), plano.telasSimultaneas(),
                    plano.qualidadeMaxima().name(), plano.diasDeTeste());
        }
    }

    public record AssinaturaVista(UUID id, String status, String iniciadaEm, String fimDoCiclo,
                                 String fimDaCarencia, boolean assistindoAgora) {

        public static AssinaturaVista de(Assinatura assinatura, java.time.Instant agora) {
            return new AssinaturaVista(assinatura.id(), assinatura.status().name(),
                    String.valueOf(assinatura.iniciadaEm()),
                    String.valueOf(assinatura.fimDoCicloAtual()),
                    String.valueOf(assinatura.fimDaCarencia()),
                    assinatura.permiteAssistir(agora));
        }
    }

    public record LicencaVista(UUID id, String titular, String contrato, Set<String> territorios,
                               Set<String> dispositivos, String inicio, String fim, String status,
                               boolean temComprovacao) {

        public static LicencaVista de(Licenca licenca) {
            return new LicencaVista(licenca.id(), licenca.titular(), licenca.referenciaDoContrato(),
                    licenca.territorios().stream()
                            .map(br.com.outorga.domain.rights.Territorio::codigo)
                            .collect(java.util.stream.Collectors.toSet()),
                    licenca.dispositivosAutorizados().stream().map(Enum::name)
                            .collect(java.util.stream.Collectors.toSet()),
                    String.valueOf(licenca.janela().inicio()),
                    licenca.janela().fim() == null ? null : String.valueOf(licenca.janela().fim()),
                    licenca.status().name(),
                    licenca.comprovacaoUri() != null);
        }
    }

    public record CanalVisto(UUID id, String nome, int numero, String logo, String classificacao,
                             boolean noAr) {

        public static CanalVisto de(CanalAoVivo canal) {
            return new CanalVisto(canal.id(), canal.nome(), canal.numero(), canal.logoUri(),
                    canal.classificacao().rotulo(), canal.noAr());
        }
    }

    /** O que o aplicativo precisa saber para se pintar com a marca do cliente. */
    public record Identidade(String slug, String nome, String logo, String corPrimaria,
                             String corDeFundo, boolean aceitandoAcesso) {

        public static Identidade de(Tenant tenant, java.time.Instant agora) {
            return new Identidade(tenant.slug(), tenant.marca().nomeExibido(),
                    tenant.marca().logoUri(), tenant.marca().corPrimaria(),
                    tenant.marca().corDeFundo(), tenant.aceitaTrafegoDeEspectador(agora));
        }
    }
}
