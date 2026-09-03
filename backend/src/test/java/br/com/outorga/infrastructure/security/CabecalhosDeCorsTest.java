package br.com.outorga.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nome de cabeçalho HTTP é um token, e token não aceita espaço.
 *
 * Este teste existe por um erro real: a troca de nome do sistema passou por
 * "X-Mirante-Dispositivo" e devolveu "X-Outorga TV-Dispositivo". Nada quebrou
 * no build. O MockMvc guarda o nome como texto e não reclama, então a suíte
 * inteira continuou verde, e o defeito só apareceu contra o servidor no ar,
 * onde o navegador recusa o cabeçalho e o pedido de reprodução para de sair.
 *
 * Um nome de cabeçalho é o tipo de coisa que uma substituição em massa
 * atravessa sem avisar, e por isso a regra fica no build.
 */
class CabecalhosDeCorsTest {

    /** Token conforme a RFC 7230: letras, dígitos e a pontuação listada. */
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9!#$%&'*+\\-.^_`|~]+");

    private List<String> cabecalhosConfigurados() {
        var fonte = (UrlBasedCorsConfigurationSource)
                new ConfiguracaoDeSeguranca("http://localhost:3000").corsConfigurationSource();

        var todos = new ArrayList<String>();
        for (CorsConfiguration config : fonte.getCorsConfigurations().values()) {
            if (config.getAllowedHeaders() != null) {
                todos.addAll(config.getAllowedHeaders());
            }
            if (config.getExposedHeaders() != null) {
                todos.addAll(config.getExposedHeaders());
            }
        }
        return todos;
    }

    @Test
    @DisplayName("todo cabeçalho de CORS tem nome válido")
    void nomeDeCabecalhoEValido() {
        var cabecalhos = cabecalhosConfigurados();

        assertThat(cabecalhos).isNotEmpty();
        assertThat(cabecalhos)
                .allSatisfy(nome -> assertThat(TOKEN.matcher(nome).matches())
                        .as("cabeçalho \"%s\" não é um token válido", nome)
                        .isTrue());
    }

    @Test
    @DisplayName("o prefixo do produto não carrega espaço")
    void prefixoDoProdutoNaoTemEspaco() {
        assertThat(cabecalhosConfigurados())
                .filteredOn(nome -> nome.toLowerCase().startsWith("x-outorga"))
                .isNotEmpty()
                .allSatisfy(nome -> assertThat(nome).doesNotContain(" "));
    }
}
