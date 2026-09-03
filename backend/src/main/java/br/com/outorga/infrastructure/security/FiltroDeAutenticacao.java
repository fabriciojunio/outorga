package br.com.outorga.infrastructure.security;

import br.com.outorga.application.ports.EmissorDeToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Le o token do cabeçalho e coloca a identidade no contexto. Token ausente ou
 * inválido não para a requisicao aqui: quem decide se aquele endereço exige
 * autenticação e a configuração de segurança.
 */
@Component
public class FiltroDeAutenticacao extends OncePerRequestFilter {

    private final EmissorDeToken emissor;

    public FiltroDeAutenticacao(EmissorDeToken emissor) {
        this.emissor = emissor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta,
                                    FilterChain corrente) throws ServletException, IOException {
        var cabecalho = requisicao.getHeader("Authorization");
        if (cabecalho != null && cabecalho.startsWith("Bearer ")) {
            var conteudo = emissor.validarAcesso(cabecalho.substring(7).trim());
            if (conteudo.sucesso()) {
                var dados = conteudo.valorOuFalha();
                SecurityContextHolder.getContext().setAuthentication(new UsuarioAutenticado(
                        dados.tenantId(), dados.usuarioId(), dados.papeis(), ipDe(requisicao)));
            }
        }
        corrente.doFilter(requisicao, resposta);
    }

    /**
     * Atrás de proxy o IP real vem no X-Forwarded-For. Pega só o primeiro
     * salto: o resto da lista e o proxy contando a própria historia e não
     * serve para auditoria.
     */
    public static String ipDe(HttpServletRequest requisicao) {
        var encaminhado = requisicao.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",")[0].trim();
        }
        return requisicao.getRemoteAddr();
    }
}
