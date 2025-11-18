package com.example.tureserva.controlador;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.tureserva.servicio.ServicioAdministradorComplejo;
import com.example.tureserva.servicio.ServicioComplejoDeportivo;
import com.example.tureserva.servicio.ServicioReserva;
import com.example.tureserva.modelo.AdministradorComplejo;
import com.example.tureserva.modelo.ComplejoDeportivo;
import com.example.tureserva.modelo.Usuario;
import com.example.tureserva.modelo.Reserva;
import com.example.tureserva.modelo.enums.EstadoReserva;
import com.example.tureserva.modelo.enums.MetodoPago;
import com.example.tureserva.utiles.ValidadorFormulario;
import com.example.tureserva.utiles.ManejadorMensajes;
import com.example.tureserva.utiles.ValidadorContrasena;
import com.example.tureserva.repositorio.RepositorioReserva;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin-complejo")
public class ControladorAdministradorComplejo {

    private final ServicioAdministradorComplejo servicioAdministradorComplejo;
    private final ServicioComplejoDeportivo servicioComplejoDeportivo;
    private final ServicioReserva servicioReserva;
    private final RepositorioReserva repositorioReserva;

    public ControladorAdministradorComplejo(ServicioAdministradorComplejo servicioAdministradorComplejo,
                                          ServicioComplejoDeportivo servicioComplejoDeportivo,
                                          ServicioReserva servicioReserva,
                                          RepositorioReserva repositorioReserva) {
        this.servicioAdministradorComplejo = servicioAdministradorComplejo;
        this.servicioComplejoDeportivo = servicioComplejoDeportivo;
        this.servicioReserva = servicioReserva;
        this.repositorioReserva = repositorioReserva;
    }

    // ===== DASHBOARD ADMINISTRADOR COMPLEJO =====

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        AdministradorComplejo adminComplejo = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(email);
        Usuario usuario = (Usuario) adminComplejo;
        if (adminComplejo == null) {
            ManejadorMensajes.agregarMensajeError(model, "No se pudo cargar el perfil del Administrador de Complejo");
            return "redirect:/login";
        }
        model.addAttribute("usuario", usuario);
        model.addAttribute("adminComplejo", adminComplejo);

