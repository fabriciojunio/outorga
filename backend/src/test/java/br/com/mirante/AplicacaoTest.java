package br.com.mirante;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sobe a aplicacao inteira contra um Postgres de verdade e percorre o caminho
 * que um espectador faz de fato: abre a vitrine, entra, pede play, e o
 * servidor responde.
 *
 * E o teste que responde a pergunta que nenhum teste de unidade responde: as
 * pecas, ligadas do jeito que serao ligadas em producao, funcionam? Aqui
 * passam a configuracao de seguranca, o mapeamento de rota, o serializador,
 * as migracoes, a carga de demonstracao e a escolha dos adaptadores.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Aplicacao de ponta a ponta")
class AplicacaoTest {

    private static EmbeddedPostgres postgres;

    @DynamicPropertySource
    static void apontarParaOBancoDeTeste(DynamicPropertyRegistry propriedades) throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        propriedades.add("spring.datasource.url",
                () -> postgres.getJdbcUrl("postgres", "postgres"));
        propriedades.add("spring.datasource.username", () -> "postgres");
        propriedades.add("spring.datasource.password", () -> "");
        propriedades.add("mirante.modo", () -> "DEMONSTRACAO");
        propriedades.add("mirante.autenticacao.segredo",
                () -> "segredo-de-teste-com-mais-de-sessenta-e-quatro-caracteres-para-o-hmac-ok");
        propriedades.add("mirante.url-publica-da-api", () -> "http://localhost");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private static String tokenDoEspectador;
    private static String tokenDoDono;
    private static String tituloId;

