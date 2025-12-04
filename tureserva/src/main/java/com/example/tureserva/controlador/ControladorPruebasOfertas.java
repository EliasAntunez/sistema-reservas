package com.example.tureserva.controlador;

import com.example.tureserva.servicio.ServicioAvisos;
import com.example.tureserva.servicio.ServicioOfertas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controlador temporal para probar manualmente el módulo de Ofertas Flash.
 * 
 * IMPORTANTE: Este controlador es solo para desarrollo/testing.
 * Eliminar o deshabilitar en producción.
 * 
 * Endpoints disponibles:
 * - GET  /test/ofertas/dashboard : Panel de control de pruebas
 * - POST /test/ofertas/avisos    : Ejecutar proceso de avisos manualmente
 * - POST /test/ofertas/expirar   : Procesar ofertas expiradas manualmente
 */
@Controller
@RequestMapping("/test/ofertas")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN_COMPLEJO')")
public class ControladorPruebasOfertas {

    private static final Logger logger = LoggerFactory.getLogger(ControladorPruebasOfertas.class);
    
    private final ServicioAvisos servicioAvisos;
    private final ServicioOfertas servicioOfertas;

    public ControladorPruebasOfertas(ServicioAvisos servicioAvisos, ServicioOfertas servicioOfertas) {
        this.servicioAvisos = servicioAvisos;
        this.servicioOfertas = servicioOfertas;
    }

    /**
     * Dashboard de pruebas para el módulo de Ofertas Flash.
     * Muestra controles para ejecutar procesos manualmente.
     */
    @GetMapping("/dashboard")
    public String mostrarDashboard(Model model) {
        try {
            // Obtener ofertas activas
            var ofertasActivas = servicioOfertas.listarOfertasActivas();
            
            model.addAttribute("ofertasActivas", ofertasActivas);
            model.addAttribute("cantidadOfertas", ofertasActivas.size());
            
            return "test/ofertas-dashboard";
            
        } catch (Exception e) {
            logger.error("Error al cargar dashboard de pruebas: {}", e.getMessage(), e);
            model.addAttribute("error", "Error al cargar dashboard: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Ejecuta manualmente el proceso de avisos de recupero.
     * Busca reservas que estén cerca de perder su seña y envía emails.
     */
    @PostMapping("/avisos")
    public String ejecutarAvisos(RedirectAttributes redirectAttributes) {
        try {
            logger.info("🧪 Ejecutando proceso de avisos manualmente...");
            
            int avisosEnviados = servicioAvisos.enviarAvisosManuales();
            
            redirectAttributes.addFlashAttribute("success", 
                String.format("✅ Proceso completado. Se enviaron %d avisos.", avisosEnviados));
            
            logger.info("✅ Proceso manual completado: {} avisos enviados", avisosEnviados);
            
        } catch (Exception e) {
            logger.error("❌ Error ejecutando proceso de avisos: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", 
                "Error al ejecutar proceso de avisos: " + e.getMessage());
        }
        
        return "redirect:/test/ofertas/dashboard";
    }

    /**
     * Procesa manualmente las ofertas expiradas.
     * Marca como EXPIRADA las ofertas que ya pasaron su fecha de vencimiento.
     */
    @PostMapping("/expirar")
    public String procesarExpiradas(RedirectAttributes redirectAttributes) {
        try {
            logger.info("🧪 Procesando ofertas expiradas manualmente...");
            
            int procesadas = servicioOfertas.procesarOfertasExpiradas();
            
            redirectAttributes.addFlashAttribute("success", 
                String.format("✅ Se procesaron %d ofertas expiradas.", procesadas));
            
            logger.info("✅ Proceso manual completado: {} ofertas expiradas", procesadas);
            
        } catch (Exception e) {
            logger.error("❌ Error procesando ofertas expiradas: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", 
                "Error al procesar ofertas expiradas: " + e.getMessage());
        }
        
        return "redirect:/test/ofertas/dashboard";
    }

    /**
     * Simula una cancelación tardía para generar una oferta flash de prueba.
     * 
     * @param reservaId ID de la reserva a cancelar
     */
    @PostMapping("/simular-cancelacion/{reservaId}")
    public String simularCancelacion(
            @PathVariable Long reservaId,
            RedirectAttributes redirectAttributes) {
        
        try {
            logger.info("🧪 Simulando cancelación tardía de reserva {}...", reservaId);
            
            // Aquí deberías llamar al servicio de reservas para cancelar
            // y que automáticamente genere la oferta flash
            
            redirectAttributes.addFlashAttribute("info", 
                "Para simular una cancelación tardía, usa el endpoint de cancelación normal. " +
                "Si la cancelación es fuera de plazo, se generará automáticamente una Oferta Flash.");
            
        } catch (Exception e) {
            logger.error("❌ Error simulando cancelación: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", 
                "Error al simular cancelación: " + e.getMessage());
        }
        
        return "redirect:/test/ofertas/dashboard";
    }
}
