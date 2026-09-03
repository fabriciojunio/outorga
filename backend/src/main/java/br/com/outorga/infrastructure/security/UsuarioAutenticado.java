package br.com.outorga.infrastructure.security;

import br.com.outorga.application.ContextoDoChamador;
import br.com.outorga.domain.identity.Papel;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;
import java.util.UUID;

/**
 * Identidade autenticada, do jeito que o Spring Security espera, mas
 * carregando o tenant. Os papeis viram authorities com prefixo ROLE_ para
 * funcionar com as anotacoes padrão.
 */
public class UsuarioAutenticado extends AbstractAuthenticationToken {

    private final UUID tenantId;
    private final UUID usuarioId;
    private final Set<Papel> papeis;
    private final String enderecoIp;

    public UsuarioAutenticado(UUID tenantId, UUID usuarioId, Set<Papel> papeis, String enderecoIp) {
        super(papeis.stream()
                .map(p -> new SimpleGrantedAuthority("ROLE_" + p.name()))
                .map(a -> (org.springframework.security.core.GrantedAuthority) a)
                .toList());
        this.tenantId = tenantId;
        this.usuarioId = usuarioId;
        this.papeis = papeis;
        this.enderecoIp = enderecoIp;
        setAuthenticated(true);
    }

    public ContextoDoChamador contexto() {
        return new ContextoDoChamador(tenantId, usuarioId, usuarioId.toString(), papeis, enderecoIp);
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public Set<Papel> papeis() {
        return papeis;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return usuarioId;
    }
}
