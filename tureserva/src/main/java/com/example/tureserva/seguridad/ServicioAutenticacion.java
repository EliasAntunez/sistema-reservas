package com.example.tureserva.seguridad;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.tureserva.modelo.AdministradorComplejo;
import com.example.tureserva.modelo.Cliente;
import com.example.tureserva.modelo.SuperAdministrador;
import com.example.tureserva.repositorio.RepositorioAdministradorComplejo;
import com.example.tureserva.repositorio.RepositorioCliente;
import com.example.tureserva.repositorio.RepositorioSuperAdministrador;
import com.example.tureserva.repositorio.RepositorioUsuario;

@Service("servicioAutenticacion")
public class ServicioAutenticacion implements UserDetailsService {

    private final RepositorioAdministradorComplejo repositorioAdministradorComplejo;
    private final RepositorioCliente repositorioCliente;
    private final RepositorioSuperAdministrador repositorioSuperAdministrador;
    private final RepositorioUsuario repositorioUsuario;
    
    // Constructor injection
    public ServicioAutenticacion(RepositorioAdministradorComplejo repositorioAdministradorComplejo,
                                RepositorioCliente repositorioCliente, 
                                RepositorioSuperAdministrador repositorioSuperAdministrador,
                                RepositorioUsuario repositorioUsuario) {
        this.repositorioAdministradorComplejo = repositorioAdministradorComplejo;
        this.repositorioCliente = repositorioCliente;
        this.repositorioSuperAdministrador = repositorioSuperAdministrador;
        this.repositorioUsuario = repositorioUsuario;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Crear lista de autoridades/roles
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        
        // OPTIMIZACIÓN: Primero consultar el tipo de usuario usando la columna discriminadora
        // Esto evita hacer 3 consultas (una a cada tabla) para "adivinar" el tipo
        Optional<Class<?>> tipoUsuarioOpt = repositorioUsuario.findTipoUsuarioByEmail(email);
        
        if (tipoUsuarioOpt.isEmpty()) {
            throw new UsernameNotFoundException("Usuario no encontrado con email: " + email);
        }
        
        Class<?> tipoUsuario = tipoUsuarioOpt.get();
        
        // Ahora buscar directamente en el repositorio correcto según el discriminador
        if (tipoUsuario.equals(SuperAdministrador.class)) {
            SuperAdministrador superAdmin = repositorioSuperAdministrador.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("SuperAdministrador no encontrado: " + email));
            
            if (!superAdmin.isActivo()) {
                throw new UsernameNotFoundException("El usuario está desactivado: " + email);
            }
            
            authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
            
            return new User(
                superAdmin.getEmail(),
                superAdmin.getContrasena(),
                superAdmin.isActivo(),
                true,
                true,
                true,
                authorities
            );
        } 
        else if (tipoUsuario.equals(AdministradorComplejo.class)) {
            AdministradorComplejo adminComplejo = repositorioAdministradorComplejo.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("AdministradorComplejo no encontrado: " + email));
            
            if (!adminComplejo.isActivo()) {
                throw new UsernameNotFoundException("El usuario está desactivado: " + email);
            }

            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN_COMPLEJO"));
            
            return new User(
                adminComplejo.getEmail(),
                adminComplejo.getContrasena(),
                adminComplejo.isActivo(),
                true,
                true,
                true,
                authorities
            );
        } 
        else if (tipoUsuario.equals(Cliente.class)) {
            Cliente cliente = repositorioCliente.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Cliente no encontrado: " + email));
            
            if (!cliente.isActivo()) {
                throw new UsernameNotFoundException("El usuario está desactivado: " + email);
            }

            authorities.add(new SimpleGrantedAuthority("ROLE_CLIENTE"));
            
            return new User(
                cliente.getEmail(),
                cliente.getContrasena(),
                cliente.isActivo(),
                true,
                true,
                true,
                authorities
            );
        }
        
        throw new UsernameNotFoundException("Tipo de usuario no reconocido: " + tipoUsuario.getSimpleName());
    }
}