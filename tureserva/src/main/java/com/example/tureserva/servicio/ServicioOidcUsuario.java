package com.example.tureserva.servicio;

import java.util.Collections;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.example.tureserva.modelo.Cliente;
import com.example.tureserva.repositorio.RepositorioCliente;

/**
 * Servicio personalizado para manejar autenticación OIDC (OpenID Connect) como Google.
 * Google usa OIDC en lugar de OAuth2 estándar, por eso necesitamos este servicio adicional.
 * 
 * Este servicio se ejecuta cuando un usuario inicia sesión con Google y asigna
 * el rol ROLE_CLIENTE a todos los usuarios de Google (ya que solo clientes usan Google).
 */
@Service
public class ServicioOidcUsuario extends OidcUserService {

    private final RepositorioCliente repositorioCliente;

    public ServicioOidcUsuario(RepositorioCliente repositorioCliente) {
        this.repositorioCliente = repositorioCliente;
        System.out.println("=== SERVICIO OIDC - CONSTRUCTOR LLAMADO ===");
        System.out.println("ServicioOidcUsuario creado correctamente");
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        System.out.println("=== SERVICIO OIDC - loadUser() INICIADO ===");
        System.out.println("Client Registration: " + userRequest.getClientRegistration().getRegistrationId());
        System.out.println("Access Token: " + (userRequest.getAccessToken() != null ? "PRESENTE" : "AUSENTE"));
        
        // Llamar al servicio padre para obtener el usuario OIDC
        OidcUser oidcUser = super.loadUser(userRequest);
        System.out.println("OidcUser obtenido correctamente desde Google");
        System.out.println("Email: " + oidcUser.getEmail());
        System.out.println("Name: " + oidcUser.getFullName());
        System.out.println("Attributes: " + oidcUser.getAttributes());
        
        try {
            OidcUser result = procesarUsuarioOidc(oidcUser);
            System.out.println("Usuario OIDC procesado correctamente - Authorities: " + result.getAuthorities());
            return result;
        } catch (Exception ex) {
            System.err.println("ERROR procesando usuario OIDC: " + ex.getMessage());
            ex.printStackTrace();
            throw new OAuth2AuthenticationException("Error procesando usuario OIDC: " + ex.getMessage());
        }
    }

    private OidcUser procesarUsuarioOidc(OidcUser oidcUser) {
        System.out.println("=== PROCESANDO USUARIO OIDC (GOOGLE) ===");
        String email = oidcUser.getEmail();
        String nombre = oidcUser.getGivenName();
        String apellido = oidcUser.getFamilyName();
        String nombreCompleto = oidcUser.getFullName();

        System.out.println("Email: " + email);
        System.out.println("Nombre: " + nombre);
        System.out.println("Apellido: " + apellido);
        System.out.println("Nombre completo: " + nombreCompleto);

        // Verificar si ya existe un cliente con este email
        Cliente clienteExistente = repositorioCliente.findByEmail(email).orElse(null);
        System.out.println("Cliente existente: " + (clienteExistente != null ? "SÍ (ID: " + clienteExistente.getId() + ")" : "NO"));

        // IMPORTANTE: Como solo los CLIENTES usan Google para autenticarse,
        // SIEMPRE asignamos ROLE_CLIENTE (sin importar si existe o no en la DB)
        System.out.println("Asignando ROLE_CLIENTE a usuario de Google");
        return new DefaultOidcUser(
            Collections.singleton(new SimpleGrantedAuthority("ROLE_CLIENTE")),
            oidcUser.getIdToken(),
            oidcUser.getUserInfo()
        );
    }
}
