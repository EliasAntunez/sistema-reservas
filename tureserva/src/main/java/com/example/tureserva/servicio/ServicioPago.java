package com.example.tureserva.servicio;

import com.example.tureserva.modelo.AdministradorComplejo;
import com.example.tureserva.modelo.Pago;
import com.example.tureserva.modelo.enums.TipoPago;
import com.example.tureserva.repositorio.RepositorioComplejoDeportivo;
import com.example.tureserva.repositorio.RepositorioPago;
import com.example.tureserva.servicio.dto.InitSeniaRequest;
import com.example.tureserva.modelo.DatosReservaTemp;
import jakarta.servlet.http.HttpSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferencePayerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import com.example.tureserva.modelo.Reserva;
import com.example.tureserva.modelo.enums.MetodoPago;

@Service
public class ServicioPago {
    private static final Logger log = LoggerFactory.getLogger(ServicioPago.class);

    // Clase auxiliar para agrupar servicios por franja horaria
    private static class FranjaKey {
        private final java.time.LocalDate fecha;
        private final java.time.LocalTime inicio;
        private final java.time.LocalTime fin;

        FranjaKey(java.time.LocalDate fecha, java.time.LocalTime inicio, java.time.LocalTime fin) {
            this.fecha = fecha;
            this.inicio = inicio;
            this.fin = fin;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FranjaKey that = (FranjaKey) o;
            return java.util.Objects.equals(fecha, that.fecha) && 
                   java.util.Objects.equals(inicio, that.inicio) && 
                   java.util.Objects.equals(fin, that.fin);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(fecha, inicio, fin);
        }
    }

    private final RepositorioPago repositorioPago;
    private final RepositorioComplejoDeportivo repositorioComplejoDeportivo;
    private final MercadoPagoClient mpClient;
    private final ObjectMapper objectMapper;
    private final ServicioEspacioReservable servicioEspacioReservable;
    private final ServicioServicioAdicional servicioServicioAdicional;
    private final com.example.tureserva.repositorio.RepositorioDetalleServicioAdicional repositorioDetalleServicioAdicional;
    private final ServicioReserva servicioReserva;
    private final com.example.tureserva.repositorio.RepositorioBloqueoTemporal repositorioBloqueoTemporal;
    private final Environment env;

    public static class PreferenciaResponse {
        public Long pagoId;
        public String preferenceId;
        public String initPoint;
        public BigDecimal monto;

        public PreferenciaResponse(Long pagoId, String preferenceId, String initPoint, BigDecimal monto) {
            this.pagoId = pagoId;
            this.preferenceId = preferenceId;
            this.initPoint = initPoint;
            this.monto = monto;
        }
    }

    public ServicioPago(RepositorioPago repositorioPago,
                        RepositorioComplejoDeportivo repositorioComplejoDeportivo,
                        MercadoPagoClient mpClient,
                        ObjectMapper objectMapper,
                        ServicioEspacioReservable servicioEspacioReservable,
                        ServicioServicioAdicional servicioServicioAdicional,
                        com.example.tureserva.repositorio.RepositorioDetalleServicioAdicional repositorioDetalleServicioAdicional,
                        ServicioReserva servicioReserva,
                        com.example.tureserva.repositorio.RepositorioBloqueoTemporal repositorioBloqueoTemporal,
                        Environment env) {
        this.repositorioPago = repositorioPago;
        this.repositorioComplejoDeportivo = repositorioComplejoDeportivo;
        this.mpClient = mpClient;
        this.objectMapper = objectMapper;
        this.servicioEspacioReservable = servicioEspacioReservable;
        this.servicioServicioAdicional = servicioServicioAdicional;
        this.repositorioDetalleServicioAdicional = repositorioDetalleServicioAdicional;
        this.servicioReserva = servicioReserva;
        this.repositorioBloqueoTemporal = repositorioBloqueoTemporal;
        this.env = env;
    }

