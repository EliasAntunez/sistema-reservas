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

import com.example.tureserva.modelo.Cliente;
import com.example.tureserva.modelo.SuperAdministrador;
import com.example.tureserva.repositorio.RepositorioCliente;
import com.example.tureserva.repositorio.RepositorioSuperAdministrador;

@Service("servicioAutenticacion")
public class ServicioAutenticacion implements UserDetailsService {

    private final RepositorioCliente repositorioCliente;
    private final RepositorioSuperAdministrador repositorioSuperAdministrador;
    
    // Constructor injection
    public ServicioAutenticacion(RepositorioCliente repositorioCliente, 
                                RepositorioSuperAdministrador repositorioSuperAdministrador) {
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
        
        // Si no es SuperAdministrador, buscar Cliente por email
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
        
        // Si no se encuentra ni como SuperAdmin ni como Cliente
        throw new UsernameNotFoundException("Usuario no encontrado con email: " + email);
    }
}