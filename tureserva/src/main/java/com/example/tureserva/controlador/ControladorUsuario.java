package com.example.tureserva.controlador;

import com.example.tureserva.modelo.Cliente;
import com.example.tureserva.servicio.ServicioCliente;
import com.example.tureserva.servicio.ServicioUsuarioUnificado;
import com.example.tureserva.utiles.ManejadorMensajes;
import com.example.tureserva.utiles.UtilesAutenticacion;
import com.example.tureserva.utiles.ValidadorContrasena;
import com.example.tureserva.utiles.ValidadorFormulario;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


@Controller
public class ControladorUsuario {
    
    private final ServicioCliente servicioCliente;
    private final ServicioUsuarioUnificado servicioUsuarioUnificado;
    
    // Constructor injection (más moderno que @Autowired)
    public ControladorUsuario(ServicioCliente servicioCliente, ServicioUsuarioUnificado servicioUsuarioUnificado) {
        this.servicioCliente = servicioCliente;
        this.servicioUsuarioUnificado = servicioUsuarioUnificado;
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
    public String dashboard(Authentication authentication) {
        // Si necesita completar datos, redirigir
        if (servicioUsuarioUnificado.necesitaCompletarDatos(authentication)) {
            return "redirect:/completar-datos";
        }
        
        // Redirigir según el rol efectivo del usuario
        String rolEfectivo = servicioUsuarioUnificado.obtenerRolEfectivo(authentication);
        switch (rolEfectivo) {
            case "ROLE_SUPER_ADMIN":
                return "redirect:/super-admin/dashboard";
            case "ROLE_ADMIN_COMPLEJO":
                return "redirect:/admin-complejo/dashboard";
            case "ROLE_CLIENTE":
            default:
                return "usuarios/dashboard";
        }
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
            @RequestParam("dni") String dni,
            @RequestParam("contrasena") String contrasena,
            @RequestParam(value = "telefono", required = false) String telefono) {
        
        // Verificar si el usuario ya está autenticado
        if (UtilesAutenticacion.usuarioEstaAutenticado()) {
            // Si está autenticado, redirigir al dashboard
            return "redirect:/dashboard";
        }
        
        // Validar formato de DNI
        if (!ValidadorFormulario.validarFormatoDni(dni)) {
            return "redirect:/usuarios/registro?error=formato_dni";
        }
        
        // Validar formato de email
        if (!ValidadorFormulario.validarFormatoEmail(email)) {
            return "redirect:/usuarios/registro?error=formato_email";
        }
        
        // Validar longitud de contraseña
        if (!ValidadorFormulario.validarLongitudContrasena(contrasena)) {
            return "redirect:/usuarios/registro?error=contrasena";
        }
        
        // Verificar si el email ya existe
        if (servicioCliente.verificarEmail(email)) {
            return "redirect:/usuarios/registro?error=email";
        }

        // Verificar si el DNI ya existe
        if (servicioCliente.verificarDni(dni)) {
            return "redirect:/usuarios/registro?error=dni";
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
            cliente.setDni(dni);
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
        Cliente cliente = servicioUsuarioUnificado.obtenerCliente(authentication);
        
        if (cliente == null) {
            model.addAttribute("error", "No se pudo cargar el perfil del cliente");
            return "redirect:/dashboard";
        }
        
        // Verificar si es OAuth2 y necesita completar datos
        if (servicioUsuarioUnificado.necesitaCompletarDatos(authentication)) {
            return "redirect:/completar-datos";
        }
        
        model.addAttribute("cliente", cliente);
        model.addAttribute("esOAuth2", servicioUsuarioUnificado.esUsuarioOAuth2(authentication));
        return "usuarios/ver-perfil";
    }

    // Mostrar formulario de edición de perfil
    @GetMapping("/perfil/editar")
    public String mostrarFormularioEdicion(Authentication authentication, Model model) {
        System.out.println("=== DEBUG EDITAR PERFIL ===");
        System.out.println("Authentication type: " + authentication.getClass().getSimpleName());
        
        String email;
        
        // Obtener email según el tipo de autenticación
        if (authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
            email = oauth2Token.getPrincipal().getAttribute("email");
            System.out.println("Usuario OAuth2 - Email: " + email);
        } else {
            email = authentication.getName(); // Email del usuario logueado tradicional
            System.out.println("Usuario tradicional - Email: " + email);
        }
        
        Cliente cliente = servicioCliente.obtenerClientePorEmail(email);
        
        if (cliente == null) {
            System.out.println("Cliente no encontrado para email: " + email);
            model.addAttribute("error", "No se pudo cargar el perfil del cliente");
            return "redirect:/dashboard";
        }
        
        System.out.println("Cliente encontrado para edición - DNI: " + cliente.getDni());
        
        // Limpiar la contraseña para no mostrarla en el formulario
        cliente.setContrasena("");
        model.addAttribute("cliente", cliente);
        model.addAttribute("esOAuth2", authentication instanceof OAuth2AuthenticationToken);
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
        
        // Obtener email según el tipo de autenticación
        String emailActual;
        if (authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
            emailActual = oauth2Token.getPrincipal().getAttribute("email");
        } else {
            emailActual = authentication.getName(); // Email del usuario logueado tradicional
        }
        
        Cliente clienteExistente = servicioCliente.obtenerClientePorEmail(emailActual); // Obtenemos el cliente existente en base al email
        
        if (clienteExistente == null) {
            redirectAttributes.addFlashAttribute("error", "No se pudo encontrar el cliente");
            return "redirect:/perfil";
        }

        boolean esUsuarioOAuth2 = authentication instanceof OAuth2AuthenticationToken;
        
        // Para usuarios OAuth2, proteger campos que no deben cambiar
        if (esUsuarioOAuth2) {
            // Restaurar datos originales para campos protegidos
            clienteFormulario.setEmail(clienteExistente.getEmail()); // Email no cambia
            clienteFormulario.setNombre(clienteExistente.getNombre()); // Nombre no cambia
            clienteFormulario.setApellido(clienteExistente.getApellido()); // Apellido no cambia
            
            // DNI solo cambia si estaba vacío
            if (clienteExistente.getDni() != null && !clienteExistente.getDni().isEmpty()) {
                clienteFormulario.setDni(clienteExistente.getDni());
            }
        }

        // Validaciones según el tipo de usuario
        if (esUsuarioOAuth2) {
            // Para OAuth2, solo validar teléfono y DNI (si está permitido cambiarlo)
            if (clienteFormulario.getDni() == null || clienteFormulario.getDni().trim().isEmpty()) {
                bindingResult.rejectValue("dni", "error.cliente", "El DNI es obligatorio");
            }
        } else {
            // Validaciones completas para usuarios tradicionales
            ValidadorFormulario.validarCamposCompletosUsuario(
                clienteFormulario.getNombre(), 
                clienteFormulario.getApellido(), 
                clienteFormulario.getEmail(), 
                clienteFormulario.getDni(),
                bindingResult
            );
            
            // Verificar si el email cambió y si ya existe
            if (!clienteFormulario.getEmail().equals(emailActual) && 
                servicioCliente.verificarEmail(clienteFormulario.getEmail())) {
                bindingResult.rejectValue("email", "error.cliente", ManejadorMensajes.EMAIL_EN_USO);
            }
        }

        // Verificar DNI solo si cambió
        if (!clienteFormulario.getDni().equals(clienteExistente.getDni()) && 
            servicioCliente.verificarDni(clienteFormulario.getDni())) {
            bindingResult.rejectValue("dni", "error.cliente", "El DNI ya está en uso");
        }

        // Verificar si el DNI cambió y si ya existe
        if (!clienteFormulario.getDni().equals(clienteExistente.getDni()) && 
            servicioCliente.verificarDni(clienteFormulario.getDni())) {
            bindingResult.rejectValue("dni", "error.cliente", "El DNI ya está en uso por otro usuario");
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
            clienteExistente.setApellido(clienteFormulario.getApellido()); // Actualizar apellido
            clienteExistente.setDni(clienteFormulario.getDni()); // Actualizar DNI
            clienteExistente.setEmail(clienteFormulario.getEmail()); // Actualizar email
            clienteExistente.setTelefono(clienteFormulario.getTelefono()); // Actualizar teléfono
            
            // Solo actualizar contraseña si se proporcionó una nueva
            if (clienteFormulario.getContrasena() != null && !clienteFormulario.getContrasena().trim().isEmpty()) {
                clienteExistente.setContrasena(clienteFormulario.getContrasena());
            }

            servicioCliente.actualizarCliente(clienteExistente);
            ManejadorMensajes.agregarMensajeExito(redirectAttributes, ManejadorMensajes.PERFIL_ACTUALIZADO);
            return "redirect:/perfil";
            
        } catch (Exception e) {
            ManejadorMensajes.agregarMensajeError(model, ManejadorMensajes.ERROR_GENERICO);
            return "usuarios/editar-perfil";
        }
    }

    // Mostrar confirmación de baja
    @GetMapping("/perfil/eliminar")
    public String mostrarConfirmacionBaja(Authentication authentication, Model model) {
        System.out.println("=== DEBUG CONFIRMAR BAJA ===");
        System.out.println("Authentication type: " + authentication.getClass().getSimpleName());
        
        String email;
        
        // Obtener email según el tipo de autenticación
        if (authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
            email = oauth2Token.getPrincipal().getAttribute("email");
            System.out.println("Usuario OAuth2 - Email: " + email);
        } else {
            email = authentication.getName(); // Email del usuario logueado tradicional
            System.out.println("Usuario tradicional - Email: " + email);
        }
        
        Cliente cliente = servicioCliente.obtenerClientePorEmail(email);
        
        if (cliente == null) {
            System.out.println("Cliente no encontrado para email: " + email);
            model.addAttribute("error", "No se pudo cargar el perfil del cliente");
            return "redirect:/dashboard";
        }
        
        System.out.println("Cliente encontrado para baja - DNI: " + cliente.getDni());
        model.addAttribute("cliente", cliente);
        return "usuarios/confirmar-baja";
    }

    // Procesar baja del cliente
    @PostMapping("/perfil/eliminar")
    public String procesarBajaCliente(Authentication authentication, RedirectAttributes redirectAttributes) {
        System.out.println("=== DEBUG PROCESAR BAJA ===");
        System.out.println("Authentication type: " + authentication.getClass().getSimpleName());
        
        String email;
        
        // Obtener email según el tipo de autenticación
        if (authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
            email = oauth2Token.getPrincipal().getAttribute("email");
            System.out.println("Usuario OAuth2 - Email: " + email);
        } else {
            email = authentication.getName(); // Email del usuario logueado tradicional
            System.out.println("Usuario tradicional - Email: " + email);
        }
        
        Cliente cliente = servicioCliente.obtenerClientePorEmail(email);
        
        if (cliente == null) {
            System.out.println("Cliente no encontrado para email: " + email);
            redirectAttributes.addFlashAttribute("error", "No se pudo encontrar el cliente");
            return "redirect:/perfil";
        }
        
        System.out.println("Procediendo a eliminar cliente - ID: " + cliente.getId());

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

    // ===== RUTAS DE CAMBIO DE CONTRASEÑA =====

    // Mostrar formulario de cambio de contraseña
    @GetMapping("/perfil/cambiar-contrasena")
    public String mostrarFormularioCambioContrasena(Authentication authentication, Model model) {
        // Los usuarios OAuth2 no pueden cambiar contraseña
        if (authentication instanceof OAuth2AuthenticationToken) {
            model.addAttribute("error", "Los usuarios que inician sesión con Google no pueden cambiar contraseña desde aquí. Tu contraseña se gestiona a través de tu cuenta de Google.");
            return "redirect:/perfil";
        }
        
        return "usuarios/cambiar-contrasena";
    }

    // Procesar cambio de contraseña
    @PostMapping("/perfil/cambiar-contrasena")
    public String procesarCambioContrasena(
            @RequestParam("contrasenaActual") String contrasenaActual,
            @RequestParam("nuevaContrasena") String nuevaContrasena,
            @RequestParam("confirmarContrasena") String confirmarContrasena,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        // Los usuarios OAuth2 no pueden cambiar contraseña
        if (authentication instanceof OAuth2AuthenticationToken) {
            redirectAttributes.addFlashAttribute("error", "Los usuarios que inician sesión con Google no pueden cambiar contraseña desde aquí.");
            return "redirect:/perfil";
        }
        
        String email = authentication.getName(); // Solo usuarios tradicionales llegan hasta aquí
        Cliente cliente = servicioCliente.obtenerClientePorEmail(email);
        
        if (cliente == null) {
            redirectAttributes.addFlashAttribute("error", "No se pudo encontrar el cliente");
            return "redirect:/perfil/cambiar-contrasena";
        }

        // Usar validador unificado de contraseñas
        String errorValidacion = ValidadorContrasena.validarCambioContrasena(
            contrasenaActual, nuevaContrasena, confirmarContrasena, 
            null, redirectAttributes, "/perfil/cambiar-contrasena"
        );
        
        if (errorValidacion != null) {
            return errorValidacion;
        }

        try {
            // Verificar la contraseña actual y cambiar por la nueva
            boolean actualizada = servicioCliente.cambiarContrasena(cliente.getId(), contrasenaActual, nuevaContrasena);
            
            if (actualizada) {
                ManejadorMensajes.agregarMensajeExito(redirectAttributes, ManejadorMensajes.CONTRASENA_ACTUALIZADA);
                return "redirect:/perfil";
            } else {
                ManejadorMensajes.agregarMensajeError(redirectAttributes, ManejadorMensajes.CONTRASENA_ACTUAL_INCORRECTA);
                return "redirect:/perfil/cambiar-contrasena";
            }
            
        } catch (Exception e) {
            ManejadorMensajes.agregarMensajeError(redirectAttributes, ManejadorMensajes.ERROR_GENERICO);
            return "redirect:/perfil/cambiar-contrasena";
        }
    }

    // ===== RUTAS PARA COMPLETAR DATOS OAUTH2 =====

    // Mostrar formulario para completar datos después de OAuth2
    @GetMapping("/completar-datos")
    public String mostrarCompletarDatos(Authentication authentication, Model model) {
        if (!servicioUsuarioUnificado.esUsuarioOAuth2(authentication)) {
            return "redirect:/dashboard";
        }

        Cliente clienteTemp = servicioUsuarioUnificado.crearClienteTemporal(authentication);
        model.addAttribute("cliente", clienteTemp);
        return "usuarios/completar-datos";
    }

    // Procesar formulario de completar datos
    @PostMapping("/completar-datos")
    public String procesarCompletarDatos(
            @RequestParam("dni") String dni,
            @RequestParam(value = "telefono", required = false) String telefono,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        if (!servicioUsuarioUnificado.esUsuarioOAuth2(authentication)) {
            return "redirect:/dashboard";
        }

        // Validaciones
        if (dni == null || dni.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "El DNI es obligatorio");
            return "redirect:/completar-datos";
        }

        if (servicioCliente.verificarDni(dni.trim().toUpperCase())) {
            redirectAttributes.addFlashAttribute("error", "El DNI ya está registrado");
            return "redirect:/completar-datos";
        }

        String email = servicioUsuarioUnificado.obtenerEmail(authentication);
        if (servicioCliente.verificarEmail(email)) {
            redirectAttributes.addFlashAttribute("error", "El email ya está registrado");
            return "redirect:/completar-datos";
        }

        try {
            Cliente nuevoCliente = servicioUsuarioUnificado.crearClienteOAuth2(authentication, dni, telefono);
            servicioCliente.guardarCliente(nuevoCliente);

            ManejadorMensajes.agregarMensajeExito(redirectAttributes, "¡Registro completado exitosamente!");
            return "redirect:/dashboard";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al completar el registro: " + e.getMessage());
            return "redirect:/completar-datos";
        }
    }
}
