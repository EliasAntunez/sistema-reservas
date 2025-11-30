package com.example.tureserva.controlador;

import com.example.tureserva.servicio.ServicioPago;
import com.example.tureserva.servicio.ServicioPago.PreferenciaResponse;
import com.example.tureserva.servicio.dto.InitSeniaRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pagos")
public class ControladorPago {

    private final ServicioPago servicioPago;

    public ControladorPago(ServicioPago servicioPago) {
        this.servicioPago = servicioPago;
    }

    @PostMapping("/init-senia")
    public ResponseEntity<?> iniciarSenia(@RequestBody InitSeniaRequest req, HttpSession session) {
        try {
            PreferenciaResponse resp = servicioPago.iniciarSenia(req, session);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            org.slf4j.LoggerFactory.getLogger(ControladorPago.class).warn("Error de validación: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (IllegalStateException ise) {
            org.slf4j.LoggerFactory.getLogger(ControladorPago.class).warn("Error de estado: {}", ise.getMessage());
            return ResponseEntity.status(409).body(ise.getMessage());
        } catch (Exception ex) {
            // Log full stacktrace
            org.slf4j.LoggerFactory.getLogger(ControladorPago.class).error("Error inesperado en iniciarSenia", ex);
            return ResponseEntity.status(500).body("Error interno del servidor: " + ex.getMessage());
        }
    }

    @GetMapping("/check/{pagoId}")
    public ResponseEntity<?> checkPago(@PathVariable Long pagoId) {
        java.util.Optional<com.example.tureserva.modelo.Pago> p = servicioPago.obtenerPagoPorId(pagoId);
        if (p.isEmpty()) return ResponseEntity.notFound().build();
        com.example.tureserva.modelo.Pago pago = p.get();
        java.util.Map<String,Object> resp = new java.util.HashMap<>();
        resp.put("pagoId", pago.getId());
        resp.put("estado", pago.getEstadoPago());
        resp.put("initPoint", pago.getInitPoint());
        resp.put("preferenceId", pago.getPreferenceId());
        return ResponseEntity.ok(resp);
    }
}
