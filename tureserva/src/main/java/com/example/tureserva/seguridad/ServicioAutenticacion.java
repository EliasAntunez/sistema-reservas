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
import com.example.tureserva.repositorio.RepositorioCliente;

@Service("servicioAutenticacion")
public class ServicioAutenticacion implements UserDetailsService {

    private final RepositorioCliente repositorioCliente;
    
    // Constructor injection
    public ServicioAutenticacion(RepositorioCliente repositorioCliente) {
        this.repositorioCliente = repositorioCliente;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Buscar cliente por email
        Cliente cliente = repositorioCliente.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + email));
        
        // Verificar que el cliente esté activo
        if (!cliente.isActivo()) {
            throw new UsernameNotFoundException("El usuario está desactivado: " + email);
        }

        // Crear lista de autoridades/roles
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_CLIENTE"));
        
        // Retornar UserDetails de Spring Security
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
}