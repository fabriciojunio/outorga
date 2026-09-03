package br.com.outorga.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Resposta para requisicao sem autenticação.
 *
 * O padrão do Spring aqui e 403, o que confunde: 403 diz "você não pode", e o
 * caso e "você ainda não disse quem e". A diferença importa para o cliente,
 * que precisa mandar para a tela de login em um caso e mostrar "sem permissão"
 * no outro. O corpo sai no mesmo formato de todos os erros da API.
 */
public class RespostaDeNaoAutenticado implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest requisicao, HttpServletResponse resposta,
                         AuthenticationException excecao) throws IOException {
        resposta.setStatus(HttpStatus.UNAUTHORIZED.value());
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        resposta.getWriter().write("""
                {"codigo":"NAO_AUTENTICADO","mensagem":"Entre na sua conta para continuar",\
                "detalhes":{}}""");
    }
}
