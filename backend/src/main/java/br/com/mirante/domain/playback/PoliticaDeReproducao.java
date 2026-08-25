package br.com.mirante.domain.playback;

import br.com.mirante.domain.catalog.StatusDePublicacao;
import br.com.mirante.shared.FalhaDeNegocio;
import br.com.mirante.shared.Result;

import java.time.Duration;
import java.util.UUID;

/**
 * A decisao de deixar alguem dar play. Fica em um lugar so, de proposito.
 *
 * A ordem das checagens nao e aleatoria. Primeiro o que e do contrato do
 * cliente com o Mirante, depois o que e do contrato do assinante com o
 * cliente, depois o direito sobre a obra, e so no fim o detalhe tecnico. Assim
 * a mensagem que chega no espectador explica a causa de verdade, em vez de
 * dizer "conteudo indisponivel" para tudo.
 */
public final class PoliticaDeReproducao {

    /** Vida do token entregue ao player. Curto por escolha: renova, nao alonga. */
    public static final Duration VALIDADE_DO_TOKEN = Duration.ofMinutes(5);

    public Result<Autorizacao> decidir(ContextoDeReproducao ctx) {
        if (!ctx.tenant().aceitaTrafegoDeEspectador(ctx.agora())) {
            return Result.erro(new FalhaDeNegocio("SERVICO_INDISPONIVEL",
                    "Este servico esta temporariamente fora do ar")
                    .com("statusDoTenant", ctx.tenant().status().name()));
        }

        if (ctx.assinatura() == null || !ctx.assinatura().permiteAssistir(ctx.agora())) {
            return Result.erro(new FalhaDeNegocio("ASSINATURA_SEM_ACESSO",
                    "Assinatura sem acesso ativo"));
        }

        if (ctx.titulo() == null) {
            return Result.erro(new FalhaDeNegocio("TITULO_NAO_ENCONTRADO",
                    "Titulo nao encontrado"));
        }
        if (!ctx.titulo().tenantId().equals(ctx.tenant().id())) {
            return Result.erro(new FalhaDeNegocio("TITULO_DE_OUTRO_TENANT",
                    "Titulo nao pertence a este servico"));
        }
        if (!ctx.titulo().noAr()) {
            return Result.erro(new FalhaDeNegocio("TITULO_FORA_DO_AR",
                    ctx.titulo().status() == StatusDePublicacao.BLOQUEADO_POR_DIREITO
                            ? "Titulo indisponivel por questao de direitos"
                            : "Titulo indisponivel no momento"));
        }

        if (ctx.licenca() == null) {
            return Result.erro(new FalhaDeNegocio("SEM_LICENCA",
                    "Titulo sem licenca vinculada"));
        }
        if (!ctx.licenca().vigenteEm(ctx.agora())) {
            return Result.erro(new FalhaDeNegocio("LICENCA_NAO_VIGENTE",
                    "Titulo indisponivel por questao de direitos"));
        }
        if (!ctx.licenca().cobreTerritorio(ctx.territorio())) {
            return Result.erro(new FalhaDeNegocio("FORA_DO_TERRITORIO",
                    "Titulo nao disponivel na sua regiao")
                    .com("territorio", ctx.territorio().codigo()));
        }
        if (!ctx.licenca().dispositivosAutorizados().contains(ctx.dispositivo().tipo())) {
            return Result.erro(new FalhaDeNegocio("DISPOSITIVO_NAO_LICENCIADO",
                    "Este titulo nao pode ser exibido neste tipo de aparelho")
                    .com("dispositivo", ctx.dispositivo().tipo().name()));
        }

        if (ctx.perfil() != null && !ctx.titulo().visivelPara(ctx.perfil().tetoDeClassificacao())) {
            return Result.erro(new FalhaDeNegocio("BLOQUEADO_PELO_CONTROLE_PARENTAL",
                    "Conteudo acima da classificacao liberada neste perfil")
                    .com("classificacao", ctx.titulo().classificacao().rotulo()));
        }

        if (ctx.sessoesAtivas() >= ctx.plano().telasSimultaneas()) {
            return Result.erro(new FalhaDeNegocio("LIMITE_DE_TELAS",
                    "Voce atingiu o limite de " + ctx.plano().telasSimultaneas()
                            + " telas ao mesmo tempo")
                    .com("telasDoPlano", ctx.plano().telasSimultaneas()));
        }

        if (ctx.referenciaDoVideo() == null || ctx.referenciaDoVideo().isBlank()) {
            return Result.erro(new FalhaDeNegocio("VIDEO_INDISPONIVEL",
                    "O arquivo de video ainda nao esta pronto"));
        }

        return Result.ok(new Autorizacao(
                UUID.randomUUID(),
                ctx.tenant().id(),
                ctx.perfil() == null ? null : ctx.perfil().id(),
                ctx.titulo().id(),
                ctx.referenciaDoVideo(),
                ctx.plano().limitar(ctx.qualidadePedida()),
                ctx.agora().plus(VALIDADE_DO_TOKEN),
                ctx.licenca().id()));
    }
}
