package br.com.outorga.infrastructure.security;

import br.com.outorga.application.ports.CifradorDeSenha;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt com custo 12.
 *
 * Argon2id seria a escolha de manual, e o dominio não muda se trocarmos: e um
 * adaptador atrás de uma interface. O que decidiu foi a memória. A instancia
 * gratuita onde isso roda tem 512 MB, e o Argon2 com parâmetro decente come
 * dezenas de MB por hash simultaneo. Custo 12 de BCrypt gasta CPU, não RAM, e
 * cabe. Quando o servidor crescer, esta classe é o único lugar a mexer.
 */
@Component
public class CifradorBCrypt implements CifradorDeSenha {

    private static final int CUSTO = 12;

    private final BCryptPasswordEncoder codificador = new BCryptPasswordEncoder(CUSTO);

    @Override
    public String cifrar(String senhaEmTextoClaro) {
        return codificador.encode(senhaEmTextoClaro);
    }

    @Override
    public boolean confere(String senhaEmTextoClaro, String hash) {
        if (senhaEmTextoClaro == null || hash == null) {
            return false;
        }
        try {
            return codificador.matches(senhaEmTextoClaro, hash);
        } catch (IllegalArgumentException e) {
            // Hash em formato inválido, como o marcador de conta anonimizada.
            return false;
        }
    }
}
