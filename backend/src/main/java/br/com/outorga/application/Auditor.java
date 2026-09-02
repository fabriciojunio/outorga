package br.com.outorga.application;

import br.com.outorga.application.ports.Repositorios;
import br.com.outorga.domain.audit.AcaoAuditavel;
import br.com.outorga.domain.audit.RegistroDeAuditoria;

import java.time.Clock;
import java.util.Map;

/**
 * Atalho para escrever na trilha sem repetir montagem de registro em cada
 * caso de uso.
 */
public class Auditor {

    private final Repositorios.DeAuditoria repositorio;
    private final Clock relogio;

    public Auditor(Repositorios.DeAuditoria repositorio, Clock relogio) {
        this.repositorio = repositorio;
        this.relogio = relogio;
    }

    public void registrar(ContextoDoChamador chamador, AcaoAuditavel acao, String recursoTipo,
                          String recursoId, Map<String, String> detalhes) {
        repositorio.registrar(RegistroDeAuditoria.de(
                chamador.tenantId(),
                chamador.usuarioId(),
                chamador.descricao(),
                acao,
                recursoTipo,
                recursoId,
                chamador.enderecoIp(),
                detalhes,
                relogio.instant()));
    }

    public void registrar(ContextoDoChamador chamador, AcaoAuditavel acao, String recursoTipo,
                          String recursoId) {
        registrar(chamador, acao, recursoTipo, recursoId, Map.of());
    }
}