    @Transactional
    public PreferenciaResponse iniciarSenia(InitSeniaRequest req, HttpSession session) throws Exception {
        if (req == null) throw new IllegalArgumentException("request vacía");

        // Obtener DatosReservaTemp desde la sesión
        DatosReservaTemp datos = (DatosReservaTemp) session.getAttribute("datosReserva");
        if (datos == null || !datos.tieneEspacios()) {
            throw new IllegalArgumentException("No hay datos de reserva en sesión para calcular la seña");
        }

        // Obtener administrador por complejo (preferimos usar el complejo de la sesión)
        var complejo = repositorioComplejoDeportivo.findById(datos.getComplejoId())
            .orElseThrow(() -> new IllegalArgumentException("Complejo no encontrado"));

        AdministradorComplejo admin = complejo.getAdministradorComplejo();

        if (admin == null || admin.getMpAccessToken() == null || admin.getMpAccessToken().isBlank()) {
            throw new IllegalStateException("El administrador del complejo no tiene credenciales de Mercado Pago configuradas");
        }

        String accessToken = admin.getMpAccessToken();

        // Calcular seña solo sobre el precio de los ESPACIOS (sin servicios adicionales)
        // Cada espacio tiene su propia política de seña
        java.math.BigDecimal seniaEsperada = java.math.BigDecimal.ZERO;
        java.time.LocalDate hoy = java.time.LocalDate.now();
        
        if (datos.getItems() == null || datos.getItems().isEmpty()) {
            throw new IllegalStateException("No hay espacios en la reserva");
        }

        // VALIDAR DISPONIBILIDAD DE ESPACIOS (horarios) ANTES DE PERMITIR EL PAGO
        // Consulta directa a las reservas existentes para evitar problemas de lazy loading
        for (var item : datos.getItems()) {
            var espacioOpt = servicioEspacioReservable.obtenerPorId(item.getEspacioId());
            if (espacioOpt.isEmpty()) {
                throw new IllegalStateException("Espacio no encontrado: " + item.getEspacioId());
            }

            var espacio = espacioOpt.get();
            
            // Buscar reservas confirmadas que se superpongan con el horario solicitado
            java.util.List<com.example.tureserva.modelo.DetalleReserva> reservasSuperpuestas = 
                servicioReserva.buscarReservasEnRango(
                    espacio.getId(), 
                    datos.getFecha(), 
                    item.getHoraInicio(), 
                    item.getHoraFin()
                );
            
            if (!reservasSuperpuestas.isEmpty()) {
                throw new IllegalStateException(
                    String.format("El horario %s-%s ya no está disponible para el espacio '%s'. Por favor, selecciona otro horario.", 
                        item.getHoraInicio(), item.getHoraFin(), espacio.getNombre()));
            }
            
            // VALIDAR BLOQUEOS TEMPORALES (otros usuarios en proceso de pago)
            java.util.List<com.example.tureserva.modelo.BloqueoTemporal> bloqueosActivos = 
                repositorioBloqueoTemporal.findBloqueosActivosEnRango(
                    espacio.getId(),
                    datos.getFecha(),
                    item.getHoraInicio(),
                    item.getHoraFin(),
                    java.time.LocalDateTime.now()
                );
            
            if (!bloqueosActivos.isEmpty()) {
                throw new IllegalStateException(
                    String.format("El horario %s-%s está temporalmente bloqueado para el espacio '%s'. Otro usuario está completando el pago. Por favor, intenta nuevamente en unos minutos.", 
                        item.getHoraInicio(), item.getHoraFin(), espacio.getNombre()));
            }
        }

        // VALIDAR DISPONIBILIDAD DE SERVICIOS ADICIONALES ANTES DE PERMITIR EL PAGO
        // Mapear los servicios solicitados por franja para verificar disponibilidad
        Map<Long, Map<FranjaKey, Integer>> solicitadoPorServicio = new java.util.HashMap<>();
        
        if (req.getServiciosPorItem() != null && !req.getServiciosPorItem().isEmpty()) {
            for (int idx = 0; idx < datos.getItems().size(); idx++) {
                var item = datos.getItems().get(idx);
                Map<Long, Integer> serviciosDelItem = req.getServiciosPorItem().get(idx);
                
                if (serviciosDelItem != null && !serviciosDelItem.isEmpty()) {
                    for (Map.Entry<Long, Integer> e : serviciosDelItem.entrySet()) {
                        Long idServicio = e.getKey();
                        Integer cantidad = e.getValue();
                        if (cantidad == null || cantidad <= 0) continue;

                        // Verificar que el servicio existe
                        var servicioOpt = servicioServicioAdicional.obtenerPorId(idServicio);
                        if (servicioOpt.isEmpty()) {
                            throw new IllegalStateException("Servicio adicional no encontrado: " + idServicio);
                        }

                        var svc = servicioOpt.get();

                        // Validación de cantidad máxima si está definida
                        if (svc.getMaximoCantidad() != null && cantidad > svc.getMaximoCantidad()) {
                            throw new IllegalStateException("La cantidad solicitada para el servicio '" + svc.getNombre() + "' excede el máximo permitido: " + svc.getMaximoCantidad());
                        }

                        // Registrar en el mapa de solicitado por servicio+franja
                        FranjaKey fk = new FranjaKey(datos.getFecha(), item.getHoraInicio(), item.getHoraFin());
                        solicitadoPorServicio
                            .computeIfAbsent(idServicio, k -> new java.util.HashMap<>())
                            .merge(fk, cantidad, Integer::sum);
                    }
                }
            }

            // Validar disponibilidad de servicios por franja
            for (Map.Entry<Long, Map<FranjaKey, Integer>> entry : solicitadoPorServicio.entrySet()) {
                Long svcId = entry.getKey();
                var servicioOpt = servicioServicioAdicional.obtenerPorId(svcId);
                if (servicioOpt.isEmpty()) {
                    throw new IllegalStateException("Servicio adicional no encontrado: " + svcId);
                }
                
                var svc = servicioOpt.get();
                Integer capacidad = svc.getCapacidadTotal() != null ? svc.getCapacidadTotal() : svc.getMaximoCantidad();
                
                if (capacidad == null) {
                    // Sin límite global definido -> no validar
                    continue;
                }

                for (Map.Entry<FranjaKey, Integer> fe : entry.getValue().entrySet()) {
                    FranjaKey fk = fe.getKey();
                    Integer solicitado = fe.getValue() == null ? 0 : fe.getValue();

                    Integer existente = repositorioDetalleServicioAdicional.sumCantidadParaServicioEnFranja(
                        svcId, fk.fecha, fk.inicio, fk.fin);
                    existente = existente == null ? 0 : existente;

                    if (existente + solicitado > capacidad) {
                        throw new IllegalStateException("No hay suficiente disponibilidad para el servicio '" + svc.getNombre() + 
                            "' en la franja " + fk.inicio + "-" + fk.fin + 
                            ". Disponible: " + capacidad + ", ya reservado: " + existente + ", solicitado: " + solicitado);
                    }
                }
            }
        }

        // Calcular seña para cada espacio según su política individual
        for (var item : datos.getItems()) {
            var espacioOpt = servicioEspacioReservable.obtenerPorId(item.getEspacioId());
            if (espacioOpt.isEmpty()) {
                throw new IllegalStateException("Espacio no encontrado: " + item.getEspacioId());
            }

            var espacio = espacioOpt.get();
            var politica = espacio.getPoliticaSenia();
            
            // Verificar que el espacio tiene política de seña activa y vigente
            if (politica == null || politica.getActivo() == null || !politica.getActivo()) {
                throw new IllegalStateException("El espacio '" + espacio.getNombre() + "' no tiene política de seña activa");
            }
            
            if (hoy.isBefore(politica.getFechaInicioVigencia()) || hoy.isAfter(politica.getFechaFinVigencia())) {
                throw new IllegalStateException("La política de seña del espacio '" + espacio.getNombre() + "' no está vigente");
            }

            // Calcular el precio del espacio (sin servicios)
            java.math.BigDecimal precioEspacio = java.math.BigDecimal.valueOf(espacio.getPrecioPorHora())
                .multiply(java.math.BigDecimal.valueOf(item.getDuracionHoras()));

            // Calcular seña según política (monto fijo o porcentaje)
            java.math.BigDecimal seniaEspacio;
            if (politica.getMontoFijo() != null && politica.getMontoFijo() > 0) {
                seniaEspacio = java.math.BigDecimal.valueOf(politica.getMontoFijo());
            } else if (politica.getPorcentajeSenia() != null && politica.getPorcentajeSenia() > 0) {
                seniaEspacio = precioEspacio
                    .multiply(java.math.BigDecimal.valueOf(politica.getPorcentajeSenia()))
                    .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            } else {
                throw new IllegalStateException("La política de seña del espacio '" + espacio.getNombre() + "' no tiene monto fijo ni porcentaje configurado");
            }

            seniaEsperada = seniaEsperada.add(seniaEspacio);
        }

        if (seniaEsperada.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("El monto de seña calculado debe ser mayor a cero");
        }

        // crear pago pendiente
        Pago pago = new Pago();
        pago.setReserva(null);
        pago.setTipoPago(TipoPago.SENIA);
        pago.setMetodoPago(null);
        pago.setMonto(seniaEsperada);
        pago.setFechaPago(null);
        pago.setTransaccionId(null);
        pago.setPreferenceId(null);
        pago.setInitPoint(null);
        pago.setEstadoPago("PENDIENTE");
        pago.setExpiresAt(LocalDateTime.now().plusHours(6));

        Map<String,Object> meta = new HashMap<>();
        meta.put("clienteEmail", req.getClienteEmail());
        meta.put("complejoId", datos.getComplejoId());
        meta.put("referencia", req.getReferencia());
        // Guardar datos completos de la reserva para poder crearla desde el webhook
        meta.put("datosReserva", datos);
        meta.put("serviciosPorItem", req.getServiciosPorItem());
        pago.setMetadata(objectMapper.writeValueAsString(meta));

        repositorioPago.save(pago);
        
        // CREAR BLOQUEOS TEMPORALES para prevenir race conditions
        // Bloquear cada espacio/horario durante el proceso de pago (expiran en 10 minutos)
        java.util.List<com.example.tureserva.modelo.BloqueoTemporal> bloqueos = new java.util.ArrayList<>();
        for (var item : datos.getItems()) {
            var espacioOpt = servicioEspacioReservable.obtenerPorId(item.getEspacioId());
            if (espacioOpt.isPresent()) {
                com.example.tureserva.modelo.BloqueoTemporal bloqueo = new com.example.tureserva.modelo.BloqueoTemporal();
                bloqueo.setEspacio(espacioOpt.get());
                bloqueo.setFechaReserva(datos.getFecha());
                bloqueo.setHoraInicio(item.getHoraInicio());
                bloqueo.setHoraFin(item.getHoraFin());
                bloqueo.setClienteEmail(req.getClienteEmail());
                bloqueo.setPagoId(pago.getId());
                bloqueo.setCreadoEn(java.time.LocalDateTime.now());
                bloqueo.setExpiraEn(java.time.LocalDateTime.now().plusMinutes(10));
                bloqueos.add(bloqueo);
            }
        }
        repositorioBloqueoTemporal.saveAll(bloqueos);
        log.info("Creados {} bloqueos temporales para el pago {}", bloqueos.size(), pago.getId());

        // Crear PreferenceRequest usando SDK types y llamar al wrapper SDK
        try {
                PreferenceItemRequest item = PreferenceItemRequest.builder()
                    .title("Seña - Reserva")
                    .quantity(1)
                    .unitPrice(seniaEsperada)
                    .build();

                PreferenceRequest.PreferenceRequestBuilder preferenceBuilder = PreferenceRequest.builder()
                    .items(java.util.List.of(item))
                    .externalReference(pago.getId().toString());

                // Construir notification_url absoluta si se configura la base en propiedades
                String mpNotificationBase = env.getProperty("mp.notification.base-url", "");
                if (mpNotificationBase != null && !mpNotificationBase.isBlank()) {
                    // eliminar slash final si existe
                    if (mpNotificationBase.endsWith("/")) mpNotificationBase = mpNotificationBase.substring(0, mpNotificationBase.length()-1);
                    preferenceBuilder.notificationUrl(mpNotificationBase + "/webhook/mercadopago");
                    log.debug("Usando notification_url absoluta para Mercado Pago: {}", mpNotificationBase + "/webhook/mercadopago");

                    // Añadir back_urls para que Mercado Pago redirija al usuario al sitio tras el pago
                    // Incluir pagoId en la URL para poder buscar la reserva creada
                    PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                            .success(mpNotificationBase + "/reservas/pago-exitoso?pagoId=" + pago.getId())
                            .failure(mpNotificationBase + "/reservas/pago-fallido?pagoId=" + pago.getId())
                            .pending(mpNotificationBase + "/reservas/pago-pendiente?pagoId=" + pago.getId())
                            .build();
                    preferenceBuilder.backUrls(backUrls).autoReturn("approved");
                } else {
                    // Fallback a ruta relativa (no recomendada para entornos públicos)
                    preferenceBuilder.notificationUrl("/webhook/mercadopago");
                    log.warn("No se encontró 'mp.notification.base-url' en propiedades; usando notification_url relativa '/webhook/mercadopago'. Para entornos públicos configure la URL absoluta (ej: ngrok).\n");

                    // Añadir back_urls relativas con pagoId
                    PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                            .success("/reservas/pago-exitoso?pagoId=" + pago.getId())
                            .failure("/reservas/pago-fallido?pagoId=" + pago.getId())
                            .pending("/reservas/pago-pendiente?pagoId=" + pago.getId())
                            .build();
                    preferenceBuilder.backUrls(backUrls).autoReturn("approved");
                }

                // Si el cliente envía un email lo forzamos en el payer para que Mercado Pago
                // envíe la confirmación/validación a ese email (útil en sandbox y pruebas).
                if (req.getClienteEmail() != null && !req.getClienteEmail().isBlank()) {
                PreferencePayerRequest payer = PreferencePayerRequest.builder()
                    .email(req.getClienteEmail())
                    .build();
                preferenceBuilder.payer(payer);
                }

                PreferenceRequest preferenceReq = preferenceBuilder.build();

            MercadoPagoClient.PreferenceResult result = mpClient.createPreference(accessToken, preferenceReq);

            pago.setPreferenceId(result.id);
            pago.setInitPoint(result.initPoint);
            pago.setEstadoPago("PENDIENTE");
            repositorioPago.save(pago);

            return new PreferenciaResponse(pago.getId(), result.id, result.initPoint, seniaEsperada);

        } catch (Exception ex) {
            log.error("Error creando preference MP: {}", ex.getMessage(), ex);
            pago.setEstadoPago("ERROR_CREACION");
            repositorioPago.save(pago);
            throw ex;
        }
    }

    public java.util.Optional<Pago> obtenerPagoPorId(Long id) {
        return repositorioPago.findById(id);
    }

    public void asociarPagoAReserva(Long pagoId, Reserva reserva) {
        Pago pago = repositorioPago.findById(pagoId).orElseThrow(() -> new IllegalArgumentException("Pago no encontrado"));
        pago.setReserva(reserva);
        pago.setMetodoPago(MetodoPago.MERCADO_PAGO);
        repositorioPago.save(pago);
    }
}
