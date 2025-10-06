package com.example.tureserva.controlador;

import com.example.tureserva.utiles.UtilesAutenticacion;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.example.tureserva.servicio.ServicioCliente;
import com.example.tureserva.modelo.Cliente;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


@Controller
public class ControladorUsuario {
    
    private final ServicioCliente servicioCliente;
    
    // Constructor injection (más moderno que @Autowired)
    public ControladorUsuario(ServicioCliente servicioCliente) {
        this.servicioCliente = servicioCliente;
    }

    // ===== RUTAS DE INICIO Y NAVEGACIÓN =====
    
    // Página principal
    @GetMapping("/")
    public String inicio() {
        // Si ya está autenticado, ir al dashboard, sino al registro
        if (UtilesAutenticacion.usuarioEstaAutenticado()) {
            return "redirect:/dashboard";
        }
        return "redirect:/usuarios/registro";
    }
    
    // Página de login personalizada
    @GetMapping("/login")
    public String login() {
        // Verificar si el usuario ya está autenticado
        if (UtilesAutenticacion.usuarioEstaAutenticado()) {
            // Si está autenticado, redirigir al dashboard
            return "redirect:/dashboard";
        }
        return "usuarios/login";
    }
    
    // Dashboard (página principal después del login)
    @GetMapping("/dashboard")
    public String dashboard() {
        return "usuarios/dashboard";
    }

    // ===== RUTAS DE REGISTRO =====

    //mostrar formulario registro
    @GetMapping("/usuarios/registro")
    public String mostrarFormularioRegistro() {
        // Verificar si el usuario ya está autenticado
        if (UtilesAutenticacion.usuarioEstaAutenticado()) {
            // Si está autenticado, redirigir al dashboard
            return "redirect:/dashboard";
        }
        
        return "usuarios/registro";
    }

    //procesar formulario registro
    @PostMapping("/usuarios/registro")
    public String procesarFormularioRegistro(
            @RequestParam("email") String email, // @RequestParam para capturar los datos del formulario
            @RequestParam("nombre") String nombre,
            @RequestParam("apellido") String apellido,
            @RequestParam("contrasena") String contrasena,
            @RequestParam(value = "telefono", required = false) String telefono) {
        
        // Verificar si el usuario ya está autenticado
        if (UtilesAutenticacion.usuarioEstaAutenticado()) {
            // Si está autenticado, redirigir al dashboard
            return "redirect:/dashboard";
        }
        
        // Verificar si el email ya existe
        if (servicioCliente.verificarEmail(email)) {
            return "redirect:/usuarios/registro?error";
        }

        //si el teléfono es vacío, asignar null
        if (telefono != null && telefono.trim().isEmpty()) {
            telefono = null;
        }
        
        try {
            // Crear nuevo cliente
            Cliente cliente = new Cliente();
            cliente.setEmail(email);
            cliente.setNombre(nombre);
            cliente.setApellido(apellido);
            cliente.setContrasena(contrasena);
            cliente.setTelefono(telefono);
            
            servicioCliente.guardarCliente(cliente);
            // Redirigir al login después del registro exitoso con un mensaje de exito
            return "redirect:/login";
        } catch (Exception e) {
            return "redirect:/usuarios/registro?error";
        }
    }

    // ===== RUTAS DE PERFIL =====

    // Mostrar perfil del cliente autenticado
    @GetMapping("/perfil")
    public String mostrarPerfil(Authentication authentication, Model model) {
        String email = authentication.getName(); // Email del usuario logueado
        Cliente cliente = servicioCliente.obtenerClientePorEmail(email);
        
        if (cliente == null) {
            model.addAttribute("error", "No se pudo cargar el perfil del cliente");
            return "redirect:/dashboard";
        }
        
        model.addAttribute("cliente", cliente);
        return "usuarios/ver-perfil";
    }

    // Mostrar formulario de edición de perfil
    @GetMapping("/perfil/editar")
    public String mostrarFormularioEdicion(Authentication authentication, Model model) {
        String email = authentication.getName();
        Cliente cliente = servicioCliente.obtenerClientePorEmail(email);
        
        if (cliente == null) {
            model.addAttribute("error", "No se pudo cargar el perfil del cliente");
            return "redirect:/dashboard";
        }
        
        // Limpiar la contraseña para no mostrarla en el formulario
        cliente.setContrasena("");
        model.addAttribute("cliente", cliente);
        return "usuarios/editar-perfil";
    }