    @Test
    @Order(1)
    @DisplayName("a carga de demonstracao deixou o servico no ar")
    void identidadeDoServico() throws Exception {
        mvc.perform(get("/api/v1/publico/cineserra/identidade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("cineserra"))
                .andExpect(jsonPath("$.nome").value("Cine Serra"))
                .andExpect(jsonPath("$.aceitandoAcesso").value(true));
    }

    @Test
    @Order(2)
    @DisplayName("o catalogo publico lista so o que tem licenca vigente")
    void catalogoPublico() throws Exception {
        var resposta = mvc.perform(get("/api/v1/publico/cineserra/catalogo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn().getResponse().getContentAsString();

        var titulos = json.readTree(resposta);
        assertThat(titulos).isNotEmpty();

        // De proposito um filme, e nao a serie: serie so reproduz com
        // temporada e episodio informados, e o teste de play quer exercitar o
        // caminho feliz, nao a recusa por falta de episodio.
        for (var titulo : titulos) {
            if ("FILME".equals(titulo.get("tipo").asText())) {
                tituloId = titulo.get("id").asText();
                break;
            }
        }
        assertThat(tituloId).isNotNull();

        // A vista publica nao pode carregar referencia de video nem licenca.
        assertThat(resposta).doesNotContain("referenciaDoVideo").doesNotContain("licencaId");
    }

    @Test
    @Order(3)
    @DisplayName("a tabela de precos vem do banco, nao do codigo")
    void planosPublicos() throws Exception {
        mvc.perform(get("/api/v1/publico/cineserra/planos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].preco").value("R$ 14,90"))
                .andExpect(jsonPath("$[1].preco").value("R$ 24,90"));
    }

    @Test
    @Order(4)
    @DisplayName("servico que nao existe devolve 404")
    void servicoInexistente() throws Exception {
        mvc.perform(get("/api/v1/publico/naoexiste/identidade"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    @Test
    @Order(5)
    @DisplayName("login com senha errada devolve 401 sem contar qual campo errou")
    void loginRecusado() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "servico", "cineserra",
                                "email", "espectador@exemplo.com",
                                "senha", "chute"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIAL_INVALIDA"))
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha incorretos"));
    }

    @Test
    @Order(6)
    @DisplayName("login certo devolve os dois tokens")
    void loginAceito() throws Exception {
        var corpo = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "servico", "cineserra",
                                "email", "espectador@exemplo.com",
                                "senha", "demonstracao2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acesso").isNotEmpty())
                .andExpect(jsonPath("$.refresh").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        tokenDoEspectador = json.readTree(corpo).get("acesso").asText();
    }

    @Test
    @Order(7)
    @DisplayName("area logada sem token devolve 401")
    void areaLogadaExigeToken() throws Exception {
        mvc.perform(get("/api/v1/me/perfis")).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(8)
    @DisplayName("o espectador enxerga os proprios perfis")
    void perfisDoEspectador() throws Exception {
        mvc.perform(get("/api/v1/me/perfis").header("Authorization", "Bearer " + tokenDoEspectador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @Order(9)
    @DisplayName("o espectador nao entra no painel do operador")
    void espectadorNaoEntraNoPainel() throws Exception {
        mvc.perform(get("/api/v1/painel/licencas")
                        .header("Authorization", "Bearer " + tokenDoEspectador))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    @DisplayName("play autorizado devolve manifesto e abre sessao")
    void playAutorizado() throws Exception {
        mvc.perform(post("/api/v1/reproducao/token")
                        .header("Authorization", "Bearer " + tokenDoEspectador)
                        .header("X-Mirante-Dispositivo", "aparelho-de-teste-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "tituloId", tituloId,
                                "tipoDeDispositivo", "WEB",
                                "territorio", "BR",
                                "qualidade", "ULTRA_HD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessaoId").isNotEmpty())
                .andExpect(jsonPath("$.manifesto").isNotEmpty())
                // O plano Familia limita em Full HD: pedir 4K nao entrega 4K.
                .andExpect(jsonPath("$.qualidade").value("FULL_HD"));
    }

    @Test
    @Order(11)
    @DisplayName("play de titulo inexistente e recusado com o motivo certo")
    void playDeTituloInexistente() throws Exception {
        mvc.perform(post("/api/v1/reproducao/token")
                        .header("Authorization", "Bearer " + tokenDoEspectador)
                        .header("X-Mirante-Dispositivo", "aparelho-de-teste-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "tituloId", "00000000-0000-0000-0000-000000000000",
                                "tipoDeDispositivo", "WEB"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TITULO_NAO_ENCONTRADO"));
    }

    @Test
    @Order(12)
    @DisplayName("o dono do servico entra no painel e ve as licencas")
    void donoVeOPainel() throws Exception {
        var corpo = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "servico", "cineserra",
                                "email", "dono@cineserra.com.br",
                                "senha", "demonstracao2026"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        tokenDoDono = json.readTree(corpo).get("acesso").asText();

        mvc.perform(get("/api/v1/painel/licencas").header("Authorization", "Bearer " + tokenDoDono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @Order(13)
    @DisplayName("o painel avisa da licenca que vence em dois dias")
    void painelAvisaVencimento() throws Exception {
        mvc.perform(get("/api/v1/painel/licencas/a-vencer")
                        .header("Authorization", "Bearer " + tokenDoDono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].titular").value("Distribuidora Cerrado"));
    }

    @Test
    @Order(14)
    @DisplayName("o dono nao abre cliente novo: isso e da plataforma")
    void donoNaoAbreCliente() throws Exception {
        mvc.perform(get("/api/v1/plataforma/clientes")
                        .header("Authorization", "Bearer " + tokenDoDono))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(15)
    @DisplayName("webhook sem autenticacao e recusado")
    void webhookSemAutenticacao() throws Exception {
        mvc.perform(post("/api/v1/webhooks/pagamento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evento\":\"CONFIRMADO\",\"referencia\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("WEBHOOK_NAO_AUTENTICO"));
    }

    @Test
    @Order(16)
    @DisplayName("o simulador de pagamento responde em modo demonstracao")
    void simuladorDePagamento() throws Exception {
        mvc.perform(get("/api/v1/publico/checkout-simulado")
                        .param("referencia", "demo_teste")
                        .param("valor", "2490"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(17)
    @DisplayName("o assinante exporta os proprios dados")
    void exportacaoLgpd() throws Exception {
        mvc.perform(get("/api/v1/me/meus-dados")
                        .header("Authorization", "Bearer " + tokenDoEspectador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("espectador@exemplo.com"))
                .andExpect(jsonPath("$.perfis.length()").value(2));
    }

    @Test
    @Order(18)
    @DisplayName("nao apaga conta com assinatura ativa")
    void naoApagaContaComAssinatura() throws Exception {
        mvc.perform(delete("/api/v1/me/minha-conta")
                        .header("Authorization", "Bearer " + tokenDoEspectador))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLITO"));
    }

    @Test
    @Order(19)
    @DisplayName("a sonda de saude responde sem token")
    void sondaDeSaude() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @Order(20)
    @DisplayName("a documentacao da API esta publicada")
    void documentacaoPublicada() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/reproducao/token']").exists());
    }
}
