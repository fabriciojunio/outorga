package br.com.outorga.domain.playback;

import br.com.outorga.domain.billing.Assinatura;
import br.com.outorga.domain.billing.Plano;
import br.com.outorga.domain.billing.Qualidade;
import br.com.outorga.domain.catalog.Titulo;
import br.com.outorga.domain.identity.Dispositivo;
import br.com.outorga.domain.identity.Perfil;
import br.com.outorga.domain.rights.Licenca;
import br.com.outorga.domain.rights.Territorio;
import br.com.outorga.domain.tenant.Tenant;

import java.time.Instant;

/**
 * Tudo que a decisão de reproduzir precisa saber, já carregado. A politica e
 * pura: recebe fatos, devolve decisão, não vai ao banco.
 */
public record ContextoDeReproducao(
        Tenant tenant,
        Perfil perfil,
        Assinatura assinatura,
        Plano plano,
        Titulo titulo,
        Licenca licenca,
        Dispositivo dispositivo,
        Territorio territorio,
        Qualidade qualidadePedida,
        int sessoesAtivas,
        String referenciaDoVideo,
        Instant agora) {
}
