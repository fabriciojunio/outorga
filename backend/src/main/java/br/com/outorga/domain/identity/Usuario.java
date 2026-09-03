package br.com.outorga.domain.identity;

import br.com.outorga.shared.FalhaDeNegocio;
import br.com.outorga.shared.Result;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Usuário do sistema, seja operador de painel ou assinante. O que separa um
 * do outro e o papel, não a tabela.
 *
 * O bloqueio por tentativa erra para o lado seguro: conta bloqueada recusa
 * login mesmo com a senha certa, até o prazo passar.
 */
public class Usuario {

    public static final int TENTATIVAS_ATE_BLOQUEAR = 5;
    public static final Duration DURACAO_DO_BLOQUEIO = Duration.ofMinutes(15);

    private final UUID id;
    private final UUID tenantId;
    private Email email;
    private String senhaHash;
    private String nome;
    private final Set<Papel> papeis;
    private boolean ativo;
    private int tentativasSeguidas;
    private Instant bloqueadoAte;
    private Instant ultimoAcesso;
    private final Instant criadoEm;
    private Instant anonimizadoEm;

    private Usuario(UUID id, UUID tenantId, Email email, String senhaHash, String nome,
                    Set<Papel> papeis, Instant criadoEm) {
        this.id = id;
        this.tenantId = tenantId;
        this.email = email;
        this.senhaHash = senhaHash;
        this.nome = nome;
        this.papeis = EnumSet.copyOf(papeis);
        this.ativo = true;
        this.criadoEm = criadoEm;
    }

    public static Result<Usuario> criar(UUID tenantId, Email email, String senhaHash, String nome,
                                        Set<Papel> papeis, Instant agora) {
        if (tenantId == null) {
            return Result.erro(new FalhaDeNegocio("USUARIO_SEM_TENANT",
                    "Usuário precisa pertencer a um tenant"));
        }
        if (email == null) {
            return Result.erro(new FalhaDeNegocio("EMAIL_INVALIDO", "Informe um e-mail válido"));
        }
        if (senhaHash == null || senhaHash.isBlank()) {
            return Result.erro(new FalhaDeNegocio("SENHA_OBRIGATORIA", "Informe a senha"));
        }
        if (nome == null || nome.isBlank()) {
            return Result.erro(new FalhaDeNegocio("USUARIO_SEM_NOME", "Informe o nome"));
        }
        if (papeis == null || papeis.isEmpty()) {
            return Result.erro(new FalhaDeNegocio("USUARIO_SEM_PAPEL", "Informe ao menos um papel"));
        }
        return Result.ok(new Usuario(UUID.randomUUID(), tenantId, email, senhaHash, nome.trim(),
                papeis, agora));
    }

    /**
     * Chamado depois da conferencia da senha. A entidade decide o desfecho e
     * mantem o contador; quem verifica o hash e a infraestrutura.
     */
    public Result<Usuario> registrarTentativaDeLogin(boolean senhaConfere, Instant agora) {
        if (!ativo) {
            return Result.erro(new FalhaDeNegocio("CONTA_INATIVA",
                    "Conta desativada. Fale com o suporte"));
        }
        if (estaBloqueado(agora)) {
            return Result.erro(new FalhaDeNegocio("CONTA_BLOQUEADA",
                    "Muitas tentativas. Tente de novo mais tarde")
                    .com("liberaEm", bloqueadoAte.toString()));
        }
        if (!senhaConfere) {
            tentativasSeguidas++;
            if (tentativasSeguidas >= TENTATIVAS_ATE_BLOQUEAR) {
                bloqueadoAte = agora.plus(DURACAO_DO_BLOQUEIO);
            }
            return Result.erro(new FalhaDeNegocio("CREDENCIAL_INVALIDA",
                    "E-mail ou senha incorretos"));
        }
        tentativasSeguidas = 0;
        bloqueadoAte = null;
        ultimoAcesso = agora;
        return Result.ok(this);
    }

    public boolean estaBloqueado(Instant agora) {
        return bloqueadoAte != null && agora.isBefore(bloqueadoAte);
    }

    public Result<Usuario> trocarSenha(String novoHash) {
        if (novoHash == null || novoHash.isBlank()) {
            return Result.erro(new FalhaDeNegocio("SENHA_OBRIGATORIA", "Informe a nova senha"));
        }
        this.senhaHash = novoHash;
        this.tentativasSeguidas = 0;
        this.bloqueadoAte = null;
        return Result.ok(this);
    }

    public void desativar() {
        this.ativo = false;
    }

    /**
     * Atendimento a um pedido de exclusão. Troca o que identifica a pessoa por
     * um marcador e trava a conta. A linha continua existindo porque
     * assinatura, pagamento e auditoria apontam para ela; o que sai e o nome,
     * o e-mail e qualquer possibilidade de entrar de novo.
     */
    public void anonimizar(Instant agora) {
        this.email = new Email("removido+" + id + "@anonimizado.local");
        this.nome = "Titular removido";
        this.senhaHash = "sem-acesso";
        this.ativo = false;
        this.anonimizadoEm = agora;
    }

    public boolean anonimizado() {
        return anonimizadoEm != null;
    }

    public void reativar() {
        this.ativo = true;
        this.tentativasSeguidas = 0;
        this.bloqueadoAte = null;
    }

    public boolean tem(Papel papel) {
        return papeis.contains(papel);
    }

    public boolean acessaPainel() {
        return papeis.stream().anyMatch(Papel::acessaPainel);
    }

    public boolean podePublicarCatalogo() {
        return papeis.stream().anyMatch(Papel::podePublicarCatalogo);
    }

    public boolean podeMexerEmCobranca() {
        return papeis.stream().anyMatch(Papel::podeMexerEmCobranca);
    }

    public UUID id() { return id; }

    public UUID tenantId() { return tenantId; }

    public Email email() { return email; }

    public String senhaHash() { return senhaHash; }

    public String nome() { return nome; }

    public Set<Papel> papeis() { return Set.copyOf(papeis); }

    public boolean ativo() { return ativo; }

    public int tentativasSeguidas() { return tentativasSeguidas; }

    public Instant bloqueadoAte() { return bloqueadoAte; }

    public Instant ultimoAcesso() { return ultimoAcesso; }

    public Instant criadoEm() { return criadoEm; }

    public Instant anonimizadoEm() { return anonimizadoEm; }

    public static Usuario reconstituir(UUID id, UUID tenantId, Email email, String senhaHash, String nome,
                                       Set<Papel> papeis, boolean ativo, int tentativasSeguidas,
                                       Instant bloqueadoAte, Instant ultimoAcesso, Instant criadoEm,
                                       Instant anonimizadoEm) {
        var usuario = new Usuario(id, tenantId, email, senhaHash, nome, papeis, criadoEm);
        usuario.ativo = ativo;
        usuario.anonimizadoEm = anonimizadoEm;
        usuario.tentativasSeguidas = tentativasSeguidas;
        usuario.bloqueadoAte = bloqueadoAte;
        usuario.ultimoAcesso = ultimoAcesso;
        return usuario;
    }
}
