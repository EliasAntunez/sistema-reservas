package com.example.tureserva.controlador;

import com.example.tureserva.modelo.*;
import com.example.tureserva.modelo.enums.EstadoReserva;
import com.example.tureserva.repositorio.RepositorioCliente;
import com.example.tureserva.repositorio.RepositorioReserva;
import com.example.tureserva.servicio.ServicioComplejoDeportivo;
import com.example.tureserva.servicio.ServicioEspacioReservable;
import com.example.tureserva.servicio.ServicioReserva;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controlador para el sistema de reservas.
 */
@Controller
@RequestMapping("/reservas")
public class ControladorReserva {
    
    private static final Logger logger = LoggerFactory.getLogger(ControladorReserva.class);
    
    private final ServicioComplejoDeportivo servicioComplejo;
    private final ServicioEspacioReservable servicioEspacio;
    private final ServicioReserva servicioReserva;
    private final RepositorioCliente repositorioCliente;
    private final RepositorioReserva repositorioReserva;
    
    public ControladorReserva(ServicioComplejoDeportivo servicioComplejo,
                              ServicioEspacioReservable servicioEspacio,
                              ServicioReserva servicioReserva,
                              RepositorioCliente repositorioCliente,
                              RepositorioReserva repositorioReserva) {
        this.servicioComplejo = servicioComplejo;
        this.servicioEspacio = servicioEspacio;
        this.servicioReserva = servicioReserva;
        this.repositorioCliente = repositorioCliente;
        this.repositorioReserva = repositorioReserva;
    }

    /**
     * Intenta resolver una reserva por ID numérico o por su código único.
     */
    private Optional<Reserva> obtenerReservaPorIdOrCodigo(String idOrCodigo) {
        try {
            Long id = Long.parseLong(idOrCodigo);
            return servicioReserva.obtenerReservaPorId(id);
        } catch (NumberFormatException e) {
            // No es numérico, buscar por código
            return repositorioReserva.findByCodigoReservaWithDetalles(idOrCodigo);
        }
    }

    
    
    /**
     * Muestra la lista de complejos deportivos disponibles con búsqueda y paginación.
     */
    @GetMapping("/nueva")
    public String listarComplejos(
            @RequestParam(value = "buscar", required = false) String buscar,
            @RequestParam(value = "page", defaultValue = "0") int page,
            Model model) {
        
        Pageable pageable = PageRequest.of(page, 10);
        
        Page<ComplejoDeportivo> complejos;
        if (buscar != null && !buscar.trim().isEmpty()) {
            complejos = servicioComplejo.buscarPorNombre(buscar, pageable);
        } else {
            complejos = servicioComplejo.obtenerComplejosActivos(pageable);
        }
        
        model.addAttribute("complejos", complejos);
        model.addAttribute("buscar", buscar);
        model.addAttribute("paginaActual", page);
        
        return "reservas/listar-complejos";
    }
    
    /**
     * Muestra los espacios disponibles de un complejo con sus horarios organizados por tipo.
     */
    @GetMapping("/complejo/{id}")
    public String verEspaciosDisponibles(
            @PathVariable("id") Long idComplejo,
            @RequestParam(value = "fecha", required = false) String fechaStr,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        Optional<ComplejoDeportivo> complejoOpt = servicioComplejo.obtenerPorId(idComplejo);
        if (complejoOpt.isEmpty() || !complejoOpt.get().isActivo()) {
            redirectAttributes.addFlashAttribute("error", "El complejo solicitado no está disponible.");
            return "redirect:/reservas/nueva";
        }
        
        ComplejoDeportivo complejo = complejoOpt.get();
        LocalDate fecha = fechaStr != null && !fechaStr.isEmpty() 
            ? LocalDate.parse(fechaStr) 
            : LocalDate.now();
        
        List<EspacioReservable> espaciosActivos = servicioEspacio.obtenerEspaciosActivosDeComplejo(idComplejo);
        
        logger.debug("Total espacios activos obtenidos: {}", espaciosActivos.size());
        
        // Separar por tipo
        List<EspacioReservable> canchas = espaciosActivos.stream()
            .filter(e -> "CANCHA".equals(e.getTipoEspacio()))
            .collect(Collectors.toList());
            
        List<EspacioReservable> salones = espaciosActivos.stream()
            .filter(e -> "SALON".equals(e.getTipoEspacio()))
            .collect(Collectors.toList());
        
        logger.debug("Canchas: {}, Salones: {}", canchas.size(), salones.size());
        
        Map<String, List<IntervaloDisponible>> intervalosPorEspacio = new HashMap<>();
        for (EspacioReservable espacio : espaciosActivos) {
            List<IntervaloDisponible> intervalos = servicioReserva.generarIntervalosDisponibles(espacio, fecha);
            intervalosPorEspacio.put(String.valueOf(espacio.getId()), intervalos);
            logger.debug("Espacio {} - {} intervalos generados", espacio.getId(), intervalos.size());
        }
        
        model.addAttribute("complejo", complejo);
        model.addAttribute("fecha", fecha);
        model.addAttribute("canchas", canchas);
        model.addAttribute("salones", salones);
        model.addAttribute("totalEspacios", espaciosActivos.size());
        model.addAttribute("intervalosPorEspacio", intervalosPorEspacio);
        
        return "reservas/seleccionar-espacio";
    }
    
