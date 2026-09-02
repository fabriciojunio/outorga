package br.com.outorga.domain.billing;

import br.com.outorga.domain.billing.EventoDaAssinatura.TipoDeEvento;
import br.com.outorga.shared.FalhaDeNegocio;
import br.com.outorga.shared.Result;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Assinatura de um espectador ao servico de um cliente.
 *
 * A carencia depois de uma cobranca falha e deliberada: cartao recusado por
 * limite volta a passar em dois dias na maioria das vezes, e derrubar o acesso
 * no mesmo minuto gera cancelamento que nao precisava acontecer.
 */
public class Assinatura {

    public static final Duration CARENCIA_POR_INADIMPLENCIA = Duration.ofDays(3);

    private final UUID id;
    private final UUID tenantId;
    private final UUID usuarioId;
    private UUID planoId;
    private StatusDaAssinatura status;
    private final Instant iniciadaEm;
    private Instant fimDoCicloAtual;
    private Instant fimDaCarencia;
    private Instant encerradaEm;
    private String referenciaNoGateway;
    private final List<EventoDaAssinatura> eventos = new ArrayList<>();

    private Assinatura(UUID id, UUID tenantId, UUID usuarioId, UUID planoId, Instant iniciadaEm) {
        this.id = id;
        this.tenantId = tenantId;
        this.usuarioId = usuarioId;
        this.planoId = planoId;
        this.iniciadaEm = iniciadaEm;
    }

    public static Result<Assinatura> abrir(UUID tenantId, UUID usuarioId, Plano plano, Instant agora) {
        if (tenantId == null || usuarioId == null || plano == null) {
            return Result.erro(new FalhaDeNegocio("ASSINATURA_INCOMPLETA",
                    "Informe tenant, assinante e plano"));
        }
        if (!plano.ativo()) {
            return Result.erro(new FalhaDeNegocio("PLANO_INATIVO",
                    "Este plano nao esta mais em venda"));
        }
        if (!plano.tenantId().equals(tenantId)) {
            return Result.erro(new FalhaDeNegocio("PLANO_DE_OUTRO_TENANT",
                    "O plano informado pertence a outro tenant"));
        }
        var assinatura = new Assinatura(UUID.randomUUID(), tenantId, usuarioId, plano.id(), agora);
        assinatura.registrar(TipoDeEvento.CRIADA, plano.nome(), agora);

        if (plano.diasDeTeste() > 0) {
            assinatura.status = StatusDaAssinatura.EM_TESTE;
            assinatura.fimDoCicloAtual = agora.plus(Duration.ofDays(plano.diasDeTeste()));
            assinatura.registrar(TipoDeEvento.TESTE_INICIADO,
                    plano.diasDeTeste() + " dias", agora);
        } else {
            assinatura.status = StatusDaAssinatura.INADIMPLENTE;
            assinatura.fimDoCicloAtual = agora;
            assinatura.fimDaCarencia = agora;
        }
        return Result.ok(assinatura);
    }

    public Result<Assinatura> confirmarPagamento(Plano plano, Instant agora) {
        if (status == StatusDaAssinatura.ENCERRADA) {
            return Result.erro(new FalhaDeNegocio("ASSINATURA_ENCERRADA",
                    "Assinatura encerrada. Abra uma nova"));
        }
        var base = fimDoCicloAtual != null && fimDoCicloAtual.isAfter(agora) ? fimDoCicloAtual : agora;
        this.fimDoCicloAtual = plano.periodicidade().proximoVencimento(base);
        this.fimDaCarencia = null;
        boolean voltando = status == StatusDaAssinatura.INADIMPLENTE
                || status == StatusDaAssinatura.CANCELADA;
        this.status = StatusDaAssinatura.ATIVA;
        registrar(TipoDeEvento.PAGAMENTO_CONFIRMADO,
                "ciclo ate " + fimDoCicloAtual, agora);
        if (voltando) {
            registrar(TipoDeEvento.REATIVADA, null, agora);
        }
        return Result.ok(this);
    }

    public Result<Assinatura> registrarFalhaDePagamento(String motivo, Instant agora) {
        if (status == StatusDaAssinatura.ENCERRADA) {
            return Result.erro(new FalhaDeNegocio("ASSINATURA_ENCERRADA",
                    "Assinatura ja encerrada"));
        }
        registrar(TipoDeEvento.PAGAMENTO_FALHOU, motivo, agora);
        if (status != StatusDaAssinatura.INADIMPLENTE) {
            this.status = StatusDaAssinatura.INADIMPLENTE;
            this.fimDaCarencia = agora.plus(CARENCIA_POR_INADIMPLENCIA);
            registrar(TipoDeEvento.ENTROU_EM_CARENCIA,
                    "acesso ate " + fimDaCarencia, agora);
        }
        return Result.ok(this);
    }

