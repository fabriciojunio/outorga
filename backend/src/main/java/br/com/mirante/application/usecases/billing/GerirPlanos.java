package br.com.mirante.application.usecases.billing;

import br.com.mirante.application.Auditor;
import br.com.mirante.application.ContextoDoChamador;
import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.domain.audit.AcaoAuditavel;
import br.com.mirante.domain.billing.Dinheiro;
import br.com.mirante.domain.billing.Periodicidade;
import br.com.mirante.domain.billing.Plano;
import br.com.mirante.domain.billing.Qualidade;
import br.com.mirante.shared.Falhas;
import br.com.mirante.shared.Result;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GerirPlanos {

    private final Repositorios.DePlano planos;
    private final Auditor auditor;

    public GerirPlanos(Repositorios.DePlano planos, Auditor auditor) {
        this.planos = planos;
        this.auditor = auditor;
    }

    public record NovoPlano(String nome, String descricao, long precoEmCentavos,
                            Periodicidade periodicidade, int telas, Qualidade qualidade,
                            int diasDeTeste) {
    }

    public Result<Plano> criar(ContextoDoChamador chamador, NovoPlano novo) {
        if (!chamador.podeMexerEmCobranca()) {
            return Result.erro(Falhas.semPermissao("criar plano"));
        }
        Dinheiro preco;
        try {
            preco = Dinheiro.reais(novo.precoEmCentavos());
        } catch (IllegalArgumentException e) {
            return Result.erro(Falhas.invalido(e.getMessage()));
        }
        var criacao = Plano.criar(chamador.tenantId(), novo.nome(), preco, novo.periodicidade(),
                novo.telas(), novo.qualidade());
        if (criacao.falhou()) {
            return criacao;
        }
        var plano = criacao.valorOuFalha();
        plano.definirDescricao(novo.descricao());
        plano.definirDiasDeTeste(novo.diasDeTeste());
        planos.salvar(plano);

        auditor.registrar(chamador, AcaoAuditavel.PLANO_CRIADO, "plano", plano.id().toString(),
                Map.of("nome", plano.nome(), "preco", preco.formatado(),
                        "telas", String.valueOf(plano.telasSimultaneas())));
        return Result.ok(plano);
    }

    public List<Plano> emVenda(UUID tenantId) {
        return planos.ativosDoTenant(tenantId);
    }

    public Result<Plano> desativar(ContextoDoChamador chamador, UUID planoId) {
        if (!chamador.podeMexerEmCobranca()) {
            return Result.erro(Falhas.semPermissao("desativar plano"));
        }
        var achado = planos.porId(chamador.tenantId(), planoId);
        if (achado.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Plano"));
        }
        achado.get().desativar();
        return Result.ok(planos.salvar(achado.get()));
    }
}