    /**
     * Procesa la selección de un horario y lo agrega a la lista de items en sesión.
     * Soporta agregar múltiples espacios a una misma reserva.
     */
    @PostMapping("/seleccionar-horario")
    public String seleccionarHorario(
            @RequestParam("espacioId") Long espacioId,
            @RequestParam("fecha") String fechaStr,
            @RequestParam("horaInicio") String horaInicioStr,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        
        try {
            LocalDate fecha = LocalDate.parse(fechaStr);
            LocalTime horaInicio = LocalTime.parse(horaInicioStr);
            
            // Validar que el espacio existe
            Optional<EspacioReservable> espacioOpt = servicioEspacio.obtenerPorId(espacioId);
            if (espacioOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "El espacio seleccionado no existe.");
                return "redirect:/reservas/nueva";
            }
            
            EspacioReservable espacio = espacioOpt.get();
            Long complejoId = espacio.getComplejoDeportivo().getId_complejo();
            
            // Obtener o crear DatosReservaTemp desde la sesión
            DatosReservaTemp datosReserva = (DatosReservaTemp) session.getAttribute("datosReserva");
            
            if (datosReserva == null) {
                // Primera selección: crear nuevo DatosReservaTemp
                datosReserva = new DatosReservaTemp(complejoId, fecha);
            } else {
                // Validar que sea el mismo complejo y fecha
                if (!datosReserva.getComplejoId().equals(complejoId)) {
                    redirectAttributes.addFlashAttribute("error", "Todos los espacios deben ser del mismo complejo.");
                    return "redirect:/reservas/complejo/" + complejoId + "?fecha=" + fechaStr;
                }
                if (!datosReserva.getFecha().equals(fecha)) {
                    redirectAttributes.addFlashAttribute("error", "Todos los espacios deben ser para la misma fecha.");
                    return "redirect:/reservas/complejo/" + complejoId + "?fecha=" + fechaStr;
                }
            }
            
            // Verificar si ya se agregó este espacio con el mismo horario
            if (datosReserva.existeEspacioConHorario(espacioId, horaInicio)) {
                redirectAttributes.addFlashAttribute("error", "Ya agregaste este espacio con ese horario.");
                return "redirect:/reservas/confirmar";
            }
            
            // Crear el item con los datos del espacio para mostrar en UI
            ItemReserva item = new ItemReserva(espacioId, horaInicio, 1);
            item.setNombreEspacio(espacio.getNombre());
            item.setTipoEspacio(espacio.getTipoEspacio());
            item.setPrecioPorHora(espacio.getPrecioPorHora().doubleValue());
            
            // Agregar a la lista
            datosReserva.agregarItem(item);
            
            // Guardar en sesión
            session.setAttribute("datosReserva", datosReserva);
            
            logger.debug("Item agregado - Espacio: {}, Fecha: {}, Hora: {}. Total items: {}", 
                espacioId, fecha, horaInicio, datosReserva.cantidadEspacios());
            
            redirectAttributes.addFlashAttribute("mensaje", "Espacio agregado a tu reserva.");
            return "redirect:/reservas/confirmar";
            
        } catch (Exception e) {
            logger.error("Error al seleccionar horario: {}", e.getMessage(), e);
            
            // Si hay datos en sesión, intentar volver a confirmar en lugar de nueva
            DatosReservaTemp datosExistentes = (DatosReservaTemp) session.getAttribute("datosReserva");
            if (datosExistentes != null && datosExistentes.tieneEspacios()) {
                redirectAttributes.addFlashAttribute("error", "Error al agregar espacio: " + e.getMessage());
                return "redirect:/reservas/confirmar";
            }
            
            redirectAttributes.addFlashAttribute("error", "Error al procesar la selección: " + e.getMessage());
            return "redirect:/reservas/nueva";
        }
    }
    
    /**
     * Muestra la vista de confirmación con todos los espacios agregados.
     * Permite agregar más espacios o confirmar la reserva.
     */
    @GetMapping("/confirmar")
    public String mostrarConfirmacion(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        
        DatosReservaTemp datosReserva = (DatosReservaTemp) session.getAttribute("datosReserva");
        
        if (datosReserva == null || !datosReserva.tieneEspacios()) {
            redirectAttributes.addFlashAttribute("error", "No hay espacios agregados a la reserva.");
            return "redirect:/reservas/nueva";
        }
        
        // Obtener el complejo para poder volver a agregar más espacios
        Optional<ComplejoDeportivo> complejoOpt = servicioComplejo.obtenerPorId(datosReserva.getComplejoId());
        if (complejoOpt.isEmpty()) {
            session.removeAttribute("datosReserva");
            redirectAttributes.addFlashAttribute("error", "El complejo no existe.");
            return "redirect:/reservas/nueva";
        }
        
        ComplejoDeportivo complejo = complejoOpt.get();
        
        // Validar que todos los espacios en la lista existan (por si fueron eliminados)
        List<ItemReserva> itemsInvalidos = new ArrayList<>();
        for (int i = 0; i < datosReserva.getItems().size(); i++) {
            ItemReserva item = datosReserva.getItems().get(i);
            Optional<EspacioReservable> espacioOpt = servicioEspacio.obtenerPorId(item.getEspacioId());
            if (espacioOpt.isEmpty()) {
                itemsInvalidos.add(item);
                logger.warn("Espacio {} no encontrado, será removido de la reserva", item.getEspacioId());
            }
        }
        
        // Remover items inválidos
        for (ItemReserva itemInvalido : itemsInvalidos) {
            datosReserva.getItems().remove(itemInvalido);
        }
        
        // Si no quedan items válidos, volver atrás
        if (!datosReserva.tieneEspacios()) {
            session.removeAttribute("datosReserva");
            redirectAttributes.addFlashAttribute("error", "Los espacios seleccionados ya no están disponibles.");
            return "redirect:/reservas/nueva";
        }
        
        // Actualizar sesión con datos limpios
        if (!itemsInvalidos.isEmpty()) {
            session.setAttribute("datosReserva", datosReserva);
            model.addAttribute("mensaje", itemsInvalidos.size() + " espacio(s) ya no disponible(s) fueron removidos.");
        }
        
        // Verificar si algún espacio requiere seña (para validación)
        boolean requiereSeña = false;
        for (ItemReserva item : datosReserva.getItems()) {
            Optional<EspacioReservable> espacioOpt = servicioEspacio.obtenerPorId(item.getEspacioId());
            if (espacioOpt.isPresent() && espacioOpt.get().getPoliticaSenia() != null) {
                requiereSeña = true;
                break;
            }
        }
        
        logger.debug("Mostrando confirmación - Complejo: {}, Fecha: {}, Items: {}, Total: ${}", 
            complejo.getId_complejo(), datosReserva.getFecha(), 
            datosReserva.cantidadEspacios(), datosReserva.calcularMontoTotal());
        
        model.addAttribute("complejo", complejo);
        model.addAttribute("datosReserva", datosReserva);
        model.addAttribute("requiereSeña", requiereSeña);
        
        return "reservas/confirmar-reserva";
    }
    
    /**
     * Remueve un espacio de la lista de items en la reserva temporal.
     */
    @PostMapping("/remover-espacio")
    public String removerEspacio(
            @RequestParam("indice") int indice,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        
        DatosReservaTemp datosReserva = (DatosReservaTemp) session.getAttribute("datosReserva");
        
        if (datosReserva == null || !datosReserva.tieneEspacios()) {
            redirectAttributes.addFlashAttribute("error", "No hay espacios en la reserva.");
            return "redirect:/reservas/nueva";
        }
        
        if (datosReserva.removerItem(indice)) {
            session.setAttribute("datosReserva", datosReserva);
            logger.debug("Item removido. Items restantes: {}", datosReserva.cantidadEspacios());
            redirectAttributes.addFlashAttribute("mensaje", "Espacio removido de la reserva.");
            
            // Si no quedan espacios, volver a la lista de complejos
            if (!datosReserva.tieneEspacios()) {
                session.removeAttribute("datosReserva");
                return "redirect:/reservas/nueva";
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "No se pudo remover el espacio.");
        }
        
        return "redirect:/reservas/confirmar";
    }
    
    /**
     * Modifica la duración de un item específico.
     */
    @PostMapping("/modificar-duracion")
    public String modificarDuracion(
            @RequestParam("indice") int indice,
            @RequestParam("accion") String accion,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        
        DatosReservaTemp datosReserva = (DatosReservaTemp) session.getAttribute("datosReserva");
        
        if (datosReserva == null || !datosReserva.tieneEspacios()) {
            redirectAttributes.addFlashAttribute("error", "No hay espacios en la reserva.");
            return "redirect:/reservas/nueva";
        }
        
        Optional<ItemReserva> itemOpt = datosReserva.getItem(indice);
        if (itemOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "El espacio no existe.");
            return "redirect:/reservas/confirmar";
        }
        
        ItemReserva item = itemOpt.get();
        
        if ("extender".equals(accion)) {
            if (item.extenderUnaHora()) {
                session.setAttribute("datosReserva", datosReserva);
                redirectAttributes.addFlashAttribute("mensaje", "Duración extendida a " + item.getDuracionHoras() + " hora(s).");
            } else {
                redirectAttributes.addFlashAttribute("error", "No se puede extender más (máximo 3 horas).");
            }
        } else if ("reducir".equals(accion)) {
            if (item.reducirUnaHora()) {
                session.setAttribute("datosReserva", datosReserva);
                redirectAttributes.addFlashAttribute("mensaje", "Duración reducida a " + item.getDuracionHoras() + " hora(s).");
            } else {
                redirectAttributes.addFlashAttribute("error", "No se puede reducir más (mínimo 1 hora).");
            }
        }
        
        return "redirect:/reservas/confirmar";
    }
    
    /**
     * Confirma y crea la reserva final en la base de datos.
     * Procesa todos los espacios agregados en la sesión.
     */
    @PostMapping("/confirmar-final")
    public String confirmarReservaFinal(HttpSession session, 
                                       Authentication authentication,
                                       RedirectAttributes redirectAttributes) {
        
        DatosReservaTemp datosReserva = (DatosReservaTemp) session.getAttribute("datosReserva");
        
        if (datosReserva == null || !datosReserva.tieneEspacios()) {
            redirectAttributes.addFlashAttribute("error", "No hay espacios agregados a la reserva.");
            return "redirect:/reservas/nueva";
        }
        
        try {
            // Validar que ningún espacio requiera seña (por ahora no soportado)
            for (ItemReserva item : datosReserva.getItems()) {
                Optional<EspacioReservable> espacioOpt = servicioEspacio.obtenerPorId(item.getEspacioId());
                if (espacioOpt.isPresent() && espacioOpt.get().getPoliticaSenia() != null) {
                    redirectAttributes.addFlashAttribute("error", "Uno o más espacios requieren pago de seña.");
                    return "redirect:/reservas/confirmar";
                }
            }
            
            // Obtener el email del usuario autenticado (diferente para OAuth2 vs tradicional)
            String email;
            if (authentication.getPrincipal() instanceof OAuth2User) {
                OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
                email = oauth2User.getAttribute("email");
                logger.debug("Usuario OAuth2 detectado. Email extraído: {}", email);
            } else {
                email = authentication.getName();
                logger.debug("Usuario tradicional detectado. Email: {}", email);
            }
            
            if (email == null || email.isEmpty()) {
                throw new IllegalStateException("No se pudo obtener el email del usuario autenticado");
            }
            
            // Buscar el cliente en la base de datos usando el email
            Cliente cliente = repositorioCliente.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("No tienes un perfil de cliente asociado. Solo los clientes pueden realizar reservas."));
            
            logger.debug("Cliente encontrado: ID={}, Email={}", cliente.getId(), cliente.getEmail());
            
            // Crear la reserva usando el servicio (ahora procesa múltiples items)
            Reserva reserva = servicioReserva.crearReservaDesdeDatosTemp(cliente, datosReserva);
            
            // Limpiar sesión
            session.removeAttribute("datosReserva");
            
            logger.info("Reserva {} creada exitosamente para cliente {} con {} espacios", 
                reserva.getId(), cliente.getId(), reserva.getDetalles().size());
            
            // Redirigir a vista de éxito
            return "redirect:/reservas/exitosa/" + reserva.getId();
            
        } catch (Exception e) {
            logger.error("Error al confirmar reserva: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error al confirmar la reserva: " + e.getMessage());
            return "redirect:/reservas/confirmar";
        }
    }
    
    /**
     * Muestra la vista de reserva exitosa.
     */
    @GetMapping("/exitosa/{id}")
    public String mostrarReservaExitosa(@PathVariable("id") Long reservaId, Model model, RedirectAttributes redirectAttributes) {
        
        Optional<Reserva> reservaOpt = servicioReserva.obtenerReservaPorId(reservaId);
        
        if (reservaOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "La reserva solicitada no existe.");
            return "redirect:/reservas/nueva";
        }
        
        model.addAttribute("reserva", reservaOpt.get());
        return "reservas/reserva-exitosa";
    }
    
    /**
     * Muestra las reservas del cliente autenticado con filtros opcionales.
     */
    @GetMapping("/mis-reservas")
        public String mostrarMisReservas(
            @RequestParam(value = "estado", required = false) String estadoStr,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            prepararMisReservasModel(estadoStr, page, size, authentication, model);
            return "reservas/mis-reservas";
        } catch (Exception e) {
            logger.error("Error al cargar reservas del cliente: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error al cargar tus reservas: " + e.getMessage());
            return "redirect:/dashboard";
        }
    }

    /**
     * Endpoint que devuelve solo el fragmento con la grilla de "mis-reservas".
     * Usado por la paginación AJAX para reemplazar la tabla sin recargar toda la página.
     */
    @GetMapping("/mis-reservas/fragment")
    public String mostrarMisReservasFragment(
            @RequestParam(value = "estado", required = false) String estadoStr,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            prepararMisReservasModel(estadoStr, page, size, authentication, model);
            return "reservas/mis-reservas :: grid";
        } catch (Exception e) {
            logger.error("Error al cargar fragment de reservas: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error al cargar tus reservas: " + e.getMessage());
            return "redirect:/dashboard";
        }
    }

    /**
     * Extrae la lógica común para obtener las reservas y estadísticas y poblar el modelo.
     */
    private void prepararMisReservasModel(String estadoStr, int page, int size, Authentication authentication, Model model) throws Exception {
        // Obtener email del usuario autenticado (OAuth2 o tradicional)
        String email;
        if (authentication.getPrincipal() instanceof OAuth2User) {
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            email = oauth2User.getAttribute("email");
        } else {
            email = authentication.getName();
        }

        // Buscar cliente por email
        Cliente cliente = repositorioCliente.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Debe tener un perfil de cliente para ver reservas"));

        // Usar paginación two-step desde ServicioReserva
        EstadoReserva estado = null;
        if (estadoStr != null && !estadoStr.isEmpty()) {
            try {
                estado = EstadoReserva.valueOf(estadoStr.toUpperCase());
                model.addAttribute("estadoFiltro", estadoStr);
            } catch (IllegalArgumentException e) {
                estado = null; // ignore invalid
            }
        }

        org.springframework.data.domain.Page<Reserva> pageRes = servicioReserva.paginarReservasPorCliente(cliente, estado, page, size);

        model.addAttribute("reservas", pageRes.getContent());
        model.addAttribute("paginaActual", pageRes.getNumber());
        model.addAttribute("totalPaginas", pageRes.getTotalPages());
        model.addAttribute("pageSize", pageRes.getSize());

        // Estadísticas (no paginadas)
        long totalReservas = repositorioReserva.countByCliente(cliente);
        long reservasConfirmadas = repositorioReserva.countByClienteAndEstado(cliente, EstadoReserva.CONFIRMADA);
        long reservasPendientes = repositorioReserva.countByClienteAndEstado(cliente, EstadoReserva.PENDIENTE);

        model.addAttribute("totalReservas", totalReservas);
        model.addAttribute("reservasConfirmadas", reservasConfirmadas);
        model.addAttribute("reservasPendientes", reservasPendientes);
        model.addAttribute("estadosDisponibles", EstadoReserva.values());
    }
    
    // ==================== CANCELACIÓN DE RESERVAS ====================
    
    /**
     * Muestra el modal de confirmación de cancelación (GET).
     * Valida que se pueda cancelar según tiempo límite y políticas.
     */
        @GetMapping("/{idOrCodigo}/cancelar")
        public String mostrarCancelacion(
            @PathVariable("idOrCodigo") String idOrCodigo,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Obtener email del usuario
            String email;
            if (authentication.getPrincipal() instanceof OAuth2User) {
                OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
                email = oauth2User.getAttribute("email");
            } else {
                email = authentication.getName();
            }
            
            // Intentar buscar cliente; si no existe, comprobar si es administrador del complejo
            Cliente cliente = repositorioCliente.findByEmail(email).orElse(null);

            // Obtener reserva (acepta id numérico o codigoReserva)
            Reserva reserva = obtenerReservaPorIdOrCodigo(idOrCodigo)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

            boolean esAdminComplejo = false;

            if (cliente != null) {
                // Validar pertenencia del cliente
                if (!reserva.getCliente().getId().equals(cliente.getId())) {
                    cliente = null; // no es el cliente propietario
                }
            }

            if (cliente == null) {
                // Comprobar si el usuario autenticado es el administrador del complejo asociado
                if (reserva.getDetalles() == null || reserva.getDetalles().isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", "Reserva sin detalles asociados");
                    return "redirect:/reservas/mis-reservas";
                }

                DetalleReserva primerDetalle = reserva.getDetalles().get(0);
                EspacioReservable espacio = primerDetalle.getEspacioReservable();
                Long complejoId = espacio.getComplejoDeportivo().getId_complejo();

                Optional<ComplejoDeportivo> complejoOpt = servicioComplejo.obtenerPorId(complejoId);
                if (complejoOpt.isPresent() && complejoOpt.get().getAdministradorComplejo() != null
                        && email != null && email.equals(complejoOpt.get().getAdministradorComplejo().getEmail())) {
                    esAdminComplejo = true;
                } else {
                    redirectAttributes.addFlashAttribute("error", "No tienes permiso para cancelar esta reserva");
                    return "redirect:/reservas/mis-reservas";
                }
            }

            // Validar estado general
            if (reserva.estaCancelada()) {
                redirectAttributes.addFlashAttribute("error", "Esta reserva ya ha sido cancelada");
                return esAdminComplejo ? "redirect:/admin-complejo/reservas/" + reserva.getDetalles().get(0).getEspacioReservable().getComplejoDeportivo().getId_complejo() : "redirect:/reservas/mis-reservas";
            }

            if (reserva.getEstado() == EstadoReserva.FINALIZADA) {
                redirectAttributes.addFlashAttribute("error", "No se puede cancelar una reserva finalizada");
                return esAdminComplejo ? "redirect:/admin-complejo/reservas/" + reserva.getDetalles().get(0).getEspacioReservable().getComplejoDeportivo().getId_complejo() : "redirect:/reservas/mis-reservas";
            }

            // Si es cliente, mostramos la validación de tiempo límite como antes
            if (!esAdminComplejo) {
                DetalleReserva primerDetalle = reserva.getDetalles().get(0);
                EspacioReservable espacio = primerDetalle.getEspacioReservable();
                PoliticaCancelacion politica = espacio.getPoliticaCancelacion();

                LocalDateTime fechaHoraReserva = LocalDateTime.of(
                    primerDetalle.getFechaReserva(),
                    primerDetalle.getHoraInicio()
                );
                LocalDateTime ahora = LocalDateTime.now();
                long horasRestantes = java.time.Duration.between(ahora, fechaHoraReserva).toHours();

                model.addAttribute("reserva", reserva);
                model.addAttribute("horasRestantes", horasRestantes);
                model.addAttribute("politica", politica);
                model.addAttribute("esAdmin", false);

                return "reservas/cancelar-reserva";
            } else {
                // Si es administrador, no aplicamos la validación de tiempo; mostramos vista con flag de admin
                model.addAttribute("reserva", reserva);
                model.addAttribute("esAdmin", true);
                return "reservas/cancelar-reserva";
            }
            
        } catch (Exception e) {
            logger.error("Error al mostrar cancelación: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/reservas/mis-reservas";
        }
    }
    
    /**
     * Procesa la cancelación de una reserva (POST).
     */
        @PostMapping("/{idOrCodigo}/cancelar")
        public String cancelarReserva(
            @PathVariable("idOrCodigo") String idOrCodigo,
            @RequestParam(value = "motivo", required = false) String motivo,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Obtener email del usuario
            String email;
            if (authentication.getPrincipal() instanceof OAuth2User) {
                OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
                email = oauth2User.getAttribute("email");
            } else {
                email = authentication.getName();
            }
            
            // Intentar buscar cliente; si no existe o no es propietario, comprobar si es admin del complejo
            Cliente cliente = repositorioCliente.findByEmail(email).orElse(null);
            Reserva reserva = obtenerReservaPorIdOrCodigo(idOrCodigo)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada"));

            boolean esAdminComplejo = false;

            if (cliente != null && reserva.getCliente() != null && reserva.getCliente().getId().equals(cliente.getId())) {
                // propietario -> proceder como cliente
            } else {
                // verificar admin
                if (reserva.getDetalles() == null || reserva.getDetalles().isEmpty()) {
                    throw new RuntimeException("Reserva sin detalles asociados");
                }
                DetalleReserva primerDetalle = reserva.getDetalles().get(0);
                EspacioReservable espacio = primerDetalle.getEspacioReservable();
                Long complejoId = espacio.getComplejoDeportivo().getId_complejo();

                Optional<ComplejoDeportivo> complejoOpt = servicioComplejo.obtenerPorId(complejoId);
                if (complejoOpt.isPresent() && complejoOpt.get().getAdministradorComplejo() != null
                        && email != null && email.equals(complejoOpt.get().getAdministradorComplejo().getEmail())) {
                    esAdminComplejo = true;
                } else {
                    throw new RuntimeException("Debe tener un perfil de cliente o ser administrador del complejo para cancelar esta reserva");
                }
            }

            if (motivo == null || motivo.trim().isEmpty()) {
                motivo = "Cancelada";
            }

            if (esAdminComplejo) {
                // El administrador puede cancelar sin respetar el tiempo límite
                servicioReserva.cancelarReservaPorAdmin(reserva.getId(), motivo);
                redirectAttributes.addFlashAttribute("mensaje", "Reserva cancelada exitosamente");
                // Redirigir al listado del admin para el complejo correspondiente
                DetalleReserva primerDetalle = reserva.getDetalles().get(0);
                Long complejoId = primerDetalle.getEspacioReservable().getComplejoDeportivo().getId_complejo();
                return "redirect:/admin-complejo/reservas/" + complejoId;
            } else {
                // Cancelación por cliente (aplicar reglas de política)
                ServicioReserva.ResultadoCancelacion resultado = servicioReserva.cancelarReserva(
                    reserva.getId(), cliente, motivo
                );

                if (!resultado.isPuedeSerCancelada()) {
                    redirectAttributes.addFlashAttribute("error", resultado.getMensaje());
                    return "redirect:/reservas/mis-reservas";
                }

                String mensajeExito = "Reserva cancelada exitosamente";
                if (resultado.getPorcentajeDevolucion() != null && resultado.getPorcentajeDevolucion() < 100) {
                    mensajeExito += String.format(". Se devolverá el %.0f%% del monto pagado",
                        resultado.getPorcentajeDevolucion());
                }

                redirectAttributes.addFlashAttribute("mensaje", mensajeExito);
                logger.info("Reserva {} cancelada exitosamente por cliente {}", reserva.getId(), reserva.getCliente() != null ? reserva.getCliente().getId() : "-" );
                return "redirect:/reservas/mis-reservas";
            }
            
        } catch (Exception e) {
            logger.error("Error al cancelar reserva {}: {}", idOrCodigo, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/reservas/mis-reservas";
        }
    }

}
