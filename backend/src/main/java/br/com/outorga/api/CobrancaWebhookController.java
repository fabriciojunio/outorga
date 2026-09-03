package br.com.outorga.api;

import br.com.outorga.application.usecases.billing.ProcessarEventoDeCobranca;
import br.com.outorga.infrastructure.config.ConfiguracaoDaOutorga;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Recebimento de eventos de cobrança e, em demonstração, o simulador que os
 * dispara.
 *
 * O webhook responde 200 para quase tudo que consegue interpretar, inclusive
 * para cobrança que não bate com nenhuma assinatura daqui. Gateway que recebe
 * erro reentrega, e reentrega em laco de um evento que nunca vai casar só
 * enche o log e atrasa os eventos que importam.
 */
@RestController
@RequestMapping("/api/v1")
public class CobrancaWebhookController {

    private final ProcessarEventoDeCobranca processar;
    private final ConfiguracaoDaOutorga configuracao;

    public CobrancaWebhookController(ProcessarEventoDeCobranca processar,
                                     ConfiguracaoDaOutorga configuracao) {
        this.processar = processar;
        this.configuracao = configuracao;
    }

    @PostMapping("/webhooks/pagamento")
    public ResponseEntity<?> receber(@RequestHeader Map<String, String> cabecalhos,
                                     @RequestBody String corpo) {
        var normalizados = new LinkedHashMap<String, String>();
        cabecalhos.forEach((chave, valor) -> normalizados.put(chave.toLowerCase(Locale.ROOT), valor));
        return Respostas.de(processar.executar(normalizados, corpo),
                mensagem -> Map.of("resultado", mensagem));
    }

    /**
     * Tela de pagamento simulada. Existe só no modo demonstração e serve para
     * a demonstração comercial: da para mostrar o fluxo inteiro de assinatura,
     * do clique até a liberacao do catálogo, sem gateway contratado.
     */
    @GetMapping(value = "/publico/checkout-simulado", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> checkoutSimulado(@RequestParam String referencia,
                                                   @RequestParam long valor) {
        if (!configuracao.emDemonstracao()) {
            return ResponseEntity.notFound().build();
        }
        var reais = String.format(Locale.of("pt", "BR"), "R$ %,.2f", valor / 100.0);
        var html = """
                <!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Pagamento simulado</title>
                <style>
                  body{background:#0d0f14;color:#e8eaee;font:16px system-ui,sans-serif;
                       display:grid;place-items:center;min-height:100vh;margin:0}
                  .cartao{background:#161a22;border:1px solid #262c38;border-radius:14px;
                          padding:32px;max-width:420px;width:calc(100%% - 32px)}
                  h1{font-size:20px;margin:0 0 4px}
                  p{color:#98a2b3;line-height:1.5}
                  .valor{font-size:34px;font-weight:700;color:#e6b800;margin:16px 0}
                  button{width:100%%;padding:14px;border:0;border-radius:10px;font-size:15px;
                         font-weight:600;cursor:pointer;margin-top:10px}
                  .ok{background:#e6b800;color:#161a22}
                  .nao{background:transparent;color:#98a2b3;border:1px solid #262c38}
                  .aviso{margin-top:20px;font-size:13px;color:#f0a04b;
                         border-top:1px solid #262c38;padding-top:16px}
                </style></head><body><div class="cartao">
                <h1>Pagamento simulado</h1>
                <p>Cobranca <code>%s</code></p>
                <div class="valor">%s</div>
                <button class="ok" onclick="responder('CONFIRMADO')">Confirmar pagamento</button>
                <button class="nao" onclick="responder('RECUSADO')">Simular recusa</button>
                <p class="aviso">Ambiente de demonstracao. Nenhum valor e cobrado
                e nenhum dado de cartao e pedido.</p>
                <p id="saida"></p></div>
                <script>
                async function responder(evento){
                  const r = await fetch('/api/v1/webhooks/pagamento',{
                    method:'POST',
                    headers:{'Content-Type':'application/json','x-outorga-origem':'simulador'},
                    body: JSON.stringify({evento, referencia:'%s', centavos:%d})
                  });
                  const corpo = await r.json();
                  document.getElementById('saida').textContent =
                    r.ok ? 'Pronto. Pode voltar para o aplicativo.' : ('Falhou: ' + JSON.stringify(corpo));
                }
                </script></body></html>
                """.formatted(referencia, reais, referencia, valor);
        return ResponseEntity.ok(html);
    }
}
