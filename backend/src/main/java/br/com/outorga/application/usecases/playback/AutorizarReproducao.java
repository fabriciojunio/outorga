package br.com.outorga.application.usecases.playback;

import br.com.outorga.application.Auditor;
import br.com.outorga.application.ContextoDoChamador;
import br.com.outorga.application.ports.EntregaDeVideo;
import br.com.outorga.application.ports.Repositorios;
import br.com.outorga.domain.audit.AcaoAuditavel;
import br.com.outorga.domain.billing.Plano;
import br.com.outorga.domain.billing.Qualidade;
import br.com.outorga.domain.catalog.Titulo;
import br.com.outorga.domain.identity.Dispositivo;
import br.com.outorga.domain.playback.Autorizacao;
import br.com.outorga.domain.playback.ContextoDeReproducao;
import br.com.outorga.domain.playback.PoliticaDeReproducao;
import br.com.outorga.domain.playback.SessaoDeReproducao;
import br.com.outorga.domain.rights.TipoDeDispositivo;
import br.com.outorga.domain.rights.Territorio;
import br.com.outorga.shared.FalhaDeNegocio;
import br.com.outorga.shared.Falhas;
import br.com.outorga.shared.Result;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquestra a autorização de play: carrega os fatos, chama a politica e, se a
 * politica disser sim, pede o endereço assinado e abre a sessão.
 *
 * A politica de decisão fica no dominio. Aqui só tem busca, efeito colateral
 * e tradução. Nenhum "if" de regra de negócio mora neste arquivo, e essa
 * separação e o que deixa a regra testavel sem banco.
 */
public class AutorizarReproducao {

    private final Repositorios.DeTenant tenants;
    private final Repositorios.DeTitulo titulos;
    private final Repositorios.DeLicenca licencas;
    private final Repositorios.DeAssinatura assinaturas;
    private final Repositorios.DePlano planos;
    private final Repositorios.DePerfil perfis;
    private final Repositorios.DeDispositivo dispositivos;
    private final Repositorios.DeSessao sessoes;
    private final PoliticaDeReproducao politica;
    private final EntregaDeVideo entrega;
    private final Auditor auditor;
    private final Clock relogio;

    public AutorizarReproducao(Repositorios.DeTenant tenants, Repositorios.DeTitulo titulos,
                               Repositorios.DeLicenca licencas, Repositorios.DeAssinatura assinaturas,
                               Repositorios.DePlano planos, Repositorios.DePerfil perfis,
                               Repositorios.DeDispositivo dispositivos, Repositorios.DeSessao sessoes,
                               PoliticaDeReproducao politica, EntregaDeVideo entrega,
                               Auditor auditor, Clock relogio) {
        this.tenants = tenants;
        this.titulos = titulos;
        this.licencas = licencas;
        this.assinaturas = assinaturas;
        this.planos = planos;
        this.perfis = perfis;
        this.dispositivos = dispositivos;
        this.sessoes = sessoes;
        this.politica = politica;
        this.entrega = entrega;
        this.auditor = auditor;
        this.relogio = relogio;
    }

    public record Entrada(UUID tituloId, Integer temporada, Integer episodio, UUID perfilId,
                          String dispositivoIdentificador, TipoDeDispositivo dispositivoTipo,
                          String dispositivoApelido, String territorio, Qualidade qualidade) {
    }

    public record Saida(UUID sessaoId, String manifesto, String formato, Qualidade qualidade,
                        java.time.Instant expiraEm) {
    }

