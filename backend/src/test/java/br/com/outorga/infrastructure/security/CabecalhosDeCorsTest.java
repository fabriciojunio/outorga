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
 * Nome de cabecalho HTTP e um token, e token nao aceita espaco.
 *
 * Este teste existe por um erro real: a troca de nome do sistema passou por
 * "X-Mirante-Dispositivo" e devolveu "X-Outorga TV-Dispositivo". Nada quebrou
 * no build. O MockMvc guarda o nome como texto e nao reclama, entao a suite
 * inteira continuou verde, e o defeito so apareceu contra o servidor no ar,
 * onde o navegador recusa o cabecalho e o pedido de reproducao para de sair.
 *
 * Um nome de cabecalho e o tipo de coisa que uma substituicao em massa
 * atravessa sem avisar, e por isso a regra fica no build.
 */
class CabecalhosDeCorsTest {

    /** Token conforme a RFC 7230: letras, digitos e a pontuacao listada. */
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
    @DisplayName("todo cabecalho de CORS tem nome valido")
    void nomeDeCabecalhoEValido() {
        var cabecalhos = cabecalhosConfigurados();

        assertThat(cabecalhos).isNotEmpty();
        assertThat(cabecalhos)
                .allSatisfy(nome -> assertThat(TOKEN.matcher(nome).matches())
                        .as("cabecalho \"%s\" nao e um token valido", nome)
                        .isTrue());
    }

    @Test
    @DisplayName("o prefixo do produto nao carrega espaco")
    void prefixoDoProdutoNaoTemEspaco() {
        assertThat(cabecalhosConfigurados())
                .filteredOn(nome -> nome.toLowerCase().startsWith("x-outorga"))
                .isNotEmpty()
                .allSatisfy(nome -> assertThat(nome).doesNotContain(" "));
    }
}
