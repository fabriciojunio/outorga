package br.com.outorga.application.usecases.live;

import br.com.outorga.application.Auditor;
import br.com.outorga.application.ContextoDoChamador;
import br.com.outorga.application.ports.Repositorios;
import br.com.outorga.domain.audit.AcaoAuditavel;
import br.com.outorga.domain.catalog.ClassificacaoIndicativa;
import br.com.outorga.domain.live.CanalAoVivo;
import br.com.outorga.domain.live.ProgramaEpg;
import br.com.outorga.shared.Falhas;
import br.com.outorga.shared.Result;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GerirCanais {

    private final Repositorios.DeCanal canais;
    private final Repositorios.DeLicenca licencas;
    private final Repositorios.DeEpg epg;
    private final Repositorios.DePerfil perfis;
    private final Auditor auditor;
    private final Clock relogio;

    public GerirCanais(Repositorios.DeCanal canais, Repositorios.DeLicenca licencas,
                       Repositorios.DeEpg epg, Repositorios.DePerfil perfis, Auditor auditor,
                       Clock relogio) {
        this.canais = canais;
        this.licencas = licencas;
        this.epg = epg;
        this.perfis = perfis;
        this.auditor = auditor;
        this.relogio = relogio;
    }

    public record NovoCanal(String nome, int numero, ClassificacaoIndicativa classificacao,
                            String logoUri, String urlDaFonte) {
    }

    public Result<CanalAoVivo> cadastrar(ContextoDoChamador chamador, NovoCanal novo) {
        if (!chamador.podePublicarCatalogo()) {
            return Result.erro(Falhas.semPermissao("cadastrar canal"));
        }
        var criacao = CanalAoVivo.cadastrar(chamador.tenantId(), novo.nome(), novo.numero(),
                novo.classificacao());
        if (criacao.falhou()) {
            return criacao;
        }
        var canal = criacao.valorOuFalha();
        canal.definirLogo(novo.logoUri());
        if (novo.urlDaFonte() != null) {
            var fonte = canal.definirFonte(novo.urlDaFonte());
            if (fonte.falhou()) {
                return fonte;
            }
        }
        return Result.ok(canais.salvar(canal));
    }

    public Result<CanalAoVivo> colocarNoAr(ContextoDoChamador chamador, UUID canalId, UUID licencaId) {
        if (!chamador.podePublicarCatalogo()) {
            return Result.erro(Falhas.semPermissao("colocar canal no ar"));
        }
        var canal = canais.porId(chamador.tenantId(), canalId);
        if (canal.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Canal"));
        }
        var licenca = licencas.porId(chamador.tenantId(), licencaId);
        if (licenca.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Licenca"));
        }
        var noAr = canal.get().colocarNoAr(licenca.get(), relogio.instant());
        if (noAr.falhou()) {
            return noAr;
        }
        canais.salvar(canal.get());
        auditor.registrar(chamador, AcaoAuditavel.CANAL_NO_AR, "canal", canalId.toString(),
                Map.of("licenca", licencaId.toString(), "titular", licenca.get().titular()));
        return Result.ok(canal.get());
    }

    /** Grade que o espectador ve, ja filtrada pelo teto do perfil. */
    public Result<List<CanalAoVivo>> grade(UUID tenantId, UUID perfilId) {
        var teto = ClassificacaoIndicativa.DEZOITO_ANOS;
        if (perfilId != null) {
            var perfil = perfis.porId(perfilId);
            if (perfil.isEmpty()) {
                return Result.erro(Falhas.naoEncontrado("Perfil"));
            }
            teto = perfil.get().tetoDeClassificacao();
        }
        var tetoFinal = teto;
        return Result.ok(canais.noAr(tenantId).stream()
                .filter(c -> c.visivelPara(tetoFinal))
                .toList());
    }

    public record NoArEAgora(UUID canalId, String canal, ProgramaEpg agora, ProgramaEpg aSeguir) {
    }

    public List<NoArEAgora> programacao(UUID tenantId, Instant de, Instant ate) {
        var momento = relogio.instant();
        var saida = new ArrayList<NoArEAgora>();
        for (var canal : canais.noAr(tenantId)) {
            var programas = epg.doCanalEntre(tenantId, canal.id(), de, ate);
            saida.add(new NoArEAgora(canal.id(), canal.nome(),
                    ProgramaEpg.agora(programas, momento).orElse(null),
                    ProgramaEpg.aSeguir(programas, momento).orElse(null)));
        }
        return saida;
    }

    /**
     * Carga da grade. Recusa o lote inteiro quando ha choque de horario, em
     * vez de gravar metade: EPG parcialmente carregado e pior de depurar do
     * que EPG que nao carregou.
     */
    public Result<Integer> carregarGrade(ContextoDoChamador chamador, List<ProgramaEpg> programas) {
        if (!chamador.podePublicarCatalogo()) {
            return Result.erro(Falhas.semPermissao("carregar programacao"));
        }
        for (int i = 0; i < programas.size(); i++) {
            for (int j = i + 1; j < programas.size(); j++) {
                if (programas.get(i).conflitaCom(programas.get(j))) {
                    return Result.erro(Falhas.conflito("Choque de horario entre \""
                            + programas.get(i).titulo() + "\" e \"" + programas.get(j).titulo() + "\""));
                }
            }
        }
        epg.salvarTodos(programas);
        return Result.ok(programas.size());
    }
}
