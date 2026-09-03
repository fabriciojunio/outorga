package br.com.outorga.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Segurança HTTP.
 *
 * A API e sem estado e sem cookie de sessão, então CSRF não se aplica e fica
 * desligado por escolha, não por esquecimento. O que protege escrita e o
 * token no cabeçalho, que nenhum navegador manda sozinho.
 */
@Configuration
@EnableMethodSecurity
public class ConfiguracaoDeSeguranca {

    private final String[] origensPermitidas;

    public ConfiguracaoDeSeguranca(
            @org.springframework.beans.factory.annotation.Value("${outorga.origens-permitidas:http://localhost:3000}")
            String origens) {
        this.origensPermitidas = origens.split(",");
    }

    @Bean
    public SecurityFilterChain corrente(HttpSecurity http, FiltroDeAutenticacao filtro) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // Sem isto o Spring devolve 403 para quem nem mandou token, e
                // o cliente não consegue distinguir "faca login" de "você não
                // tem permissão", que são coisas diferentes na tela.
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(new RespostaDeNaoAutenticado()))
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(f -> f.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'"))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))
                .authorizeHttpRequests(rotas -> rotas
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/publico/**",
                                "/api/v1/webhooks/**",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN_PLATAFORMA")
                        .anyRequest().authenticated())
                .addFilterBefore(filtro,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origensPermitidas));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Outorga-Tenant",
                "X-Outorga-Dispositivo", "X-Outorga-Perfil"));
        config.setExposedHeaders(List.of("X-Outorga-Requisicao"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        var fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", config);
        return fonte;
    }
}
