package br.com.mirante.application.usecases.catalog;

import br.com.mirante.application.Auditor;
import br.com.mirante.application.ContextoDoChamador;
import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.domain.audit.AcaoAuditavel;
import br.com.mirante.domain.catalog.ClassificacaoIndicativa;
import br.com.mirante.domain.catalog.TipoDeTitulo;
import br.com.mirante.domain.catalog.Titulo;
import br.com.mirante.shared.Falhas;
import br.com.mirante.shared.Result;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

public class CriarTitulo {

    private final Repositorios.DeTitulo titulos;
    private final Auditor auditor;

    public CriarTitulo(Repositorios.DeTitulo titulos, Auditor auditor) {
        this.titulos = titulos;
        this.auditor = auditor;
    }

    public record Entrada(TipoDeTitulo tipo, String nome, String sinopse, Integer anoDeProducao,
                          ClassificacaoIndicativa classificacao, Duration duracao,
                          Set<String> generos, String capaUri, String referenciaDoVideo) {
    }

    public Result<Titulo> executar(ContextoDoChamador chamador, Entrada entrada) {
        if (!chamador.podePublicarCatalogo()) {
            return Result.erro(Falhas.semPermissao("cadastrar titulo"));
        }

        var criacao = entrada.tipo() == TipoDeTitulo.FILME
                ? Titulo.criarFilme(chamador.tenantId(), entrada.nome(), entrada.classificacao(),
                        entrada.duracao())
                : Titulo.criarSerie(chamador.tenantId(), entrada.nome(), entrada.classificacao());
        if (criacao.falhou()) {
            return criacao;
        }

        var titulo = criacao.valorOuFalha();
        titulo.definirSinopse(entrada.sinopse());
        titulo.definirAnoDeProducao(entrada.anoDeProducao());
        titulo.definirCapa(entrada.capaUri());
        if (entrada.generos() != null) {
            entrada.generos().forEach(titulo::adicionarGenero);
        }
        if (entrada.tipo() == TipoDeTitulo.FILME && entrada.referenciaDoVideo() != null) {
            var video = titulo.definirVideoDoFilme(entrada.referenciaDoVideo());
            if (video.falhou()) {
                return video;
            }
        }

        titulos.salvar(titulo);
        auditor.registrar(chamador, AcaoAuditavel.TITULO_CRIADO, "titulo", titulo.id().toString(),
                Map.of("nome", titulo.nome(), "tipo", titulo.tipo().name()));
        return Result.ok(titulo);
    }
}
