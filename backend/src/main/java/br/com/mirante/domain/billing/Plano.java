package br.com.mirante.domain.billing;

import br.com.mirante.shared.FalhaDeNegocio;
import br.com.mirante.shared.Result;

import java.util.UUID;

/**
 * Plano vendido pelo cliente aos assinantes dele. Telas e qualidade nao sao
 * enfeite de tabela de preco: entram na decisao de reproduzir.
 */
public class Plano {

    public static final int MAXIMO_DE_TELAS = 6;

    private final UUID id;
    private final UUID tenantId;
    private String nome;
    private String descricao;
    private Dinheiro preco;
    private Periodicidade periodicidade;
    private int telasSimultaneas;
    private Qualidade qualidadeMaxima;
    private int diasDeTeste;
    private boolean ativo;

    private Plano(UUID id, UUID tenantId, String nome, Dinheiro preco, Periodicidade periodicidade,
                  int telasSimultaneas, Qualidade qualidadeMaxima) {
        this.id = id;
        this.tenantId = tenantId;
        this.nome = nome;
        this.preco = preco;
        this.periodicidade = periodicidade;
        this.telasSimultaneas = telasSimultaneas;
        this.qualidadeMaxima = qualidadeMaxima;
        this.ativo = true;
    }

    public static Result<Plano> criar(UUID tenantId, String nome, Dinheiro preco,
                                      Periodicidade periodicidade, int telasSimultaneas,
                                      Qualidade qualidadeMaxima) {
        if (tenantId == null) {
            return Result.erro(new FalhaDeNegocio("PLANO_SEM_TENANT",
                    "Plano precisa pertencer a um tenant"));
        }
        if (nome == null || nome.isBlank()) {
            return Result.erro(new FalhaDeNegocio("PLANO_SEM_NOME", "Informe o nome do plano"));
        }
        if (preco == null) {
            return Result.erro(new FalhaDeNegocio("PLANO_SEM_PRECO", "Informe o preco do plano"));
        }
        if (telasSimultaneas < 1 || telasSimultaneas > MAXIMO_DE_TELAS) {
            return Result.erro(new FalhaDeNegocio("PLANO_TELAS_INVALIDO",
                    "Telas simultaneas entre 1 e " + MAXIMO_DE_TELAS));
        }
        if (periodicidade == null || qualidadeMaxima == null) {
            return Result.erro(new FalhaDeNegocio("PLANO_INCOMPLETO",
                    "Informe periodicidade e qualidade maxima"));
        }
        return Result.ok(new Plano(UUID.randomUUID(), tenantId, nome.trim(), preco, periodicidade,
                telasSimultaneas, qualidadeMaxima));
    }

    /**
     * Quantos aparelhos a conta pode manter registrados. Fica no dobro das
     * telas porque a familia troca de celular e liga a TV da casa de praia
     * sem avisar ninguem, e a fila de suporte com "remova meu aparelho antigo"
     * custa mais caro do que a banda que esse folego consome. O limite que
     * protege receita e o de telas simultaneas, nao o de cadastro.
     */
    public int dispositivosRegistraveis() {
        return telasSimultaneas * 2;
    }

    /** Qualidade efetiva de uma sessao: o menor entre o pedido e o teto do plano. */
    public Qualidade limitar(Qualidade pedida) {
        return qualidadeMaxima.cobre(pedida) ? pedida : qualidadeMaxima;
    }

    public Dinheiro precoCom(Cupom cupom) {
        return cupom == null ? preco : preco.comDescontoDe(cupom.percentual());
    }

    public void definirDiasDeTeste(int dias) {
        this.diasDeTeste = Math.max(0, dias);
    }

    public void definirDescricao(String descricao) {
        this.descricao = descricao;
    }

    public void desativar() {
        this.ativo = false;
    }

    public UUID id() { return id; }

    public UUID tenantId() { return tenantId; }

    public String nome() { return nome; }

    public String descricao() { return descricao; }

    public Dinheiro preco() { return preco; }

    public Periodicidade periodicidade() { return periodicidade; }

    public int telasSimultaneas() { return telasSimultaneas; }

    public Qualidade qualidadeMaxima() { return qualidadeMaxima; }

    public int diasDeTeste() { return diasDeTeste; }

    public boolean ativo() { return ativo; }

    public static Plano reconstituir(UUID id, UUID tenantId, String nome, String descricao, Dinheiro preco,
                                     Periodicidade periodicidade, int telasSimultaneas,
                                     Qualidade qualidadeMaxima, int diasDeTeste, boolean ativo) {
        var plano = new Plano(id, tenantId, nome, preco, periodicidade, telasSimultaneas, qualidadeMaxima);
        plano.descricao = descricao;
        plano.diasDeTeste = diasDeTeste;
        plano.ativo = ativo;
        return plano;
    }
}
