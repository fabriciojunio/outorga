package br.com.mirante.application.ports;

import br.com.mirante.domain.audit.RegistroDeAuditoria;
import br.com.mirante.domain.billing.Assinatura;
import br.com.mirante.domain.billing.Cupom;
import br.com.mirante.domain.billing.Plano;
import br.com.mirante.domain.catalog.Titulo;
import br.com.mirante.domain.identity.Dispositivo;
import br.com.mirante.domain.identity.Email;
import br.com.mirante.domain.identity.Perfil;
import br.com.mirante.domain.identity.Usuario;
import br.com.mirante.domain.live.CanalAoVivo;
import br.com.mirante.domain.live.ProgramaEpg;
import br.com.mirante.domain.playback.SessaoDeReproducao;
import br.com.mirante.domain.rights.Licenca;
import br.com.mirante.domain.tenant.Tenant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Portas de persistencia. Ficam agrupadas porque sao contrato, nao
 * implementacao: um arquivo por interface de tres metodos so espalha o
 * contrato sem deixar nada mais claro.
 *
 * Toda consulta recebe o tenant explicitamente. O filtro por tenant tambem
 * existe no Hibernate, mas depender so dele significa que um esquecimento de
 * configuracao vaza dado de cliente. Aqui a assinatura do metodo cobra.
 */
public interface Repositorios {

    interface DeTenant {
        Optional<Tenant> porId(UUID id);

        Optional<Tenant> porSlug(String slug);

        Optional<Tenant> porDominio(String dominio);

        List<Tenant> todos();

        Tenant salvar(Tenant tenant);
    }

    interface DeUsuario {
        Optional<Usuario> porId(UUID tenantId, UUID id);

        Optional<Usuario> porEmail(UUID tenantId, Email email);

        List<Usuario> doTenant(UUID tenantId);

        boolean existeEmail(UUID tenantId, Email email);

        Usuario salvar(Usuario usuario);
    }

    interface DePerfil {
        Optional<Perfil> porId(UUID id);

        List<Perfil> doUsuario(UUID usuarioId);

        int quantidadeDoUsuario(UUID usuarioId);

        Perfil salvar(Perfil perfil);

        void remover(UUID id);
    }

    interface DeDispositivo {
        Optional<Dispositivo> porIdentificador(UUID usuarioId, String identificador);

        List<Dispositivo> doUsuario(UUID usuarioId);

        Dispositivo salvar(Dispositivo dispositivo);

        void remover(UUID id);
    }

    interface DeTitulo {
        Optional<Titulo> porId(UUID tenantId, UUID id);

        List<Titulo> publicados(UUID tenantId, int pagina, int tamanho);

        List<Titulo> buscar(UUID tenantId, String termo, int limite);

        List<Titulo> porLicenca(UUID tenantId, UUID licencaId);

        /**
         * Titulos que a varredura de direitos precisa olhar: os que estao no
         * ar e os que ela mesma tirou do ar, porque licenca vencida pode
         * voltar a vigorar e o titulo tem que voltar sozinho.
         */
        List<Titulo> sujeitosARevisaoDeDireitos(UUID tenantId);

        Titulo salvar(Titulo titulo);
    }

    interface DeLicenca {
        Optional<Licenca> porId(UUID tenantId, UUID id);

        List<Licenca> doTenant(UUID tenantId);

        List<Licenca> vencendoAte(Instant limite);

        Licenca salvar(Licenca licenca);
    }

    interface DePlano {
        Optional<Plano> porId(UUID tenantId, UUID id);

        List<Plano> ativosDoTenant(UUID tenantId);

        Plano salvar(Plano plano);
    }

    interface DeAssinatura {
        Optional<Assinatura> porId(UUID tenantId, UUID id);

        Optional<Assinatura> vigenteDoUsuario(UUID tenantId, UUID usuarioId);

        Optional<Assinatura> porReferenciaNoGateway(String referencia);

        List<Assinatura> vencendoAte(Instant limite);

        Assinatura salvar(Assinatura assinatura);
    }

    interface DeCupom {
        Optional<Cupom> porCodigo(UUID tenantId, String codigo);

        Cupom salvar(Cupom cupom);
    }

    interface DeCanal {
        Optional<CanalAoVivo> porId(UUID tenantId, UUID id);

        List<CanalAoVivo> noAr(UUID tenantId);

        List<CanalAoVivo> doTenant(UUID tenantId);

        List<CanalAoVivo> porLicenca(UUID tenantId, UUID licencaId);

        CanalAoVivo salvar(CanalAoVivo canal);
    }

    interface DeEpg {
        List<ProgramaEpg> doCanalEntre(UUID tenantId, UUID canalId, Instant de, Instant ate);

        void salvarTodos(List<ProgramaEpg> programas);
    }

    interface DeSessao {
        int abertasDoUsuario(UUID tenantId, UUID usuarioId, Instant agora);

        Optional<SessaoDeReproducao> porId(UUID id);

        SessaoDeReproducao salvar(SessaoDeReproducao sessao);

        int fecharAbandonadas(Instant limite);
    }

    interface DeAuditoria {
        void registrar(RegistroDeAuditoria registro);

        List<RegistroDeAuditoria> doTenant(UUID tenantId, Instant de, Instant ate, int limite);
    }
}