    // Procesar actualización de perfil
    @PostMapping("/perfil/editar")
    public String procesarActualizacionPerfil(
            @ModelAttribute("cliente") Cliente clienteFormulario, // @ModelAttribute para capturar los datos del formulario y guardar en clienteFormulario
            BindingResult bindingResult, // BindingResult para manejar errores de validación
            Authentication authentication, //Autenticación del usuario logueado
            Model model, // Modelo para pasar datos a la vista
            RedirectAttributes redirectAttributes) { // RedirectAttributes para mensajes flash
        
        String emailActual = authentication.getName(); // Obtenemos el email del usuario logueado
        Cliente clienteExistente = servicioCliente.obtenerClientePorEmail(emailActual); // Obtenemos el cliente existente en base al email
        
        if (clienteExistente == null) {
            redirectAttributes.addFlashAttribute("error", "No se pudo encontrar el cliente");
            return "redirect:/perfil";
        }

        // Validaciones básicas
        if (clienteFormulario.getNombre() == null || clienteFormulario.getNombre().trim().isEmpty()) {
            bindingResult.rejectValue("nombre", "error.cliente", "El nombre es obligatorio");
        }
        if (clienteFormulario.getEmail() == null || clienteFormulario.getEmail().trim().isEmpty()) {
            bindingResult.rejectValue("email", "error.cliente", "El email es obligatorio");
        }

        // Verificar si el email cambió y si ya existe
        if (!clienteFormulario.getEmail().equals(emailActual) && // si el email del formulario es diferente al actual
            servicioCliente.verificarEmail(clienteFormulario.getEmail())) { // y si el email ya existe en la base de datos
            bindingResult.rejectValue("email", "error.cliente", "El email ya está en uso"); // mensaje de error
        }

        // Validar contraseña solo si se proporcionó una nueva
        if (clienteFormulario.getContrasena() != null && !clienteFormulario.getContrasena().trim().isEmpty()) {
            if (clienteFormulario.getContrasena().length() < 6) {
                bindingResult.rejectValue("contrasena", "error.cliente", "La contraseña debe tener al menos 6 caracteres");
            }
        } else {
            // Si no se proporciona contraseña, mantener la actual
            clienteFormulario.setContrasena(null);
        }

        if (bindingResult.hasErrors()) {
            return "usuarios/editar-perfil";
        }

        try {
            // Copiar datos del formulario al cliente existente
            clienteExistente.setNombre(clienteFormulario.getNombre()); // Actualizar nombre
            clienteExistente.setEmail(clienteFormulario.getEmail()); // Actualizar email
            clienteExistente.setTelefono(clienteFormulario.getTelefono()); // Actualizar teléfono
            
            // Solo actualizar contraseña si se proporcionó una nueva
            if (clienteFormulario.getContrasena() != null && !clienteFormulario.getContrasena().trim().isEmpty()) {
                clienteExistente.setContrasena(clienteFormulario.getContrasena());
            }

            servicioCliente.actualizarCliente(clienteExistente);
            redirectAttributes.addFlashAttribute("mensaje", "Perfil actualizado correctamente");
            return "redirect:/perfil";
            
        } catch (Exception e) {
            model.addAttribute("error", "Error al actualizar el perfil. Inténtelo de nuevo.");
            return "usuarios/editar-perfil";
        }
    }

    // Mostrar confirmación de baja
    @GetMapping("/perfil/eliminar")
    public String mostrarConfirmacionBaja(Authentication authentication, Model model) {
        String email = authentication.getName();
        Cliente cliente = servicioCliente.obtenerClientePorEmail(email);
        
        if (cliente == null) {
            model.addAttribute("error", "No se pudo cargar el perfil del cliente");
            return "redirect:/dashboard";
        }
        
        model.addAttribute("cliente", cliente);
        return "usuarios/confirmar-baja";
    }

    // Procesar baja del cliente
    @PostMapping("/perfil/eliminar")
    public String procesarBajaCliente(Authentication authentication, RedirectAttributes redirectAttributes) {
        String email = authentication.getName();
        Cliente cliente = servicioCliente.obtenerClientePorEmail(email);
        
        if (cliente == null) {
            redirectAttributes.addFlashAttribute("error", "No se pudo encontrar el cliente");
            return "redirect:/perfil";
        }

        try {
            boolean eliminado = servicioCliente.eliminarCliente(cliente.getId());
            
            if (eliminado) {
                // Redirigir a una página especial que haga logout automático
                return "redirect:/perfil/baja-exitosa";
            } else {
                redirectAttributes.addFlashAttribute("error", "No se pudo dar de baja la cuenta");
                return "redirect:/perfil";
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al procesar la baja. Inténtelo de nuevo.");
            return "redirect:/perfil";
        }
    }

    // Página de confirmación de baja exitosa
    @GetMapping("/perfil/baja-exitosa")
    public String bajaExitosa() {
        return "usuarios/baja-exitosa";
    }
}
