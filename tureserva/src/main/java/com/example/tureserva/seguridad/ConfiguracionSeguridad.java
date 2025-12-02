package com.example.tureserva.seguridad;

import com.example.tureserva.servicio.ServicioOAuth2Usuario;
import com.example.tureserva.servicio.ServicioOidcUsuario;
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
    private final ServicioOidcUsuario servicioOidcUsuario;

    // Constructor injection (más moderno que @Autowired)
    public ConfiguracionSeguridad(
            ServicioAutenticacion servicioAutenticacion, 
            ServicioOAuth2Usuario servicioOAuth2Usuario,
            ServicioOidcUsuario servicioOidcUsuario) {
        this.servicioAutenticacion = servicioAutenticacion;
        this.servicioOAuth2Usuario = servicioOAuth2Usuario;
        this.servicioOidcUsuario = servicioOidcUsuario;
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
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                // Deshabilitar CSRF para webhooks de Mercado Pago (MP no envía tokens CSRF)
                .ignoringRequestMatchers("/webhook/**")
                // Deshabilitar CSRF para alertas climáticas (acceso desde emails)
                .ignoringRequestMatchers("/alerta-clima/**")
                // Deshabilitar CSRF para API de validación de localidad (usada desde formularios con sesión activa)
                .ignoringRequestMatchers("/api/validacion-localidad/**")
            )
            .build();
    }
    
    private void configurarAutorizaciones(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry authz) {
        authz
            // Rutas públicas
            .requestMatchers("/", "/usuarios/registro").permitAll()
            .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
            .requestMatchers("/debug/**").permitAll()
            // Webhooks y notificaciones de Mercado Pago (deben ser públicos)
            .requestMatchers("/webhook/**").permitAll()
            // Back URLs de Mercado Pago tras completar/fallar el pago
            .requestMatchers("/reservas/pago-exitoso", "/reservas/pago-fallido", "/reservas/pago-pendiente").permitAll()
            // Endpoints de pago (init-senia requiere sesión pero permitimos acceso autenticado)
            .requestMatchers("/pagos/**").permitAll()
            // Endpoints de alertas climáticas (acceso desde emails sin autenticación)
            .requestMatchers("/alerta-clima/**").permitAll()
            // Rutas OAuth2 - NO ES NECESARIO YA, todos los Google OAuth2 obtienen ROLE_CLIENTE directamente
            // .requestMatchers("/completar-datos").hasAnyAuthority("ROLE_OAUTH2_USER", "OIDC_USER")
            // Rutas de cliente (solo clientes pueden acceder a los flujos de reserva)
            .requestMatchers(
                "/reservas/nueva",
                "/reservas/complejo/**",
                "/reservas/seleccionar-horario",
                "/reservas/confirmar",
                "/reservas/confirmar-final",
                "/reservas/exitosa/**",
                "/reservas/mis-reservas/**",
                "/reservas/*/cancelar",
                "/reservas/extender-horario"
            ).hasRole("CLIENTE")
            // Rutas de administración
            .requestMatchers("/super-admin/**").hasRole("SUPER_ADMIN")
            .requestMatchers("/admin-complejo/**").hasRole("ADMIN_COMPLEJO")
            // API REST para validación de localidades (solo super admin)
            .requestMatchers("/api/validacion-localidad/**").hasRole("SUPER_ADMIN")
            // Rutas de perfil (solo clientes) y dashboard autenticado
            .requestMatchers("/perfil/**").hasRole("CLIENTE")
            .requestMatchers("/dashboard").authenticated()
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
            .userInfoEndpoint(userInfo -> {
                // OAuth2 estándar (Facebook, GitHub, etc.)
                userInfo.userService(servicioOAuth2Usuario);
                // OIDC (Google, Microsoft, Apple, etc.)
                userInfo.oidcUserService(servicioOidcUsuario);
            });
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