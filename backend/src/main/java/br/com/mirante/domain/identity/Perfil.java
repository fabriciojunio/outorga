package br.com.mirante.domain.identity;

import br.com.mirante.domain.catalog.ClassificacaoIndicativa;
import br.com.mirante.shared.FalhaDeNegocio;
import br.com.mirante.shared.Result;

import java.util.UUID;

/**
 * Perfil de exibicao dentro de uma conta. O teto de classificacao mora aqui,
 * junto com o PIN que protege o perfil adulto do controle remoto da sala.
 */
public class Perfil {

    public static final int MAXIMO_POR_CONTA = 4;

    private final UUID id;
    private final UUID usuarioId;
    private String nome;
    private ClassificacaoIndicativa tetoDeClassificacao;
    private String pinHash;
    private final boolean infantil;
    private String avatar;

    private Perfil(UUID id, UUID usuarioId, String nome, ClassificacaoIndicativa teto, boolean infantil) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.nome = nome;
        this.tetoDeClassificacao = teto;
        this.infantil = infantil;
    }

    public static Result<Perfil> criar(UUID usuarioId, String nome, ClassificacaoIndicativa teto,
                                       boolean infantil, int perfisJaExistentes) {
        if (usuarioId == null) {
            return Result.erro(new FalhaDeNegocio("PERFIL_SEM_CONTA", "Perfil precisa de uma conta"));
        }
        if (nome == null || nome.isBlank()) {
            return Result.erro(new FalhaDeNegocio("PERFIL_SEM_NOME", "Informe o nome do perfil"));
        }
        if (perfisJaExistentes >= MAXIMO_POR_CONTA) {
            return Result.erro(new FalhaDeNegocio("LIMITE_DE_PERFIS",
                    "A conta ja tem " + MAXIMO_POR_CONTA + " perfis"));
        }
        var tetoEfetivo = infantil ? ClassificacaoIndicativa.DEZ_ANOS
                : (teto == null ? ClassificacaoIndicativa.DEZOITO_ANOS : teto);
        return Result.ok(new Perfil(UUID.randomUUID(), usuarioId, nome.trim(), tetoEfetivo, infantil));
    }

    public Result<Perfil> ajustarTeto(ClassificacaoIndicativa novoTeto) {
        if (novoTeto == null) {
            return Result.erro(new FalhaDeNegocio("TETO_INVALIDO", "Informe a classificacao maxima"));
        }
        if (infantil && !novoTeto.liberadaPara(ClassificacaoIndicativa.DOZE_ANOS)) {
            return Result.erro(new FalhaDeNegocio("TETO_INFANTIL",
                    "Perfil infantil nao passa de 12 anos"));
        }
        this.tetoDeClassificacao = novoTeto;
        return Result.ok(this);
    }

    public void definirPin(String pinHash) {
        this.pinHash = pinHash;
    }

    public boolean protegidoPorPin() {
        return pinHash != null && !pinHash.isBlank();
    }

    public void definirAvatar(String avatar) {
        this.avatar = avatar;
    }

    public UUID id() { return id; }

    public UUID usuarioId() { return usuarioId; }

    public String nome() { return nome; }

    public ClassificacaoIndicativa tetoDeClassificacao() { return tetoDeClassificacao; }

    public String pinHash() { return pinHash; }

    public boolean infantil() { return infantil; }

    public String avatar() { return avatar; }

    public static Perfil reconstituir(UUID id, UUID usuarioId, String nome, ClassificacaoIndicativa teto,
                                      String pinHash, boolean infantil, String avatar) {
        var perfil = new Perfil(id, usuarioId, nome, teto, infantil);
        perfil.pinHash = pinHash;
        perfil.avatar = avatar;
        return perfil;
    }
}
