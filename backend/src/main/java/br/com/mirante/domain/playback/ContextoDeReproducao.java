package br.com.mirante.domain.playback;

import br.com.mirante.domain.billing.Assinatura;
import br.com.mirante.domain.billing.Plano;
import br.com.mirante.domain.billing.Qualidade;
import br.com.mirante.domain.catalog.Titulo;
import br.com.mirante.domain.identity.Dispositivo;
import br.com.mirante.domain.identity.Perfil;
import br.com.mirante.domain.rights.Licenca;
import br.com.mirante.domain.rights.Territorio;
import br.com.mirante.domain.tenant.Tenant;

import java.time.Instant;

/**
 * Tudo que a decisao de reproduzir precisa saber, ja carregado. A politica e
 * pura: recebe fatos, devolve decisao, nao vai ao banco.
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
