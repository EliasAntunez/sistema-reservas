package com.example.tureserva.servicio;

import java.util.Collections;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.example.tureserva.modelo.Cliente;
import com.example.tureserva.repositorio.RepositorioCliente;

@Service
public class ServicioOAuth2Usuario extends DefaultOAuth2UserService {

    private final RepositorioCliente repositorioCliente;

    public ServicioOAuth2Usuario(RepositorioCliente repositorioCliente) {
        this.repositorioCliente = repositorioCliente;
        System.out.println("=== SERVICIO OAUTH2 - CONSTRUCTOR LLAMADO ===");
        System.out.println("ServicioOAuth2Usuario creado correctamente");
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        System.out.println("=== SERVICIO OAUTH2 - loadUser() INICIADO ===");
        System.out.println("Client Registration: " + userRequest.getClientRegistration().getRegistrationId());
        System.out.println("Access Token: " + (userRequest.getAccessToken() != null ? "PRESENTE" : "AUSENTE"));
        
        OAuth2User oauth2User = super.loadUser(userRequest);
        System.out.println("OAuth2User obtenido correctamente desde Google");
        System.out.println("Attributes: " + oauth2User.getAttributes());
        System.out.println("Name attribute: " + oauth2User.getName());
        
        try {
            OAuth2User result = procesarUsuarioOAuth2(oauth2User);
            System.out.println("Usuario OAuth2 procesado correctamente - Authorities: " + result.getAuthorities());
            return result;
        } catch (Exception ex) {
            System.err.println("ERROR procesando usuario OAuth2: " + ex.getMessage());
            ex.printStackTrace();
            throw new OAuth2AuthenticationException("Error procesando usuario OAuth2: " + ex.getMessage());
        }
    }

    private OAuth2User procesarUsuarioOAuth2(OAuth2User oauth2User) {
        System.out.println("=== PROCESANDO USUARIO OAUTH2 ===");
        String email = oauth2User.getAttribute("email");
        String nombre = oauth2User.getAttribute("given_name");
        String apellido = oauth2User.getAttribute("family_name");
        String idOAuth2 = oauth2User.getAttribute("sub");
        String nombreCompleto = oauth2User.getAttribute("name");

        System.out.println("Email: " + email);
        System.out.println("Nombre: " + nombre);
        System.out.println("Apellido: " + apellido);
        System.out.println("ID OAuth2: " + idOAuth2);

        // Si no tenemos nombre/apellido separados, usar el nombre completo
        if (nombre == null && nombreCompleto != null) {
            String[] partesNombre = nombreCompleto.split(" ", 2);
            nombre = partesNombre[0];
            apellido = partesNombre.length > 1 ? partesNombre[1] : "";
            System.out.println("Nombre completo dividido - Nombre: " + nombre + ", Apellido: " + apellido);
        }

        // NO CREAR CLIENTE AÚN - Solo verificar si ya existe uno completamente registrado
        Cliente clienteExistente = repositorioCliente.findByEmail(email).orElse(null);
        System.out.println("Cliente existente: " + (clienteExistente != null ? "SÍ (ID: " + clienteExistente.getId() + ")" : "NO"));

        if (clienteExistente != null && clienteExistente.getDni() != null) {
            System.out.println("Cliente ya completamente registrado");
            // Cliente ya existe y está completo
            return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_CLIENTE")),
                oauth2User.getAttributes(),
                "email"
            );
        } else {
            System.out.println("Usuario OAuth2 temporal - necesita completar registro");
            // Usuario temporal que necesita completar datos
            // Usamos un rol especial para usuarios temporales
            return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_OAUTH2_USER")),
                oauth2User.getAttributes(),
                "email"
            );
        }
    }
}