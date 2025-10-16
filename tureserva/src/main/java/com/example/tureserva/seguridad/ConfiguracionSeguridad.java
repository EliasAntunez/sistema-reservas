package com.example.tureserva.seguridad;

import com.example.tureserva.servicio.ServicioOAuth2Usuario;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class ConfiguracionSeguridad {

    private final ServicioAutenticacion servicioAutenticacion;
    private final ServicioOAuth2Usuario servicioOAuth2Usuario;

    // Constructor injection (más moderno que @Autowired)
    public ConfiguracionSeguridad(ServicioAutenticacion servicioAutenticacion, ServicioOAuth2Usuario servicioOAuth2Usuario) {
        this.servicioAutenticacion = servicioAutenticacion;
        this.servicioOAuth2Usuario = servicioOAuth2Usuario;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder = 
            http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder
            .userDetailsService(servicioAutenticacion)
            .passwordEncoder(passwordEncoder());
        return authenticationManagerBuilder.build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(this::configurarAutorizaciones)
            .formLogin(this::configurarFormLogin)
            .oauth2Login(this::configurarOAuth2Login)
            .logout(this::configurarLogout)
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .build();
    }
    
    private void configurarAutorizaciones(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry authz) {
        authz
            // Rutas públicas
            .requestMatchers("/", "/usuarios/registro").permitAll()
            .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
            .requestMatchers("/debug/**").permitAll()
            // Rutas OAuth2
            .requestMatchers("/completar-datos").hasAnyAuthority("ROLE_OAUTH2_USER", "OIDC_USER")
            // Rutas de administración
            .requestMatchers("/super-admin/**").hasRole("SUPER_ADMIN")
            .requestMatchers("/admin-complejo/**").hasRole("ADMIN_COMPLEJO")
            // Rutas autenticadas
            .requestMatchers("/perfil/**", "/dashboard").authenticated()
            .anyRequest().authenticated();
    }
    
    private void configurarFormLogin(org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer<HttpSecurity> form) {
        form
            .loginPage("/login")
            .defaultSuccessUrl("/dashboard", true)
            .failureUrl("/login?error=true")
            .usernameParameter("username")
            .passwordParameter("password")
            .permitAll();
    }
    
    private void configurarOAuth2Login(org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer<HttpSecurity> oauth2) {
        oauth2
            .loginPage("/login")
            .defaultSuccessUrl("/dashboard", true)
            .failureUrl("/login?error=oauth2")
            .userInfoEndpoint(userInfo -> userInfo.userService(servicioOAuth2Usuario));
    }
    
    private void configurarLogout(org.springframework.security.config.annotation.web.configurers.LogoutConfigurer<HttpSecurity> logout) {
        logout
            .logoutUrl("/logout")
            .logoutSuccessUrl("/login?logout=true")
            .invalidateHttpSession(true)
            .deleteCookies("JSESSIONID")
            .permitAll();
    }
}