        return "admin-complejo/dashboard";
    }

    // ===== PERFIL DEL ADMINISTRADOR COMPLEJO =====

    @GetMapping("/perfil")
    public String verPerfil(Authentication authentication, Model model) {
        String email = authentication.getName();
        AdministradorComplejo adminComplejo = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(email);
        
        if (adminComplejo == null) {
            ManejadorMensajes.agregarMensajeError(model, ManejadorMensajes.PERFIL_NO_ENCONTRADO);
            return "redirect:/admin-complejo/dashboard";
        }
        
        model.addAttribute("adminComplejo", adminComplejo);
        return "admin-complejo/perfil/ver";
    }

    @GetMapping("/perfil/editar")
    public String mostrarFormularioEditarPerfil(Authentication authentication, Model model) {
        String email = authentication.getName();
        AdministradorComplejo adminComplejo = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(email);
        
        if (adminComplejo == null) {
            ManejadorMensajes.agregarMensajeError(model, ManejadorMensajes.PERFIL_NO_ENCONTRADO);
            return "redirect:/admin-complejo/dashboard";
        }
        
        // Limpiar contraseña para no mostrarla
        adminComplejo.setContrasena("");
        model.addAttribute("adminComplejo", adminComplejo);
        return "admin-complejo/perfil/editar";
    }

    @PostMapping("/perfil/editar")
    public String actualizarPerfil(@ModelAttribute("adminComplejo") AdministradorComplejo adminFormulario,
                                  BindingResult bindingResult,
                                  Authentication authentication,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        
        String emailActual = authentication.getName();
        AdministradorComplejo existente = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(emailActual);
        
        if (existente == null) {
            ManejadorMensajes.agregarMensajeError(redirectAttributes, ManejadorMensajes.PERFIL_NO_ENCONTRADO);
            return "redirect:/admin-complejo/perfil";
        }

        // Validaciones completas usando utilidad (incluyendo DNI)
        ValidadorFormulario.validarCamposCompletosUsuario(
            adminFormulario.getNombre(), 
            adminFormulario.getApellido(), 
            adminFormulario.getEmail(), 
            adminFormulario.getDni(),
            bindingResult
        );

        // Verificar si el email cambió y si ya existe
        if (!adminFormulario.getEmail().equals(emailActual) && 
            servicioAdministradorComplejo.verificarEmail(adminFormulario.getEmail())) {
            bindingResult.rejectValue("email", "error.adminComplejo", ManejadorMensajes.EMAIL_EN_USO);
        }

        // Verificar si el DNI cambió y ya existe
        if (adminFormulario.getDni() != null && !adminFormulario.getDni().trim().isEmpty()) {
            String dniExistente = existente.getDni() != null ? existente.getDni() : "";
            if (!adminFormulario.getDni().equals(dniExistente) && servicioAdministradorComplejo.verificarDni(adminFormulario.getDni())) {
                bindingResult.rejectValue("dni", "error.adminComplejo", "El DNI ya está registrado");
            }
        }

        if (bindingResult.hasErrors()) {
            return "admin-complejo/perfil/editar";
        }

        try {
            // Actualizar datos
            existente.setNombre(adminFormulario.getNombre());
            existente.setApellido(adminFormulario.getApellido());
            existente.setEmail(adminFormulario.getEmail());
            existente.setDni(adminFormulario.getDni());

            servicioAdministradorComplejo.actualizarAdministrador(existente);
            ManejadorMensajes.agregarMensajeExito(redirectAttributes, ManejadorMensajes.PERFIL_ACTUALIZADO);
            return "redirect:/admin-complejo/perfil";
            
        } catch (Exception e) {
            ManejadorMensajes.agregarMensajeError(model, ManejadorMensajes.ERROR_GENERICO);
            return "admin-complejo/perfil/editar";
        }
    }

    @GetMapping("/perfil/cambiar-contrasena")
    public String mostrarFormularioCambiarContrasena() {
        return "admin-complejo/perfil/cambiar-contrasena";
    }

    @PostMapping("/perfil/cambiar-contrasena")
    public String cambiarContrasena(@RequestParam("contrasenaActual") String contrasenaActual,
                                   @RequestParam("nuevaContrasena") String nuevaContrasena,
                                   @RequestParam("confirmarContrasena") String confirmarContrasena,
                                   Authentication authentication,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        
        String email = authentication.getName();
        AdministradorComplejo adminComplejo = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(email);
        
        if (adminComplejo == null) {
            ManejadorMensajes.agregarMensajeError(model, ManejadorMensajes.USUARIO_NO_ENCONTRADO);
            return "admin-complejo/perfil/cambiar-contrasena";
        }

        // Usar validador unificado de contraseñas
        String errorValidacion = ValidadorContrasena.validarCambioContrasena(
            contrasenaActual, nuevaContrasena, confirmarContrasena, 
            model, null, "admin-complejo/perfil/cambiar-contrasena"
        );
        
        if (errorValidacion != null) {
            return errorValidacion;
        }

        try {
            boolean actualizado = servicioAdministradorComplejo.cambiarContrasena(adminComplejo.getId(), contrasenaActual, nuevaContrasena);
            
            if (actualizado) {
                ManejadorMensajes.agregarMensajeExito(redirectAttributes, ManejadorMensajes.CONTRASENA_ACTUALIZADA);
                return "redirect:/admin-complejo/perfil";
            } else {
                ManejadorMensajes.agregarMensajeError(model, ManejadorMensajes.CONTRASENA_ACTUAL_INCORRECTA);
                return "admin-complejo/perfil/cambiar-contrasena";
            }
            
        } catch (Exception e) {
            ManejadorMensajes.agregarMensajeError(model, ManejadorMensajes.ERROR_GENERICO);
            return "admin-complejo/perfil/cambiar-contrasena";
        }
    }

    // ===== GESTIÓN DE COMPLEJOS =====

    /**
     * Redirigir de /admin-complejo a /admin-complejo/mis-complejos
     */
    @GetMapping("")
    public String redirectToMisComplejos() {
        return "redirect:/admin-complejo/mis-complejos";
    }

    /**
     * Mostrar los complejos del administrador logueado
     */
    @GetMapping("/mis-complejos")
    public String misComplejos(Model model, Authentication authentication) {
        try {
            // Obtener el email del usuario autenticado
            String emailUsuario = authentication.getName();
            
            // Buscar el administrador por email
            AdministradorComplejo administrador = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(emailUsuario);
            if (administrador == null) {
                ManejadorMensajes.agregarMensajeError(model, "No se encontró el administrador de complejo");
                return "admin-complejo/mis-complejos";
            }

            // Obtener los complejos del administrador
            List<ComplejoDeportivo> misComplejos = servicioComplejoDeportivo.obtenerComplejosPorAdministradorYActivoTrue(administrador.getId());
            
            System.out.println("DEBUG: Administrador ID: " + administrador.getId() + 
                             ", Email: " + emailUsuario + 
                             ", Complejos encontrados: " + misComplejos.size());

            model.addAttribute("administrador", administrador);
            model.addAttribute("complejos", misComplejos);
            
            return "admin-complejo/mis-complejos";
        } catch (Exception e) {
            e.printStackTrace();
            ManejadorMensajes.agregarMensajeError(model, "Error al cargar sus complejos: " + e.getMessage());
            return "admin-complejo/mis-complejos";
        }
    }

    /**
     * Gestionar complejo específico (proximamente)
     */
    @GetMapping("/gestionar/{id}")
    public String gestionarComplejo(@PathVariable Long id, Model model, Authentication authentication) {
        try {
            // Obtener el email del usuario autenticado
            String emailUsuario = authentication.getName();
            
            // Buscar el administrador por email
            AdministradorComplejo administrador = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(emailUsuario);
            if (administrador == null) {
                ManejadorMensajes.agregarMensajeError(model, "No se encontró el administrador de complejo");
                return "redirect:/admin-complejo/mis-complejos";
            }

            // Verificar que el complejo pertenece al administrador
            ComplejoDeportivo complejo = servicioComplejoDeportivo.obtenerPorId(id)
                .orElseThrow(() -> new RuntimeException("Complejo no encontrado"));
            
            if (!complejo.getAdministradorComplejo().getId().equals(administrador.getId())) {
                ManejadorMensajes.agregarMensajeError(model, "No tiene permisos para gestionar este complejo");
                return "redirect:/admin-complejo/mis-complejos";
            }

            model.addAttribute("complejo", complejo);
            
            return "admin-complejo/gestionar";
        } catch (Exception e) {
            e.printStackTrace();
            ManejadorMensajes.agregarMensajeError(model, "Error al cargar el complejo: " + e.getMessage());
            return "redirect:/admin-complejo/mis-complejos";
        }
    }
    
    // ==================== GESTIÓN DE RESERVAS ====================
    
    /**
     * Muestra las reservas del complejo seleccionado con filtros opcionales y paginación.
     */
    @GetMapping("/reservas/{complejoId}")
    public String verReservasComplejo(
            @PathVariable("complejoId") Long complejoId,
            @RequestParam(value = "fecha", required = false) String fechaStr,
            @RequestParam(value = "estado", required = false) String estadoStr,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Validar que el complejo pertenece al administrador
            String emailUsuario = authentication.getName();
            AdministradorComplejo administrador = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(emailUsuario);
            
            if (administrador == null) {
                ManejadorMensajes.agregarMensajeError(redirectAttributes, "No se encontró el administrador");
                return "redirect:/admin-complejo/mis-complejos";
            }
            
            ComplejoDeportivo complejo = servicioComplejoDeportivo.obtenerPorId(complejoId)
                .orElseThrow(() -> new RuntimeException("Complejo no encontrado"));
            
            if (!complejo.getAdministradorComplejo().getId().equals(administrador.getId())) {
                ManejadorMensajes.agregarMensajeError(redirectAttributes, "No tiene permisos para ver este complejo");
                return "redirect:/admin-complejo/mis-complejos";
            }
            
            // Delegar llenado del modelo y renderizado
            cargarDatosReservas(complejo, fechaStr, estadoStr, page, model);
            return "admin-complejo/reservas/listar";
            
        } catch (Exception e) {
            e.printStackTrace();
            ManejadorMensajes.agregarMensajeError(redirectAttributes, "Error al cargar las reservas: " + e.getMessage());
            return "redirect:/admin-complejo/mis-complejos";
        }
    }

    /**
     * Endpoint que devuelve únicamente el fragmento HTML de la grilla (tabla + paginación)
     * para que la UI pueda cargar páginas vía AJAX sin recargar toda la página.
     */
    @GetMapping("/reservas/{complejoId}/fragment")
    public String verReservasFragment(
            @PathVariable("complejoId") Long complejoId,
            @RequestParam(value = "fecha", required = false) String fechaStr,
            @RequestParam(value = "estado", required = false) String estadoStr,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            String emailUsuario = authentication.getName();
            AdministradorComplejo administrador = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(emailUsuario);

            if (administrador == null) {
                ManejadorMensajes.agregarMensajeError(redirectAttributes, "No se encontró el administrador");
                return "admin-complejo/reservas/listar :: grid"; // devolver fragment vacío/consistente
            }

            ComplejoDeportivo complejo = servicioComplejoDeportivo.obtenerPorId(complejoId)
                .orElseThrow(() -> new RuntimeException("Complejo no encontrado"));

            if (!complejo.getAdministradorComplejo().getId().equals(administrador.getId())) {
                ManejadorMensajes.agregarMensajeError(redirectAttributes, "No tiene permisos para ver este complejo");
                return "admin-complejo/reservas/listar :: grid";
            }

            cargarDatosReservas(complejo, fechaStr, estadoStr, page, model);
            // Devolver sólo el fragmento llamado 'grid' del template
            return "admin-complejo/reservas/listar :: grid";

        } catch (Exception e) {
            e.printStackTrace();
            ManejadorMensajes.agregarMensajeError(redirectAttributes, "Error al cargar las reservas: " + e.getMessage());
            return "admin-complejo/reservas/listar :: grid";
        }
    }

    /**
     * Extrae la lógica que carga la lista de reservas y estadísticas en el model.
     */
    private void cargarDatosReservas(ComplejoDeportivo complejo,
                                     String fechaStr,
                                     String estadoStr,
                                     int page,
                                     Model model) {
        // Configurar paginación (20 elementos por página)
        Pageable pageable = PageRequest.of(page, 20);

        // Obtener IDs de reservas según filtros con paginación (primer paso)
        Page<Long> paginaIds;
        LocalDate fecha = fechaStr != null && !fechaStr.isEmpty() ? LocalDate.parse(fechaStr) : null;
        EstadoReserva estado = null;

        if (estadoStr != null && !estadoStr.isEmpty()) {
            try {
                estado = EstadoReserva.valueOf(estadoStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Estado inválido, ignorar
            }
        }

        Long complejoId = complejo.getId_complejo();

        if (fecha != null && estado != null) {
            paginaIds = repositorioReserva.findIdsByComplejoDeportivoIdAndFechaAndEstado(complejoId, fecha, estado, pageable);
        } else if (fecha != null) {
            paginaIds = repositorioReserva.findIdsByComplejoDeportivoIdAndFecha(complejoId, fecha, pageable);
        } else if (estado != null) {
            paginaIds = repositorioReserva.findIdsByComplejoDeportivoIdAndEstado(complejoId, estado, pageable);
        } else {
            paginaIds = repositorioReserva.findIdsByComplejoDeportivoId(complejoId, pageable);
        }

        // Cargar reservas completas con todos sus datos (segundo paso)
        List<Reserva> reservas = paginaIds.hasContent()
                ? repositorioReserva.findByIdInWithDetalles(paginaIds.getContent())
                : List.of();

        // Calcular estadísticas (sin paginación para totales)
        long reservasPendientes = repositorioReserva.countByComplejoDeportivoIdAndEstado(complejoId, EstadoReserva.PENDIENTE);
        long reservasConfirmadas = repositorioReserva.countByComplejoDeportivoIdAndEstado(complejoId, EstadoReserva.CONFIRMADA);
        long reservasFinalizadas = repositorioReserva.countByComplejoDeportivoIdAndEstado(complejoId, EstadoReserva.FINALIZADA);

        model.addAttribute("complejo", complejo);
        model.addAttribute("reservas", reservas);
        model.addAttribute("paginaActual", page);
        model.addAttribute("totalPaginas", paginaIds.getTotalPages());
        model.addAttribute("totalReservas", paginaIds.getTotalElements());
        model.addAttribute("reservasPendientes", reservasPendientes);
        model.addAttribute("reservasConfirmadas", reservasConfirmadas);
        model.addAttribute("reservasFinalizadas", reservasFinalizadas);
        model.addAttribute("fechaFiltro", fechaStr);
        model.addAttribute("estadoFiltro", estadoStr);
        model.addAttribute("estadosDisponibles", EstadoReserva.values());
    }
    
    /**
     * Confirma una reserva pendiente.
     */
        @PostMapping("/reservas/{reservaId}/confirmar")
        public String confirmarReserva(
            @PathVariable("reservaId") String reservaId,
            @RequestParam("complejoId") Long complejoId,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Validar permisos
            if (!validarPermisoComplejo(complejoId, authentication)) {
                ManejadorMensajes.agregarMensajeError(redirectAttributes, "No tiene permisos para realizar esta acción");
                return "redirect:/admin-complejo/mis-complejos";
            }
            
            Long resolvedId = resolveReservaId(reservaId);
            servicioReserva.confirmarReservaPendiente(resolvedId);
            ManejadorMensajes.agregarMensajeExito(redirectAttributes, "Reserva confirmada exitosamente");
            
        } catch (Exception e) {
            ManejadorMensajes.agregarMensajeError(redirectAttributes, "Error al confirmar reserva: " + e.getMessage());
        }
        
        return "redirect:/admin-complejo/reservas/" + complejoId;
    }
    
    /**
     * Finaliza una reserva confirmada (registra pago completo).
     */
        @PostMapping("/reservas/{reservaId}/finalizar")
        public String finalizarReserva(
            @PathVariable("reservaId") String reservaId,
            @RequestParam("complejoId") Long complejoId,
            @RequestParam("metodoPago") String metodoPagoStr,
            @RequestParam(value = "numeroComprobante", required = false) String numeroComprobante,
            @RequestParam(value = "notas", required = false) String notas,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Validar permisos
            if (!validarPermisoComplejo(complejoId, authentication)) {
                ManejadorMensajes.agregarMensajeError(redirectAttributes, "No tiene permisos para realizar esta acción");
                return "redirect:/admin-complejo/mis-complejos";
            }
            
            // Obtener administrador
            String emailUsuario = authentication.getName();
            AdministradorComplejo administrador = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(emailUsuario);
            
            // Convertir método de pago
            MetodoPago metodoPago;
            try {
                metodoPago = MetodoPago.valueOf(metodoPagoStr);
            } catch (IllegalArgumentException e) {
                ManejadorMensajes.agregarMensajeError(redirectAttributes, "Método de pago inválido");
                return "redirect:/admin-complejo/reservas/" + complejoId;
            }
            
            Long resolvedId = resolveReservaId(reservaId);
            servicioReserva.finalizarReservaConPago(resolvedId, metodoPago, numeroComprobante, notas, administrador);
            ManejadorMensajes.agregarMensajeExito(redirectAttributes, "Reserva finalizada - Pago completo registrado");
            
        } catch (Exception e) {
            ManejadorMensajes.agregarMensajeError(redirectAttributes, "Error al finalizar reserva: " + e.getMessage());
        }
        
        return "redirect:/admin-complejo/reservas/" + complejoId;
    }
    
    /**
     * Cancela una reserva por parte del administrador.
     */
        @PostMapping("/reservas/{reservaId}/cancelar")
        public String cancelarReservaPorAdmin(
            @PathVariable("reservaId") String reservaId,
            @RequestParam("complejoId") Long complejoId,
            @RequestParam(value = "motivo", required = false) String motivo,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Validar permisos
            if (!validarPermisoComplejo(complejoId, authentication)) {
                ManejadorMensajes.agregarMensajeError(redirectAttributes, "No tiene permisos para realizar esta acción");
                return "redirect:/admin-complejo/mis-complejos";
            }
            
            Long resolvedId = resolveReservaId(reservaId);
            servicioReserva.cancelarReservaPorAdmin(resolvedId, motivo);
            ManejadorMensajes.agregarMensajeExito(redirectAttributes, "Reserva cancelada exitosamente");
            
        } catch (Exception e) {
            ManejadorMensajes.agregarMensajeError(redirectAttributes, "Error al cancelar reserva: " + e.getMessage());
        }
        
        return "redirect:/admin-complejo/reservas/" + complejoId;
    }
    
    /**
     * Método auxiliar para validar que el complejo pertenece al administrador autenticado.
     */
    private boolean validarPermisoComplejo(Long complejoId, Authentication authentication) {
        String emailUsuario = authentication.getName();
        AdministradorComplejo administrador = servicioAdministradorComplejo.obtenerAdministradorActivoPorEmail(emailUsuario);
        
        if (administrador == null) {
            return false;
        }
        
        ComplejoDeportivo complejo = servicioComplejoDeportivo.obtenerPorId(complejoId).orElse(null);
        
        return complejo != null && complejo.getAdministradorComplejo().getId().equals(administrador.getId());
    }

    /**
     * Muestra el detalle de una reserva para el administrador del complejo.
     */
    @GetMapping("/reservas/{reservaId}/detalle")
    public String verDetalleReservaAdmin(@PathVariable("reservaId") String reservaId,
                                         Authentication authentication,
                                         Model model,
                                         RedirectAttributes redirectAttributes) {
        try {
            // Cargar reserva (aceptamos ID numérico o codigoReserva)
            Reserva reserva = obtenerReservaPorIdOrCodigo(reservaId)
                    .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

            // Obtener complejo asociado a la primera reserva (asumimos al menos un detalle)
            if (reserva.getDetalles() == null || reserva.getDetalles().isEmpty()) {
                ManejadorMensajes.agregarMensajeError(redirectAttributes, "Reserva sin detalles asociados");
                return "redirect:/admin-complejo/mis-complejos";
            }

            Long complejoId = reserva.getDetalles().get(0).getEspacioReservable().getComplejoDeportivo().getId_complejo();

            // Validar permisos del administrador
            if (!validarPermisoComplejo(complejoId, authentication)) {
                ManejadorMensajes.agregarMensajeError(redirectAttributes, "No tiene permisos para ver esta reserva");
                return "redirect:/admin-complejo/mis-complejos";
            }

            model.addAttribute("reserva", reserva);
            model.addAttribute("complejoId", complejoId);
            return "admin-complejo/reservas/detalle";
        } catch (Exception e) {
            e.printStackTrace();
            ManejadorMensajes.agregarMensajeError(redirectAttributes, "Error al cargar el detalle de la reserva: " + e.getMessage());
            return "redirect:/admin-complejo/mis-complejos";
        }
    }

    /**
     * Intenta resolver una reserva por ID numérico o por su código único.
     */
    private java.util.Optional<Reserva> obtenerReservaPorIdOrCodigo(String idOrCodigo) {
        // Intentar parsear como Long
        try {
            Long id = Long.parseLong(idOrCodigo);
            return servicioReserva.obtenerReservaPorId(id);
        } catch (NumberFormatException e) {
            // No es numérico, buscar por código
            return repositorioReserva.findByCodigoReservaWithDetalles(idOrCodigo);
        }
    }

    /**
     * Resuelve un ID de reserva (lanza RuntimeException si no existe).
     */
    private Long resolveReservaId(String idOrCodigo) {
        return obtenerReservaPorIdOrCodigo(idOrCodigo)
                .map(Reserva::getId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada: " + idOrCodigo));
    }
}