    /** Cancelamento a pedido: para de renovar, mas o ciclo pago vale ate o fim. */
    public Result<Assinatura> cancelar(String motivo, Instant agora) {
        if (status == StatusDaAssinatura.ENCERRADA) {
            return Result.erro(new FalhaDeNegocio("ASSINATURA_ENCERRADA",
                    "Assinatura ja encerrada"));
        }
        if (status == StatusDaAssinatura.CANCELADA) {
            return Result.erro(new FalhaDeNegocio("CANCELAMENTO_JA_PEDIDO",
                    "O cancelamento ja tinha sido pedido"));
        }
        this.status = StatusDaAssinatura.CANCELADA;
        registrar(TipoDeEvento.CANCELAMENTO_PEDIDO, motivo, agora);
        return Result.ok(this);
    }

    /**
     * Passagem do tempo. Encerra o que venceu de vez, sem depender de alguem
     * clicar em nada. Retorna verdadeiro quando o status mudou.
     */
    public boolean aplicarPassagemDoTempo(Instant agora) {
        if (status == StatusDaAssinatura.ENCERRADA) {
            return false;
        }
        if (permiteAssistir(agora)) {
            return false;
        }
        this.status = StatusDaAssinatura.ENCERRADA;
        this.encerradaEm = agora;
        registrar(TipoDeEvento.ENCERRADA, "vencimento sem pagamento", agora);
        return true;
    }

    /** A pergunta que o playback faz. */
    public boolean permiteAssistir(Instant agora) {
        return switch (status) {
            case ATIVA, EM_TESTE, CANCELADA -> fimDoCicloAtual != null && agora.isBefore(fimDoCicloAtual);
            case INADIMPLENTE -> dentroDaCarencia(agora) || dentroDoCicloPago(agora);
            case ENCERRADA -> false;
        };
    }

    private boolean dentroDaCarencia(Instant agora) {
        return fimDaCarencia != null && agora.isBefore(fimDaCarencia);
    }

    private boolean dentroDoCicloPago(Instant agora) {
        return fimDoCicloAtual != null && agora.isBefore(fimDoCicloAtual);
    }

    public Result<Assinatura> trocarPlano(Plano novo, Instant agora) {
        if (novo == null || !novo.ativo()) {
            return Result.erro(new FalhaDeNegocio("PLANO_INATIVO", "Plano indisponivel"));
        }
        if (!novo.tenantId().equals(tenantId)) {
            return Result.erro(new FalhaDeNegocio("PLANO_DE_OUTRO_TENANT",
                    "O plano informado pertence a outro tenant"));
        }
        if (novo.id().equals(planoId)) {
            return Result.erro(new FalhaDeNegocio("MESMO_PLANO", "O assinante ja esta neste plano"));
        }
        this.planoId = novo.id();
        registrar(TipoDeEvento.PLANO_TROCADO, novo.nome(), agora);
        return Result.ok(this);
    }

    public void vincularAoGateway(String referencia) {
        this.referenciaNoGateway = referencia;
    }

    private void registrar(TipoDeEvento tipo, String detalhe, Instant agora) {
        eventos.add(EventoDaAssinatura.de(id, tipo, detalhe, agora));
    }

    public UUID id() { return id; }

    public UUID tenantId() { return tenantId; }

    public UUID usuarioId() { return usuarioId; }

    public UUID planoId() { return planoId; }

    public StatusDaAssinatura status() { return status; }

    public Instant iniciadaEm() { return iniciadaEm; }

    public Instant fimDoCicloAtual() { return fimDoCicloAtual; }

    public Instant fimDaCarencia() { return fimDaCarencia; }

    public Instant encerradaEm() { return encerradaEm; }

    public String referenciaNoGateway() { return referenciaNoGateway; }

    public List<EventoDaAssinatura> eventos() { return List.copyOf(eventos); }

    public static Assinatura reconstituir(UUID id, UUID tenantId, UUID usuarioId, UUID planoId,
                                          StatusDaAssinatura status, Instant iniciadaEm,
                                          Instant fimDoCicloAtual, Instant fimDaCarencia,
                                          Instant encerradaEm, String referenciaNoGateway,
                                          List<EventoDaAssinatura> eventos) {
        var assinatura = new Assinatura(id, tenantId, usuarioId, planoId, iniciadaEm);
        assinatura.status = status;
        assinatura.fimDoCicloAtual = fimDoCicloAtual;
        assinatura.fimDaCarencia = fimDaCarencia;
        assinatura.encerradaEm = encerradaEm;
        assinatura.referenciaNoGateway = referenciaNoGateway;
        assinatura.eventos.addAll(eventos);
        return assinatura;
    }
}
