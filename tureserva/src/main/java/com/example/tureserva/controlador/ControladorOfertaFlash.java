package com.example.tureserva.controlador;

import com.example.tureserva.modelo.OfertaFlash;
import com.example.tureserva.modelo.Reserva;
import com.example.tureserva.servicio.ServicioOfertas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controlador REST para gestionar las Ofertas Flash.
 * Permite visualizar ofertas disponibles y reclamarlas.
 */
@Controller
@RequestMapping("/ofertas")
public class ControladorOfertaFlash {

    private static final Logger logger = LoggerFactory.getLogger(ControladorOfertaFlash.class);
    
    private final ServicioOfertas servicioOfertas;
    private final com.example.tureserva.repositorio.RepositorioReserva repositorioReserva;

    public ControladorOfertaFlash(
            ServicioOfertas servicioOfertas,
            com.example.tureserva.repositorio.RepositorioReserva repositorioReserva) {
        this.servicioOfertas = servicioOfertas;
        this.repositorioReserva = repositorioReserva;
    }

    /**
     * Muestra el detalle de una Oferta Flash por su token.
     * GET /ofertas/{token}
     */
    @GetMapping("/{token}")
    public String verDetalleOferta(
            @PathVariable String token,
            Model model,
            Authentication authentication) {
        
        try {
            OfertaFlash oferta = servicioOfertas.buscarPorToken(token);
            
            // Validar que la oferta exista
            if (oferta == null) {
                throw new IllegalArgumentException("Oferta no encontrada");
            }
            
            // Verificar que la oferta esté disponible
            if (!oferta.estaDisponible()) {
                model.addAttribute("error", "Esta oferta ya no está disponible.");
                model.addAttribute("oferta", oferta);
                return "ofertas/oferta-no-disponible";
            }
            
            // Agregar datos al modelo
            model.addAttribute("oferta", oferta);
            
            // Pasar datos explícitos para evitar problemas de lazy loading
            Reserva reservaOriginal = oferta.getReservaOriginal();
            var detalles = reservaOriginal.getDetalles();
            
            // Obtener datos del primer detalle
            if (!detalles.isEmpty()) {
                var primerDetalle = detalles.get(0);
                model.addAttribute("nombreComplejo", primerDetalle.getEspacioReservable().getComplejoDeportivo().getNombre_complejo());
                model.addAttribute("fecha", primerDetalle.getFechaReserva());
                model.addAttribute("horaInicio", primerDetalle.getHoraInicio());
            } else {
                // Fallback si no hay detalles
                model.addAttribute("nombreComplejo", "Complejo");
                model.addAttribute("fecha", reservaOriginal.getFechaReserva());
                model.addAttribute("horaInicio", null);
            }
            
            model.addAttribute("detalles", detalles);
            model.addAttribute("montoTotal", reservaOriginal.getMontoTotal());
            model.addAttribute("precioConDescuento", reservaOriginal.getMontoTotal().subtract(oferta.getMontoDescuentoOferta()));
            
            // Si hay usuario autenticado, verificar que no sea el cliente original
            if (authentication != null && authentication.isAuthenticated()) {
                String emailUsuario = null;
                if (authentication.getPrincipal() instanceof OAuth2User) {
                    OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
                    emailUsuario = oauth2User.getAttribute("email");
                } else if (authentication.getPrincipal() instanceof UserDetails) {
                    UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                    emailUsuario = userDetails.getUsername();
                } else {
                    emailUsuario = authentication.getName();
                }
                
                boolean esClienteOriginal = emailUsuario != null && oferta.getClienteOriginal().getEmail().equals(emailUsuario);
                model.addAttribute("esClienteOriginal", esClienteOriginal);
            } else {
                model.addAttribute("esClienteOriginal", false);
            }
            
            return "ofertas/oferta-detalle";
            
        } catch (Exception e) {
            logger.error("Error al cargar oferta {}: {}", token, e.getMessage());
            model.addAttribute("error", "Error al cargar la oferta: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Procesa el reclamo de una Oferta Flash.
     * POST /ofertas/{token}/reclamar
     */
    @PostMapping("/{token}/reclamar")
    public String reclamarOferta(
            @PathVariable String token,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        logger.info("=== RECLAMO OFERTA INICIADO === Token: {}, Authentication: {}", 
            token, authentication != null ? authentication.getName() : "NULL");
        
        try {
            // Validar que el usuario esté autenticado
            if (authentication == null || !authentication.isAuthenticated()) {
                logger.warn("Usuario no autenticado intentando reclamar oferta {}", token);
                redirectAttributes.addFlashAttribute("error", 
                        "Debes iniciar sesión para reclamar una oferta.");
                return "redirect:/usuarios/login?redirect=/ofertas/" + token;
            }
            
            // Obtener email según el tipo de autenticación (OAuth2 vs tradicional)
            String emailCliente;
            if (authentication.getPrincipal() instanceof OAuth2User) {
                OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
                emailCliente = oauth2User.getAttribute("email");
                logger.info("Usuario OAuth2 detectado. Email: {}", emailCliente);
            } else if (authentication.getPrincipal() instanceof UserDetails) {
                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                emailCliente = userDetails.getUsername();
                logger.info("Usuario tradicional detectado. Email: {}", emailCliente);
            } else {
                emailCliente = authentication.getName();
                logger.info("Tipo de autenticación desconocido. Usando getName(): {}", emailCliente);
            }
            
            if (emailCliente == null || emailCliente.isEmpty()) {
                logger.error("No se pudo obtener el email del usuario autenticado");
                redirectAttributes.addFlashAttribute("error", 
                        "Error al obtener tu información de usuario. Por favor, intenta nuevamente.");
                return "redirect:/ofertas/" + token;
            }
            
            // Reclamar la oferta (ServicioOfertas valida que no sea el cliente original)
            // Nota: El servicio busca el cliente por email internamente
            Reserva nuevaReserva = servicioOfertas.reclamarOferta(token, emailCliente);
            
            // Redirigir a página de confirmación
            redirectAttributes.addFlashAttribute("success", 
                    "¡Oferta reclamada exitosamente! Tu nueva reserva ha sido confirmada.");
            redirectAttributes.addFlashAttribute("reservaId", nuevaReserva.getId());
            
            return "redirect:/ofertas/confirmacion/" + nuevaReserva.getId();
            
        } catch (IllegalArgumentException e) {
            logger.warn("Error de validación al reclamar oferta {}: {}", token, e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/ofertas/" + token;
            
        } catch (IllegalStateException e) {
            logger.error("Error de estado al reclamar oferta {}: {}", token, e.getMessage());
            redirectAttributes.addFlashAttribute("error", 
                    "La oferta ya no está disponible. Puede haber sido reclamada por otro usuario.");
            return "redirect:/ofertas/" + token;
            
        } catch (Exception e) {
            logger.error("Error inesperado al reclamar oferta {}: {}", token, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", 
                    "Ocurrió un error al procesar tu solicitud. Por favor, intenta nuevamente.");
            return "redirect:/ofertas/" + token;
        }
    }

    /**
     * Muestra la página de confirmación después de reclamar una oferta.
     * GET /ofertas/confirmacion/{reservaId}
     */
    @GetMapping("/confirmacion/{reservaId}")
    public String confirmarReclamoOferta(
            @PathVariable Long reservaId,
            Model model,
            Authentication authentication) {
        
        try {
            // Cargar la reserva
            Reserva reserva = repositorioReserva.findById(reservaId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));
            
            // Buscar la oferta asociada a esta reserva
            OfertaFlash ofertaAsociada = servicioOfertas.buscarPorReservaNueva(reserva);
            
            if (ofertaAsociada == null) {
                logger.warn("No se encontró la oferta asociada a la reserva {}", reservaId);
                model.addAttribute("montoDescuento", java.math.BigDecimal.ZERO);
            } else {
                model.addAttribute("montoDescuento", ofertaAsociada.getMontoDescuentoOferta());
            }
            
            model.addAttribute("reservaId", reservaId);
            model.addAttribute("mensaje", 
                    "Tu reserva ha sido confirmada. Recibirás un correo con los detalles.");
            
            return "ofertas/oferta-confirmacion";
            
        } catch (Exception e) {
            logger.error("Error al mostrar confirmación de reserva {}: {}", reservaId, e.getMessage());
            model.addAttribute("error", "Error al cargar la confirmación.");
            return "error";
        }
    }

    /**
     * Lista todas las ofertas disponibles para un complejo específico.
     * GET /ofertas/complejo/{complejoId}
     */
    @GetMapping("/complejo/{complejoId}")
    public String listarOfertasPorComplejo(
            @PathVariable Long complejoId,
            Model model) {
        
        try {
            var ofertas = servicioOfertas.listarOfertasPorComplejo(complejoId);
            model.addAttribute("ofertas", ofertas);
            model.addAttribute("complejoId", complejoId);
            
            return "ofertas/ofertas-complejo";
            
        } catch (Exception e) {
            logger.error("Error al listar ofertas del complejo {}: {}", complejoId, e.getMessage());
            model.addAttribute("error", "Error al cargar las ofertas.");
            return "error";
        }
    }

    /**
     * Lista todas las ofertas activas en el sistema.
     * GET /ofertas
     */
    @GetMapping
    public String listarOfertasActivas(Model model) {
        
        try {
            var ofertas = servicioOfertas.listarOfertasActivas();
            model.addAttribute("ofertas", ofertas);
            
            return "ofertas/ofertas-lista";
            
        } catch (Exception e) {
            logger.error("Error al listar ofertas activas: {}", e.getMessage());
            model.addAttribute("error", "Error al cargar las ofertas.");
            return "error";
        }
    }
}
