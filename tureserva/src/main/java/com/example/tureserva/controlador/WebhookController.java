package com.example.tureserva.controlador;

import com.example.tureserva.modelo.AdministradorComplejo;
import com.example.tureserva.modelo.Pago;
import com.example.tureserva.modelo.Reserva;
import com.example.tureserva.modelo.Cliente;
import com.example.tureserva.modelo.DatosReservaTemp;
import com.example.tureserva.repositorio.RepositorioAdministradorComplejo;
import com.example.tureserva.repositorio.RepositorioPago;
import com.example.tureserva.repositorio.RepositorioCliente;
import com.example.tureserva.repositorio.RepositorioReserva;
import com.example.tureserva.servicio.MercadoPagoClient;
import com.example.tureserva.servicio.ServicioReserva;
import com.example.tureserva.servicio.ServicioPago;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final RepositorioAdministradorComplejo repositorioAdministradorComplejo;
    private final RepositorioPago repositorioPago;
    private final RepositorioCliente repositorioCliente;
    private final RepositorioReserva repositorioReserva;
    private final MercadoPagoClient mpClient;
    private final ServicioReserva servicioReserva;
    private final ServicioPago servicioPago;
    private final ObjectMapper objectMapper;
    private final com.example.tureserva.repositorio.RepositorioBloqueoTemporal repositorioBloqueoTemporal;

    public WebhookController(RepositorioAdministradorComplejo repositorioAdministradorComplejo,
                             RepositorioPago repositorioPago,
                             RepositorioCliente repositorioCliente,
                             RepositorioReserva repositorioReserva,
                             MercadoPagoClient mpClient,
                             ServicioReserva servicioReserva,
                             ServicioPago servicioPago,
                             ObjectMapper objectMapper,
                             com.example.tureserva.repositorio.RepositorioBloqueoTemporal repositorioBloqueoTemporal) {
        this.repositorioAdministradorComplejo = repositorioAdministradorComplejo;
        this.repositorioPago = repositorioPago;
        this.repositorioCliente = repositorioCliente;
        this.repositorioReserva = repositorioReserva;
        this.mpClient = mpClient;
        this.servicioReserva = servicioReserva;
        this.servicioPago = servicioPago;
        this.objectMapper = objectMapper;
        this.repositorioBloqueoTemporal = repositorioBloqueoTemporal;
    }

    @PostMapping("/mercadopago")
    @Transactional
    public ResponseEntity<String> handleMercadoPagoWebhook(@RequestBody Map<String, Object> payload) {
        try {
            // Extraer posible payment id desde varios formatos
            String paymentId = null;
            Object data = payload.get("data");
            if (data instanceof Map) {
                Object id = ((Map<?,?>)data).get("id");
                if (id != null) paymentId = id.toString();
            }
            if (paymentId == null && payload.get("id") != null) paymentId = payload.get("id").toString();

            if (paymentId == null) {
                log.warn("Webhook MP recibido sin id: {}", payload);
                return ResponseEntity.badRequest().body("missing id");
            }

            // Intentar localizar el pago probando los access tokens de administradores activos
            List<AdministradorComplejo> admins = repositorioAdministradorComplejo.findByActivoTrue();
            for (AdministradorComplejo admin : admins) {
                String token = admin.getMpAccessToken();
                if (token == null || token.isBlank()) continue;
                try {
                    MercadoPagoClient.PaymentResult pr = mpClient.getPayment(token, paymentId);
                    if (pr == null) continue;

                    // Buscar pago por preferenceId o por externalReference
                    Optional<Pago> pagoOpt = Optional.empty();
                    if (pr.externalReference != null && !pr.externalReference.isBlank()) {
                        // externalReference puede ser el id del Pago
                        try {
                            Long pagoId = Long.valueOf(pr.externalReference);
                            pagoOpt = repositorioPago.findById(pagoId);
                        } catch (NumberFormatException nfe) {
                            // no es id, seguir
                        }
                    }

                    if (pagoOpt.isEmpty() && pr.externalReference == null) {
                        // intentar por preference id: algunos pagos exponen preference en metadata
                        pagoOpt = repositorioPago.findByPreferenceId(pr.externalReference);
                    }

                    if (pagoOpt.isEmpty() && pr.externalReference != null) {
                        pagoOpt = repositorioPago.findByPreferenceId(pr.externalReference);
                    }

                    if (pagoOpt.isEmpty() && pr.externalReference == null) {
                        // fallback: intentar buscar por transaccionId
                        pagoOpt = repositorioPago.findByTransaccionId(pr.id);
                    }

                    if (pagoOpt.isPresent()) {
                        Pago pago = pagoOpt.get();
                        String status = pr.status != null ? pr.status.toUpperCase() : "";
                        
                        // Actualizar estado del pago
                        if ("approved".equalsIgnoreCase(pr.status) || "approved".equalsIgnoreCase(status)) {
                            pago.setEstadoPago("PAGADO");
                        } else {
                            pago.setEstadoPago(pr.status != null ? pr.status.toUpperCase() : "UNKNOWN");
                        }

                        pago.setTransaccionId(pr.id);
                        if (pr.dateApproved != null) {
                            pago.setFechaPago(pr.dateApproved.toLocalDateTime());
                        }
                        repositorioPago.save(pago);

                        log.info("Pago {} actualizado por webhook MP: status={}, pagoId={}", pr.id, pr.status, pago.getId());

                        // Si el pago fue aprobado Y aún no tiene reserva asociada, crear la reserva automáticamente
                        if ("PAGADO".equals(pago.getEstadoPago()) && pago.getReserva() == null) {
                            try {
                                crearReservaDesdeWebhook(pago);
                                log.info("Reserva creada automáticamente para pago {}", pago.getId());
                            } catch (Exception ex) {
                                log.error("Error al crear reserva automáticamente para pago {}: {}", pago.getId(), ex.getMessage(), ex);
                                // No fallar el webhook, el pago ya está marcado como PAGADO
                            }
                        }

                        return ResponseEntity.ok("processed");
                    }

                } catch (Exception e) {
                    log.debug("Token admin {} no válido para paymentId {}: {}", admin.getId(), paymentId, e.getMessage());
                    // probar siguiente admin
                }
            }

            log.warn("No se encontró Pago asociado al notification id={} payload={}", paymentId, payload);
            return ResponseEntity.ok("not_found");

        } catch (Exception e) {
            log.error("Error procesando webhook MP: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("error");
        }
    }

    /**
     * Crea automáticamente la reserva cuando el pago es aprobado.
     * Lee los datos de reserva del metadata del pago.
     */
    private void crearReservaDesdeWebhook(Pago pago) throws Exception {
        // Extraer metadata del pago
        String metadataJson = pago.getMetadata();
        if (metadataJson == null || metadataJson.isBlank()) {
            throw new IllegalStateException("Pago sin metadata, no se puede crear reserva");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = objectMapper.readValue(metadataJson, Map.class);
        String clienteEmail = (String) metadata.get("clienteEmail");
        Object datosReservaObj = metadata.get("datosReserva");
        Object serviciosPorItemObj = metadata.get("serviciosPorItem");
        
        if (clienteEmail == null || clienteEmail.isBlank()) {
            throw new IllegalStateException("No se encontró clienteEmail en metadata del pago");
        }

        if (datosReservaObj == null) {
            throw new IllegalStateException("No se encontraron datosReserva en metadata del pago");
        }

        // Buscar cliente por email
        Optional<Cliente> clienteOpt = repositorioCliente.findByEmail(clienteEmail);
        if (clienteOpt.isEmpty()) {
            throw new IllegalStateException("No se encontró cliente con email: " + clienteEmail);
        }
        Cliente cliente = clienteOpt.get();

        // Deserializar datosReserva
        DatosReservaTemp datosReserva = objectMapper.convertValue(datosReservaObj, DatosReservaTemp.class);
        
        // Deserializar y convertir serviciosPorItem de Map<String, Map<String, Integer>> a Map<Integer, Map<Long, Integer>>
        Map<Integer, Map<Long, Integer>> serviciosPorItem = new java.util.HashMap<>();
        if (serviciosPorItemObj != null) {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> rawServicios = (Map<String, Map<String, Object>>) serviciosPorItemObj;
            for (Map.Entry<String, Map<String, Object>> entry : rawServicios.entrySet()) {
                Integer indice = Integer.valueOf(entry.getKey());
                Map<Long, Integer> serviciosDelItem = new java.util.HashMap<>();
                for (Map.Entry<String, Object> svcEntry : entry.getValue().entrySet()) {
                    Long servicioId = Long.valueOf(svcEntry.getKey());
                    Integer cantidad = ((Number) svcEntry.getValue()).intValue();
                    serviciosDelItem.put(servicioId, cantidad);
                }
                serviciosPorItem.put(indice, serviciosDelItem);
            }
        }

        // Crear la reserva
        Reserva reserva = servicioReserva.crearReservaDesdeDatosTemp(cliente, datosReserva, serviciosPorItem);
        
        // Setear el monto de la seña pagada y recalcular el monto restante
        reserva.setMontoSenia(pago.getMonto());
        reserva.calcularMontoRestante(); // Calcular el monto restante (total - seña)
        
        // Guardar la reserva actualizada con la seña
        repositorioReserva.save(reserva);
        
        // Asociar pago a reserva
        servicioPago.asociarPagoAReserva(pago.getId(), reserva);
        
        // ELIMINAR BLOQUEOS TEMPORALES asociados a este pago
        // La reserva se creó exitosamente, por lo que ya no se necesitan los bloqueos
        int bloqueosEliminados = repositorioBloqueoTemporal.eliminarPorPagoId(pago.getId());
        log.info("Eliminados {} bloqueos temporales para el pago {}", bloqueosEliminados, pago.getId());
        
        log.info("Reserva {} creada automáticamente desde webhook para pago {}. Total: ${}, Seña: ${}, Restante: ${}", 
            reserva.getId(), pago.getId(), reserva.getMontoTotal(), reserva.getMontoSenia(), reserva.getMontoRestante());
    }
}
