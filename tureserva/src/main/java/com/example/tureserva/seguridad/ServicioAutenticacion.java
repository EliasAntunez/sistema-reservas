package com.example.tureserva.seguridad;

import java.util.ArrayList;
import java.util.Collection;

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

@Service("servicioAutenticacion")
public class ServicioAutenticacion implements UserDetailsService {

    private final RepositorioAdministradorComplejo repositorioAdministradorComplejo;
    private final RepositorioCliente repositorioCliente;
    private final RepositorioSuperAdministrador repositorioSuperAdministrador;
    
    // Constructor injection
    public ServicioAutenticacion(RepositorioAdministradorComplejo repositorioAdministradorComplejo,
                                RepositorioCliente repositorioCliente, 
                                RepositorioSuperAdministrador repositorioSuperAdministrador) {
        this.repositorioAdministradorComplejo = repositorioAdministradorComplejo;
        this.repositorioCliente = repositorioCliente;
        this.repositorioSuperAdministrador = repositorioSuperAdministrador;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Crear lista de autoridades/roles
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        
        // Primero buscar SuperAdministrador por email
        SuperAdministrador superAdmin = repositorioSuperAdministrador.findByEmail(email).orElse(null);
        if (superAdmin != null) {
            // Verificar que el SuperAdministrador esté activo
            if (!superAdmin.isActivo()) {
                throw new UsernameNotFoundException("El usuario está desactivado: " + email);
            }
            
            authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
            
            return new User(
                superAdmin.getEmail(),           // username (usamos email)
                superAdmin.getContrasena(),      // password (ya encriptada)
                superAdmin.isActivo(),           // enabled
                true,                            // accountNonExpired
                true,                            // credentialsNonExpired
                true,                            // accountNonLocked
                authorities                      // authorities/roles
            );
        }
        
        // Si no es SuperAdministrador, buscar AdministradorComplejo por email
        AdministradorComplejo adminComplejo = repositorioAdministradorComplejo.findByEmail(email).orElse(null);
        if (adminComplejo != null) {
            // Verificar que el AdministradorComplejo esté activo
            if (!adminComplejo.isActivo()) {
                throw new UsernameNotFoundException("El usuario está desactivado: " + email);
            }

            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN_COMPLEJO"));
            
            return new User(
                adminComplejo.getEmail(),        // username (usamos email)
                adminComplejo.getContrasena(),   // password (ya encriptada)
                adminComplejo.isActivo(),        // enabled
                true,                            // accountNonExpired
                true,                            // credentialsNonExpired
                true,                            // accountNonLocked
                authorities                      // authorities/roles
            );
        }
        
        // Si no es SuperAdministrador ni AdministradorComplejo, buscar Cliente por email
        Cliente cliente = repositorioCliente.findByEmail(email).orElse(null);
        if (cliente != null) {
            // Verificar que el cliente esté activo
            if (!cliente.isActivo()) {
                throw new UsernameNotFoundException("El usuario está desactivado: " + email);
            }

            authorities.add(new SimpleGrantedAuthority("ROLE_CLIENTE"));
            
            return new User(
                cliente.getEmail(),           // username (usamos email)
                cliente.getContrasena(),      // password (ya encriptada)
                cliente.isActivo(),           // enabled
                true,                         // accountNonExpired
                true,                         // credentialsNonExpired
                true,                         // accountNonLocked
                authorities                   // authorities/roles
            );
        }
        
        // Si no se encuentra como ningún tipo de usuario
        throw new UsernameNotFoundException("Usuario no encontrado con email: " + email);
    }
}