    public Result<Saida> executar(ContextoDoChamador chamador, Entrada entrada) {
        var agora = relogio.instant();

        var tenant = tenants.porId(chamador.tenantId());
        if (tenant.isEmpty()) {
            return recusar(chamador, entrada, Falhas.naoEncontrado("Servico"));
        }

        var territorio = Territorio.de(entrada.territorio() == null ? "BR" : entrada.territorio());
        if (territorio.falhou()) {
            return recusar(chamador, entrada, territorio.falha().orElseThrow());
        }

        var assinatura = assinaturas.vigenteDoUsuario(chamador.tenantId(), chamador.usuarioId())
                .orElse(null);
        if (assinatura == null) {
            return recusar(chamador, entrada, new FalhaDeNegocio("SEM_ASSINATURA",
                    "Nenhuma assinatura encontrada nesta conta"));
        }
        var plano = planos.porId(chamador.tenantId(), assinatura.planoId()).orElse(null);
        if (plano == null) {
            return recusar(chamador, entrada, Falhas.naoEncontrado("Plano da assinatura"));
        }

        var dispositivo = garantirDispositivo(chamador.usuarioId(), plano, entrada, agora);
        if (dispositivo.falhou()) {
            return recusar(chamador, entrada, dispositivo.falha().orElseThrow());
        }

        var titulo = titulos.porId(chamador.tenantId(), entrada.tituloId()).orElse(null);
        var licenca = titulo == null || titulo.licencaId() == null
                ? null
                : licencas.porId(chamador.tenantId(), titulo.licencaId()).orElse(null);
        var perfil = entrada.perfilId() == null ? null : perfis.porId(entrada.perfilId()).orElse(null);

        var referencia = titulo == null ? null : referenciaDoVideo(titulo, entrada);

        var contexto = new ContextoDeReproducao(tenant.get(), perfil, assinatura, plano, titulo,
                licenca, dispositivo.valorOuFalha(), territorio.valorOuFalha(),
                entrada.qualidade() == null ? Qualidade.FULL_HD : entrada.qualidade(),
                sessoes.abertasDoUsuario(chamador.tenantId(), chamador.usuarioId(), agora),
                referencia, agora);

        var decisao = politica.decidir(contexto);
        if (decisao.falhou()) {
            return recusar(chamador, entrada, decisao.falha().orElseThrow());
        }

        Autorizacao autorizacao = decisao.valorOuFalha();
        var endereco = entrega.assinarVod(autorizacao.referenciaDoVideo(), autorizacao.qualidade(),
                PoliticaDeReproducao.VALIDADE_DO_TOKEN);
        if (endereco.falhou()) {
            return recusar(chamador, entrada, endereco.falha().orElseThrow());
        }

        var sessao = SessaoDeReproducao.abrir(autorizacao, chamador.usuarioId(),
                entrada.dispositivoIdentificador(), agora);
        sessoes.salvar(sessao);
        dispositivo.valorOuFalha().marcarUso(agora);
        dispositivos.salvar(dispositivo.valorOuFalha());

        auditor.registrar(chamador, AcaoAuditavel.REPRODUCAO_AUTORIZADA, "titulo",
                entrada.tituloId().toString(),
                Map.of("sessao", sessao.id().toString(),
                        "licenca", String.valueOf(autorizacao.licencaId()),
                        "qualidade", autorizacao.qualidade().name(),
                        "dispositivo", entrada.dispositivoTipo().name()));

        var e = endereco.valorOuFalha();
        return Result.ok(new Saida(sessao.id(), e.manifesto(), e.formato(), autorizacao.qualidade(),
                e.expiraEm()));
    }

    /**
     * Série pede temporada e episódio; filme reproduz a referência do próprio
     * título. Devolver null aqui e legitimo: a politica trata como
     * VIDEO_INDISPONIVEL e a mensagem sai certa para o espectador.
     */
    private String referenciaDoVideo(Titulo titulo, Entrada entrada) {
        return switch (titulo.tipo()) {
            case FILME -> titulo.referenciaDoVideo();
            case SERIE -> {
                if (entrada.temporada() == null || entrada.episodio() == null) {
                    yield null;
                }
                yield titulo.localizarEpisodio(entrada.temporada(), entrada.episodio())
                        .map(ep -> ep.referenciaDoVideo())
                        .orElse(null);
            }
        };
    }

    /**
     * Aparelho já conhecido passa direto. Aparelho novo só entra se ainda
     * couber no limite do plano; quando estoura, a mensagem diz o que fazer,
     * porque "erro ao reproduzir" nessa hora vira chamado de suporte.
     */
    private Result<Dispositivo> garantirDispositivo(UUID usuarioId, Plano plano, Entrada entrada,
                                                    java.time.Instant agora) {
        Optional<Dispositivo> existente = dispositivos.porIdentificador(usuarioId,
                entrada.dispositivoIdentificador());
        if (existente.isPresent()) {
            return Result.ok(existente.get());
        }
        int registrados = dispositivos.doUsuario(usuarioId).size();
        if (registrados >= plano.dispositivosRegistraveis()) {
            return Result.erro(new FalhaDeNegocio("LIMITE_DE_DISPOSITIVOS",
                    "Esta conta já tem " + plano.dispositivosRegistraveis()
                            + " aparelhos registrados. Remova um em Minha conta para liberar este")
                    .com("limite", plano.dispositivosRegistraveis()));
        }
        var novo = Dispositivo.registrar(usuarioId, entrada.dispositivoIdentificador(),
                entrada.dispositivoTipo(), entrada.dispositivoApelido(), agora);
        if (novo.falhou()) {
            return novo;
        }
        return Result.ok(dispositivos.salvar(novo.valorOuFalha()));
    }

    private Result<Saida> recusar(ContextoDoChamador chamador, Entrada entrada, FalhaDeNegocio falha) {
        auditor.registrar(chamador, AcaoAuditavel.REPRODUCAO_RECUSADA, "titulo",
                entrada.tituloId() == null ? null : entrada.tituloId().toString(),
                Map.of("codigo", falha.codigo()));
        return Result.erro(falha);
    }
}
