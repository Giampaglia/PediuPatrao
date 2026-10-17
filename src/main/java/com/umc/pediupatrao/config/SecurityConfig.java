package com.umc.pediupatrao.config;

import com.umc.pediupatrao.security.AcessoNegadoHandler;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Autorização em duas camadas:
 *  1. regras por URL (abaixo), aplicadas a páginas e APIs;
 *  2. @PreAuthorize nos serviços (habilitado por @EnableMethodSecurity), que
 *     protege as regras mesmo se um novo controlador esquecer a regra de URL.
 *
 * CSRF permanece ativo: formulários Thymeleaf recebem o token automaticamente
 * e as chamadas fetch() enviam o token via cabeçalho (static/js/api.js).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String GERENTE = "GERENTE";
    private static final String ATENDENTE = "ATENDENTE";

    private final UsuarioDetailsService usuarioDetailsService;
    private final AcessoNegadoHandler acessoNegadoHandler;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService, AcessoNegadoHandler acessoNegadoHandler) {
        this.usuarioDetailsService = usuarioDetailsService;
        this.acessoNegadoHandler = acessoNegadoHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**").permitAll()

                        // Administração de usuários e perfis: somente ADMIN
                        .requestMatchers("/usuarios/**", "/api/usuarios/**", "/admin").hasRole(ADMIN)

                        // Auditoria: somente leitura, para ADMIN e GERENTE
                        .requestMatchers(HttpMethod.GET, "/auditoria/**", "/api/auditoria/**").hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers("/auditoria/**", "/api/auditoria/**").denyAll()

                        // Desconto e cancelamento: exclusivos do GERENTE (inclusive em relação ao ADMIN)
                        .requestMatchers("/pedidos/*/desconto", "/pedidos/*/cancelar",
                                "/api/pedidos/*/desconto", "/api/pedidos/*/cancelar").hasRole(GERENTE)

                        // Pedidos: todos consultam; ATENDENTE e GERENTE registram e movimentam
                        .requestMatchers("/pedidos/novo").hasAnyRole(ATENDENTE, GERENTE)
                        .requestMatchers(HttpMethod.GET, "/pedidos/**", "/api/pedidos/**").hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                        .requestMatchers("/pedidos/**", "/api/pedidos/**").hasAnyRole(ATENDENTE, GERENTE)

                        // Clientes: ATENDENTE cadastra/atualiza, GERENTE consulta (e é o único que exclui)
                        .requestMatchers("/clientes/novo", "/clientes/salvar", "/clientes/editar/**").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.DELETE, "/api/clientes/**").hasRole(GERENTE)
                        .requestMatchers(HttpMethod.GET, "/clientes/**", "/api/clientes/**").hasAnyRole(ATENDENTE, GERENTE)
                        .requestMatchers("/clientes/**", "/api/clientes/**").hasRole(ATENDENTE)

                        // Produtos: consulta para todos os perfis, manutenção para ADMIN e GERENTE
                        .requestMatchers(HttpMethod.GET, "/produtos/**", "/api/produtos/**").hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                        .requestMatchers("/produtos/**", "/api/produtos/**").hasAnyRole(ADMIN, GERENTE)

                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        // APIs respondem 401 em vez de redirecionar para a tela de login
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                AcessoNegadoHandler::isApi)
                        // 403 + registro da tentativa na auditoria
                        .accessDeniedHandler(acessoNegadoHandler)
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect("/"); // Redireciona para a página inicial após login bem-sucedido
        };
    }
}
