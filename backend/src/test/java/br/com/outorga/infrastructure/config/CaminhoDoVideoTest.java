package br.com.outorga.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O nome do título virado em caminho de arquivo.
 *
 * <p>Existe por um defeito concreto: o catálogo de demonstração passou a ter
 * acento, e o filtro de letra e dígito não reconhece letra acentuada. "O Último
 * Trem da Serra" virava {@code o--ltimo-trem-da-serra}, com o buraco no lugar
 * do "Ú".
 *
 * <p>Nada quebrava na hora. O vídeo é de demonstração e ninguém iria procurá-lo
 * pelo nome; o defeito só apareceria no dia em que alguém fosse ligar o acervo
 * de verdade e não achasse o arquivo. É o tipo de erro que a acentuação do
 * catálogo reintroduz toda vez que um título novo entra, e por isso a regra
 * fica no build.
 */
@DisplayName("Caminho do vídeo a partir do nome")
class CaminhoDoVideoTest {

    private String caminhoDe(String nome) {
        try {
            Method metodo = SemeadorDeDemonstracao.class
                    .getDeclaredMethod("caminhoDe", String.class);
            metodo.setAccessible(true);
            return (String) metodo.invoke(null, nome);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("caminhoDe mudou de assinatura", e);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "'O Último Trem da Serra', o-ultimo-trem-da-serra",
            "'Noite de São João',      noite-de-sao-joao",
            "'Estrada de Terra',       estrada-de-terra",
            "'Pipoca e Foguete',       pipoca-e-foguete",
    })
    @DisplayName("o acento vira a letra sem acento, e não um separador")
    void acentoViraLetra(String nome, String esperado) {
        assertThat(caminhoDe(nome)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("cedilha e til são tratados como as letras que são")
    void cedilhaETil() {
        assertThat(caminhoDe("Coração de Ração")).isEqualTo("coracao-de-racao");
    }

    @Test
    @DisplayName("não sobra separador na ponta, que viraria pasta vazia no caminho")
    void semSeparadorNaPonta() {
        assertThat(caminhoDe("  Ácaro!  ")).isEqualTo("acaro");
    }

    @Test
    @DisplayName("nenhum caminho gerado sai fora de letra, dígito e hífen")
    void soLetraDigitoEHifen() {
        assertThat(caminhoDe("Ó, Céu! Nº 3 (2026)")).matches("[a-z0-9-]+");
    }
}
