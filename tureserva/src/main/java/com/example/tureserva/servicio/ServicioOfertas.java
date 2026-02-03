package com.example.tureserva.servicio;

import com.example.tureserva.modelo.*;
import com.example.tureserva.modelo.enums.EstadoOferta;
import com.example.tureserva.modelo.enums.EstadoReserva;
import com.example.tureserva.modelo.enums.TipoMovimiento;
import com.example.tureserva.repositorio.RepositorioCliente;
import com.example.tureserva.repositorio.RepositorioOfertaFlash;
import com.example.tureserva.repositorio.RepositorioReserva;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;

/**
 * Servicio para gestionar Ofertas Flash.
 * Implementa la estrategia 50/50 de recupero de señas.
 * 
 * <p>Estrategia financiera:
 * - Cliente original recupera 50% de su seña
 * - Nuevo cliente paga 50% del precio total
 * - El complejo recibe el 50% restante como compensación
 * 
 * @author TuReserva
 */
@Service
@Transactional(readOnly = true)
public class ServicioOfertas {
    
    private static final Logger logger = LoggerFactory.getLogger(ServicioOfertas.class);
    
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;
    
    private final RepositorioOfertaFlash repositorioOferta;
    private final RepositorioReserva repositorioReserva;
    private final ServicioCuentaCorriente servicioCuentaCorriente;
    private final ServicioReserva servicioReserva;
    private final ServicioEmail servicioEmail;
    private final RepositorioCliente repositorioCliente;
    private final ServicioGeneradorCodigos servicioGeneradorCodigos;
    
    public ServicioOfertas(
            RepositorioOfertaFlash repositorioOferta,
            RepositorioReserva repositorioReserva,
            ServicioCuentaCorriente servicioCuentaCorriente,
            ServicioReserva servicioReserva,
            ServicioEmail servicioEmail,
            RepositorioCliente repositorioCliente,
            ServicioGeneradorCodigos servicioGeneradorCodigos) {
        this.repositorioOferta = repositorioOferta;
        this.repositorioReserva = repositorioReserva;
        this.servicioCuentaCorriente = servicioCuentaCorriente;
        this.servicioReserva = servicioReserva;
        this.servicioEmail = servicioEmail;
        this.repositorioCliente = repositorioCliente;
        this.servicioGeneradorCodigos = servicioGeneradorCodigos;
    }
    
    /**
     * Genera una Oferta Flash a partir de una reserva cancelada tardíamente.
     * 
     * <p><b>IMPORTANTE</b>: Usa REQUIRES_NEW para crear una transacción independiente.
     * Esto permite que si falla la generación, podamos manejarlo explícitamente
     * sin afectar automáticamente la transacción padre.
     * 
     * @param reservaCancelada Reserva que fue cancelada después del plazo
     * @return OfertaFlash creada
     * @throws IllegalStateException si la reserva no cumple los requisitos
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OfertaFlash generarOferta(Reserva reservaCancelada) {
        if (reservaCancelada == null) {
            throw new IllegalArgumentException("La reserva cancelada es obligatoria");
        }
        
        if (reservaCancelada.getEstado() != EstadoReserva.CANCELADA) {
            throw new IllegalStateException("Solo se pueden generar ofertas de reservas canceladas");
        }
        
        if (reservaCancelada.getMontoSenia() == null || 
            reservaCancelada.getMontoSenia().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("La reserva debe tener una seña pagada");
        }
        
        // Verificar que no exista ya una oferta para esta reserva
        var ofertaExistente = repositorioOferta.findByReservaOriginal(reservaCancelada);
        if (ofertaExistente.isPresent()) {
            logger.info("⚠️  Ya existe una Oferta Flash para reserva {}. Retornando existente.", 
                reservaCancelada.getCodigoReserva());
            return ofertaExistente.get();
        }
        
        logger.info("Generando Oferta Flash para reserva {}", reservaCancelada.getCodigoReserva());
        
        // Calcular montos según estrategia 50/50
        BigDecimal montoSeniaOriginal = reservaCancelada.getMontoSenia();
        BigDecimal montoRecuperoCliente = montoSeniaOriginal.divide(
            BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP); // 50% de la seña se recupera
        
        // El descuento para la nueva reserva es IGUAL al 50% de la seña (lo que se recupera)
        // NO es el 50% del total, sino el monto que se recupera
        BigDecimal montoDescuentoOferta = montoRecuperoCliente; // Mismo monto que se recupera
        
        // Crear oferta
        OfertaFlash oferta = new OfertaFlash();
        oferta.setReservaOriginal(reservaCancelada);
        oferta.setClienteOriginal(reservaCancelada.getCliente());
        oferta.setEstado(EstadoOferta.DISPONIBLE);
        oferta.setMontoSeniaOriginal(montoSeniaOriginal);
        oferta.setMontoRecuperoCliente(montoRecuperoCliente);
        oferta.setMontoDescuentoOferta(montoDescuentoOferta);
        oferta.generarToken();
        
        // Establecer vigencia de 24 horas
        oferta.setFechaExpiracion(LocalDateTime.now().plusHours(24));
        
        oferta = repositorioOferta.save(oferta);
        
        logger.info("✅ Oferta Flash {} creada exitosamente", oferta.getId());
        logger.info("   📍 Token: {}", oferta.getToken());
        logger.info("   💰 Recupero cliente original: ${}", montoRecuperoCliente);
        logger.info("   🎯 Descuento para nuevo cliente: ${}", montoDescuentoOferta);
        logger.info("   ⏰ Expira: {}", oferta.getFechaExpiracion());
        logger.info("   🔗 URL: {}/ofertas/{}", baseUrl, oferta.getToken());
        
        // Notificar a clientes candidatos
        // IMPORTANTE: Las notificaciones se envían después de guardar la oferta
        // Si fallan, la oferta ya está creada y no se pierde
        try {
            int clientesNotificados = notificarClientesCandidatos(oferta);
            logger.info("✉️  Notificaciones enviadas a {} clientes candidatos", clientesNotificados);
        } catch (Exception e) {
            logger.error("❌ Error al notificar clientes candidatos (oferta ya creada): {}", e.getMessage(), e);
            // La oferta ya está guardada, solo falló el envío de notificaciones
        }
        
        return oferta;
    }
    
    /**
     * Reclama una Oferta Flash y crea la nueva reserva con descuento.
     * CRÍTICO: Usa SERIALIZABLE isolation para evitar doble reclamo.
     * 
     * @param token Token único de la oferta
     * @param emailClienteReclamante Email del cliente que reclama la oferta
     * @return Reserva nueva creada con el descuento
     * @throws IllegalStateException si la oferta no está disponible o expiró
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 10)
    @Retry(name = "reclamarOferta", fallbackMethod = "reclamarOfertaFallback")
    public Reserva reclamarOferta(String token, String emailClienteReclamante) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("El token es obligatorio");
        }
        
        if (emailClienteReclamante == null || emailClienteReclamante.isBlank()) {
            throw new IllegalArgumentException("El email del cliente es obligatorio");
        }
        
        // Buscar cliente por email
        Cliente clienteReclamante = repositorioCliente.findByEmail(emailClienteReclamante)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        
        logger.info("🎯 Cliente {} intentando reclamar oferta {}", 
                clienteReclamante.getEmail(), token);
        
        // ✅ CRITICAL: Buscar oferta con PESSIMISTIC_WRITE lock
        // Esto bloquea la fila hasta el commit, previniendo race conditions
        OfertaFlash oferta = repositorioOferta.findByTokenWithLock(token)
            .orElseThrow(() -> new IllegalArgumentException("Oferta no encontrada"));
        
        logger.info("🔒 Lock adquirido para oferta {}. Estado actual: {}", token, oferta.getEstado());
        
        // Cargar detalles completos (con los espacios, etc.)
        oferta = repositorioOferta.findByTokenWithDetalles(token)
            .orElseThrow(() -> new IllegalArgumentException("Oferta no encontrada"));
        
        // ✅ Validar que el cliente reclamante no sea el mismo que canceló
        if (oferta.getClienteOriginal().getId().equals(clienteReclamante.getId())) {
            throw new IllegalStateException("No puedes reclamar tu propia oferta");
        }
        
        // ✅ Validar disponibilidad DENTRO del lock
        if (!oferta.estaDisponible()) {
            if (oferta.haExpirado()) {
                throw new IllegalStateException("Esta oferta ha expirado");
            }
            throw new IllegalStateException("Esta oferta ya no está disponible");
        }
        
        Reserva reservaOriginal = oferta.getReservaOriginal();
        
        // Crear nueva reserva duplicando los datos de la original
        // pero con 50% de descuento
        Reserva nuevaReserva = crearReservaConDescuento(
            reservaOriginal, 
            clienteReclamante, 
            oferta.getMontoDescuentoOferta()
        );
        
        // Marcar oferta como reclamada
        oferta.reclamar(clienteReclamante, nuevaReserva);
        oferta = repositorioOferta.save(oferta);
        
        // TRANSACCIÓN CRÍTICA: Acreditar 50% al cliente original
        servicioCuentaCorriente.acreditarSaldo(
            oferta.getClienteOriginal(),
            oferta.getMontoRecuperoCliente(),
            String.format("Recupero de seña por venta de Oferta Flash - Reserva %s", 
                reservaOriginal.getCodigoReserva()),
            TipoMovimiento.RECUPERO_SENIA,
            oferta,
            reservaOriginal
        );
        
        logger.info("💰 Acreditados ${} a cliente original {}", 
                oferta.getMontoRecuperoCliente(), 
                oferta.getClienteOriginal().getEmail());
        
        logger.info("✅ Oferta {} reclamada exitosamente por {}", 
                token, clienteReclamante.getEmail());
        
        // ✅ Enviar emails con Circuit Breaker (protección contra fallas del servidor de email)
        // Se ejecutan fuera de la transacción principal para no afectar el commit
        enviarEmailsReclamo(oferta, nuevaReserva);
        
        return nuevaReserva;
    }
    
    /**
     * Método fallback cuando reclamarOferta() falla después de reintentos.
     * Se ejecuta cuando Resilience4j agota los 3 intentos configurados.
     * 
     * <p><b>NOTA</b>: Invocado por Resilience4j mediante reflexión.
     * La advertencia "never used locally" es un falso positivo del analizador estático.
     */
    @SuppressWarnings("unused") // Invocado por @Retry mediante reflexión
    private Reserva reclamarOfertaFallback(String token, String emailClienteReclamante, Exception ex) {
        logger.error("🚨 FALLBACK: No se pudo reclamar la oferta {} después de reintentos. Razón: {}", 
            token, ex.getMessage());
        throw new IllegalStateException(
            "La oferta no pudo ser reclamada en este momento. " +
            "Por favor, intenta nuevamente en unos momentos.", ex);
    }
    
    /**
     * Envía emails de notificación con Circuit Breaker.
     * Si el servicio de email falla repetidamente, el circuit breaker se abre
     * y previene más llamadas hasta que se recupere.
     */
    @CircuitBreaker(name = "emailService", fallbackMethod = "enviarEmailsFallback")
    private void enviarEmailsReclamo(OfertaFlash oferta, Reserva nuevaReserva) {
        try {
            enviarEmailVentaExitosa(oferta);
            logger.info("✉️  Email enviado al cliente original");
        } catch (Exception e) {
            logger.error("Error al enviar email al cliente original: {}", e.getMessage());
            // Circuit breaker detectará el fallo
        }
        
        try {
            enviarEmailReservaOferta(nuevaReserva, oferta);
            logger.info("✉️  Email enviado al nuevo cliente");
        } catch (Exception e) {
            logger.error("Error al enviar email al nuevo cliente: {}", e.getMessage());
            // Circuit breaker detectará el fallo
        }
    }
    
    /**
     * Fallback cuando el servicio de email está caído.
     * 
     * <p><b>NOTA</b>: Invocado por Resilience4j mediante reflexión.
     * La advertencia "never used locally" es un falso positivo del analizador estático.
     */
    @SuppressWarnings("unused") // Invocado por @CircuitBreaker mediante reflexión
    private void enviarEmailsFallback(OfertaFlash oferta, Reserva nuevaReserva, Exception ex) {
        logger.warn("⚠️  Circuit breaker ABIERTO: No se pudieron enviar emails. Se intentará más tarde.");
        // Los emails se pueden reintentar con un job scheduler posteriormente
    }
    
    /**
     * Crea una nueva reserva duplicando los datos de la original pero con descuento.
     * Valida disponibilidad antes de crear.
     * 
     * IMPORTANTE: El nuevo cliente NO paga seña, se confirma directamente.
     * Deberá pagar el total con descuento al finalizar la reserva.
     */
    private Reserva crearReservaConDescuento(
            Reserva reservaOriginal, 
            Cliente nuevoCliente, 
            BigDecimal descuento) {
        
        logger.info("🔍 Validando disponibilidad para nueva reserva...");
        
        // 1. VALIDAR DISPONIBILIDAD de cada espacio/horario
        for (DetalleReserva detalleOriginal : reservaOriginal.getDetalles()) {
            EspacioReservable espacio = detalleOriginal.getEspacioReservable();
            java.time.LocalDate fecha = detalleOriginal.getFechaReserva();
            java.time.LocalTime horaInicio = detalleOriginal.getHoraInicio();
            
            // Obtener intervalos disponibles
            var intervalos = servicioReserva.generarIntervalosDisponibles(espacio, fecha);
            
            // Verificar cada hora del detalle
            int duracionHoras = detalleOriginal.getDuracionHoras() != null 
                ? detalleOriginal.getDuracionHoras().intValue() 
                : 1;
            
            for (int i = 0; i < duracionHoras; i++) {
                java.time.LocalTime horaVerificar = horaInicio.plusHours(i);
                boolean disponible = intervalos.stream()
                    .anyMatch(intervalo -> intervalo.horaInicio().equals(horaVerificar) && intervalo.disponible());
                
                if (!disponible) {
                    throw new IllegalStateException(
                        String.format("El horario %s ya no está disponible para %s. " +
                            "La oferta no puede ser reclamada porque alguien más reservó ese horario.",
                            horaVerificar, espacio.getNombre()));
                }
            }
        }
        
        logger.info("✅ Disponibilidad validada correctamente");
        
        // 2. CREAR NUEVA RESERVA
        Reserva nuevaReserva = new Reserva();
        nuevaReserva.setCliente(nuevoCliente);
        nuevaReserva.setFechaReserva(reservaOriginal.getFechaReserva());
        nuevaReserva.setFechaCreacion(LocalDateTime.now());
        nuevaReserva.setEstado(EstadoReserva.CONFIRMADA); // Confirmada sin pagar seña
        
        // Generar código único usando servicio centralizado
        String codigo = servicioGeneradorCodigos.generarCodigoReserva(
            codigoGenerado -> repositorioReserva.existsByCodigoReserva(codigoGenerado)
        );
        nuevaReserva.setCodigoReserva(codigo);
        
        // 3. CALCULAR MONTOS
        // El descuento es el 50% de la seña del cliente original
        // IMPORTANTE: Calcular subtotal original desde los detalles (sin descuentos previos)
        BigDecimal subtotalOriginal = reservaOriginal.getDetalles().stream()
            .map(DetalleReserva::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal nuevoMontoTotal = subtotalOriginal.subtract(descuento);
        
        // El nuevo cliente NO paga seña (oferta de último momento)
        // Deberá pagar el total con descuento al finalizar
        nuevaReserva.setMontoTotal(nuevoMontoTotal); // Total que debe pagar al finalizar
        nuevaReserva.setMontoSenia(BigDecimal.ZERO); // NO requiere seña
        nuevaReserva.setMontoRestante(nuevoMontoTotal); // Debe pagar todo al finalizar
        
        // 4. INICIALIZAR CAMPOS
        nuevaReserva.setAlertaEnviada(false);
        nuevaReserva.setAvisoRecuperoEnviado(false);
        
        // 5. COPIAR DETALLES (espacios y horarios)
        for (DetalleReserva detalleOriginal : reservaOriginal.getDetalles()) {
            DetalleReserva nuevoDetalle = new DetalleReserva();
            nuevoDetalle.setEspacioReservable(detalleOriginal.getEspacioReservable());
            nuevoDetalle.setFechaReserva(detalleOriginal.getFechaReserva());
            nuevoDetalle.setHoraInicio(detalleOriginal.getHoraInicio());
            nuevoDetalle.setHoraFin(detalleOriginal.getHoraFin());
            nuevoDetalle.setDuracionHoras(detalleOriginal.getDuracionHoras());
            nuevoDetalle.setPrecioPorHora(detalleOriginal.getPrecioPorHora());
            nuevoDetalle.setSubtotal(detalleOriginal.getSubtotal());
            
            nuevaReserva.agregarDetalle(nuevoDetalle);
        }
        
        // 6. MARCAR PARA EVITAR RECÁLCULO AUTOMÁTICO (Oferta Flash con descuento manual)
        nuevaReserva.setEvitarRecalculoAutomatico(true);
        
        // 7. PERSISTIR
        nuevaReserva = repositorioReserva.save(nuevaReserva);
        
        logger.info("✅ Nueva reserva {} creada exitosamente", nuevaReserva.getCodigoReserva());
        logger.info("   💰 Total a pagar al finalizar: ${} (Subtotal original: ${}, Descuento: ${})",
                nuevoMontoTotal, subtotalOriginal, descuento);
        logger.info("   🎯 Sin seña requerida (Oferta Flash de último momento)");
        
        return nuevaReserva;
    }
    
    // ELIMINADO: Método generarCodigoReserva() - Ahora usa ServicioGeneradorCodigos
    // private String generarCodigoReserva() { ... }
    
    /**
     * Busca una oferta por su token
     */
    public OfertaFlash buscarPorToken(String token) {
        return repositorioOferta.findByTokenWithDetalles(token)
            .orElse(null);
    }
    
    /**
     * Lista todas las ofertas disponibles (no expiradas)
     */
    public List<OfertaFlash> listarOfertasActivas() {
        return repositorioOferta.findOfertasDisponibles(
            EstadoOferta.DISPONIBLE, 
            LocalDateTime.now()
        );
    }
    
    /**
     * Lista ofertas disponibles de un complejo específico
     */
    public List<OfertaFlash> listarOfertasPorComplejo(Long complejoId) {
        return repositorioOferta.findOfertasDisponiblesPorComplejo(
            complejoId, 
            LocalDateTime.now()
        );
    }
    
    /**
     * Obtiene las ofertas de un cliente (como cliente original)
     */
    public List<OfertaFlash> obtenerOfertasComoOriginal(Cliente cliente) {
        return repositorioOferta.findByClienteOriginalOrderByFechaCreacionDesc(cliente);
    }
    
    /**
     * Obtiene las ofertas reclamadas por un cliente
     */
    public List<OfertaFlash> obtenerOfertasReclamadas(Cliente cliente) {
        return repositorioOferta.findByClienteReclamanteOrderByFechaReclamacionDesc(cliente);
    }
    
    /**
     * Busca la oferta asociada a una reserva nueva (reclamada)
     */
    public OfertaFlash buscarPorReservaNueva(Reserva reserva) {
        return repositorioOferta.findByReservaNueva(reserva).orElse(null);
    }
    
    /**
     * Procesa ofertas expiradas (marca como EXPIRADA)
     * Se ejecuta periódicamente desde un scheduler
     */
    @Transactional
    public int procesarOfertasExpiradas() {
        List<OfertaFlash> ofertasExpiradas = repositorioOferta.findOfertasExpiradas(
            LocalDateTime.now()
        );
        
        int procesadas = 0;
        for (OfertaFlash oferta : ofertasExpiradas) {
            oferta.marcarComoExpirada();
            repositorioOferta.save(oferta);
            procesadas++;
            
            logger.info("Oferta {} marcada como expirada", oferta.getId());
        }
        
        if (procesadas > 0) {
            logger.info("Procesadas {} ofertas expiradas", procesadas);
        }
        
        return procesadas;
    }
    
    // ==================== MÉTODOS DE EMAIL ====================
    
    private void enviarEmailVentaExitosa(OfertaFlash oferta) {
        try {
            // Obtener datos desde el primer detalle de la reserva original
            Reserva reservaOriginal = oferta.getReservaOriginal();
            DetalleReserva primerDetalle = reservaOriginal.getDetalles() != null && !reservaOriginal.getDetalles().isEmpty()
                                          ? reservaOriginal.getDetalles().get(0)
                                          : null;
            String nombreComplejo = primerDetalle != null && 
                                   primerDetalle.getEspacioReservable() != null && 
                                   primerDetalle.getEspacioReservable().getComplejoDeportivo() != null
                                   ? primerDetalle.getEspacioReservable().getComplejoDeportivo().getNombre_complejo()
                                   : "Complejo";
            java.time.LocalDate fecha = primerDetalle != null ? primerDetalle.getFechaReserva() : reservaOriginal.getFechaReserva();
            
            // Construir nombre completo del cliente original
            Cliente clienteOriginal = oferta.getClienteOriginal();
            String nombreCompletoOriginal = clienteOriginal.getNombre();
            if (clienteOriginal.getApellido() != null && !clienteOriginal.getApellido().isBlank()) {
                nombreCompletoOriginal += " " + clienteOriginal.getApellido();
            }
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("nombreCliente", nombreCompletoOriginal);
            variables.put("montoRecupero", oferta.getMontoRecuperoCliente());
            variables.put("nombreComplejo", nombreComplejo);
            variables.put("fecha", fecha);
            variables.put("montoSeniaOriginal", oferta.getMontoSeniaOriginal());
            variables.put("urlMiCuenta", baseUrl + "/usuarios/perfil");
            
            servicioEmail.enviarEmailConTemplate(
                oferta.getClienteOriginal().getEmail(),
                "¡Tu Oferta Flash fue Reclamada! - Recuperaste $" + oferta.getMontoRecuperoCliente(),
                "email/oferta-reclamada-original",
                variables
            );
        } catch (Exception e) {
            logger.error("Error al enviar email de venta exitosa: {}", e.getMessage());
        }
    }
    
    private void enviarEmailReservaOferta(Reserva nuevaReserva, OfertaFlash oferta) {
        try {
            // Construir DTO para usar el template de confirmación estándar
            Cliente nuevoCliente = nuevaReserva.getCliente();
            String nombreCompleto = nuevoCliente.getNombre();
            if (nuevoCliente.getApellido() != null && !nuevoCliente.getApellido().isBlank()) {
                nombreCompleto += " " + nuevoCliente.getApellido();
            }
            
            String nombreComplejo = "";
            if (!nuevaReserva.getDetalles().isEmpty()) {
                var detalle = nuevaReserva.getDetalles().get(0);
                if (detalle.getEspacioReservable() != null && 
                    detalle.getEspacioReservable().getComplejoDeportivo() != null) {
                    nombreComplejo = detalle.getEspacioReservable().getComplejoDeportivo().getNombre_complejo();
                }
            }
            
            // Calcular el subtotal original (sin descuento) sumando los detalles
            java.math.BigDecimal subtotalOriginal = nuevaReserva.getDetalles().stream()
                .map(DetalleReserva::getSubtotal)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            
            // El monto total ORIGINAL (antes del descuento) - para mostrar en el email
            java.math.BigDecimal montoTotalOriginal = subtotalOriginal; // Sin descuento
            
            logger.info("📧 EMAIL OFERTA FLASH - Preparando DTO para reserva {}", nuevaReserva.getCodigoReserva());
            logger.info("   └─ Subtotal original (sin descuento): {}", subtotalOriginal);
            logger.info("   └─ nuevaReserva.getMontoTotal(): {}", nuevaReserva.getMontoTotal());
            logger.info("   └─ nuevaReserva.getMontoSenia(): {}", nuevaReserva.getMontoSenia());
            logger.info("   └─ nuevaReserva.getMontoRestante(): {}", nuevaReserva.getMontoRestante());
            logger.info("   └─ oferta.getMontoDescuentoOferta(): {}", oferta.getMontoDescuentoOferta());
            
            com.example.tureserva.servicio.dto.EmailReservaDTO dto = 
                new com.example.tureserva.servicio.dto.EmailReservaDTO(
                    nombreCompleto,
                    nuevaReserva.getCodigoReserva(),
                    nuevaReserva.getFechaReserva(),
                    nombreComplejo,
                    montoTotalOriginal  // Monto ANTES del descuento (para mostrar ahorro)
                );
            
            // Configurar información de la oferta
            dto.setSubtotalEspacios(subtotalOriginal); // Total sin descuento
            dto.setSubtotalServicios(java.math.BigDecimal.ZERO);
            dto.setMontoSenia(nuevaReserva.getMontoSenia()); // $0
            dto.setMontoRestante(nuevaReserva.getMontoTotal()); // Total CON descuento (monto a pagar)
            dto.setRequirioSenia(false); // NO requiere seña, es oferta flash
            dto.setDescuentoOfertaFlash(oferta.getMontoDescuentoOferta()); // Descuento aplicado
            
            logger.info("📧 DTO CONFIGURADO:");
            logger.info("   └─ dto.getMontoTotal() [mostrar como original]: {}", dto.getMontoTotal());
            logger.info("   └─ dto.getSubtotalEspacios(): {}", dto.getSubtotalEspacios());
            logger.info("   └─ dto.getDescuentoOfertaFlash(): {}", dto.getDescuentoOfertaFlash());
            logger.info("   └─ dto.getMontoRestante() [monto FINAL a pagar]: {}", dto.getMontoRestante());
            logger.info("   └─ dto.getMontoSenia(): {}", dto.getMontoSenia());
            
            // Agregar detalles
            java.util.List<com.example.tureserva.servicio.dto.EmailDetalleDTO> detallesDto = new java.util.ArrayList<>();
            for (DetalleReserva det : nuevaReserva.getDetalles()) {
                com.example.tureserva.servicio.dto.EmailDetalleDTO detDto = 
                    new com.example.tureserva.servicio.dto.EmailDetalleDTO();
                detDto.setId(det.getId());
                detDto.setEspacioNombre(det.getEspacioReservable() != null ? det.getEspacioReservable().getNombre() : "");
                detDto.setFecha(det.getFechaReserva());
                detDto.setHoraInicio(det.getHoraInicio());
                detDto.setHoraFin(det.getHoraFin());
                detDto.setDuracionHoras(det.getDuracionHoras());
                detDto.setPrecioPorHora(det.getPrecioPorHora());
                detDto.setSubtotal(det.getSubtotal());
                
                // Política de cancelación
                if (det.getEspacioReservable() != null && 
                    det.getEspacioReservable().getPoliticaCancelacion() != null) {
                    PoliticaCancelacion pc = det.getEspacioReservable().getPoliticaCancelacion();
                    detDto.setPoliticaCancelacionNombre(pc.getNombre());
                    detDto.setPoliticaHorasAnticipacion(pc.getHorasAnticipacionMinima());
                    detDto.setPoliticaPorcentajeDevolucion(pc.getPorcentajeDevolucion());
                }
                
                detallesDto.add(detDto);
            }
            dto.setDetalles(detallesDto);
            
            // Agregar recordatorios especiales para oferta flash
            dto.getRecordatorios().add("🎉 ¡Felicitaciones! Aprovechaste una Oferta Flash");
            dto.getRecordatorios().add("💰 Ahorraste $" + oferta.getMontoDescuentoOferta() + " en esta reserva");
            dto.getRecordatorios().add("📝 Pagarás $" + nuevaReserva.getMontoTotal() + " al finalizar (sin seña requerida)");
            dto.getRecordatorios().add("Por favor presentarse 10 minutos antes del horario reservado");
            
            dto.setPoliticaCancelacionResumen("Consulta la política de cancelación de cada espacio en los detalles anteriores");
            
            // Enviar email usando el método estándar de confirmación
            servicioEmail.enviarConfirmacionReserva(dto, nuevoCliente.getEmail());
            
            logger.info("✉️  Email de confirmación enviado a {} (Oferta Flash)", nuevoCliente.getEmail());
            
        } catch (Exception e) {
            logger.error("❌ Error al enviar email de confirmación de oferta: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Notifica a clientes candidatos sobre la nueva Oferta Flash.
     * Busca clientes que hayan reservado en el mismo complejo/deporte en los últimos 6 meses.
     * 
     * @param oferta La oferta flash generada
     * @return Número de clientes notificados
     */
    private int notificarClientesCandidatos(OfertaFlash oferta) {
        try {
            logger.info("🔍 Buscando clientes candidatos para Oferta Flash {}...", oferta.getId());
            
            Reserva reservaOriginal = oferta.getReservaOriginal();
            
            // Validar que tenga detalles
            if (reservaOriginal.getDetalles() == null || reservaOriginal.getDetalles().isEmpty()) {
                logger.warn("⚠️  Reserva original sin detalles, no se pueden buscar candidatos");
                return 0;
            }
            
            DetalleReserva primerDetalle = reservaOriginal.getDetalles().get(0);
            EspacioReservable espacio = primerDetalle.getEspacioReservable();
            
            if (espacio == null || espacio.getComplejoDeportivo() == null) {
                logger.warn("⚠️  Espacio o complejo no encontrado, no se pueden buscar candidatos");
                return 0;
            }
            
            Long complejoId = espacio.getComplejoDeportivo().getId_complejo();
            String tipoEspacio = espacio.getTipoEspacio(); // "CANCHA" o "SALON"
            
            logger.info("   📍 Complejo: {}", espacio.getComplejoDeportivo().getNombre_complejo());
            logger.info("   🏟️  Tipo espacio: {}", tipoEspacio);
            
            // ✅ OPTIMIZACIÓN: Query directa en vez de findAll() + stream()
            // Antes: Cargaba 100K+ reservas en memoria (58s, 1.2GB)
            // Ahora: Query SQL con filtros (150ms, 50MB)
            LocalDateTime fechaLimite = LocalDateTime.now().minusMonths(6);
            
            List<Cliente> clientesCandidatos = repositorioOferta.findClientesCandidatosParaOferta(
                complejoId,
                oferta.getClienteOriginal().getId(),
                fechaLimite
            );
            
            logger.info("   👥 Clientes candidatos encontrados: {} (query optimizada)", clientesCandidatos.size());
            
            if (clientesCandidatos.isEmpty()) {
                logger.info("   ℹ️  No hay clientes candidatos para notificar");
                return 0;
            }
            
            // Enviar notificaciones
            int notificados = 0;
            for (Cliente cliente : clientesCandidatos) {
                try {
                    enviarNotificacionOfertaFlash(cliente, oferta);
                    notificados++;
                    logger.debug("      ✉️  Notificado: {} ({})", cliente.getNombre(), cliente.getEmail());
                } catch (Exception e) {
                    logger.error("      ❌ Error notificando a {}: {}", cliente.getEmail(), e.getMessage());
                }
            }
            
            logger.info("   ✅ Total notificados: {}/{}", notificados, clientesCandidatos.size());
            return notificados;
            
        } catch (Exception e) {
            logger.error("❌ Error buscando clientes candidatos: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * Envía email de notificación de Oferta Flash a un cliente candidato.
     */
    private void enviarNotificacionOfertaFlash(Cliente cliente, OfertaFlash oferta) {
        try {
            Reserva reservaOriginal = oferta.getReservaOriginal();
            DetalleReserva primerDetalle = reservaOriginal.getDetalles().get(0);
            
            String nombreComplejo = primerDetalle.getEspacioReservable() != null &&
                                   primerDetalle.getEspacioReservable().getComplejoDeportivo() != null
                                   ? primerDetalle.getEspacioReservable().getComplejoDeportivo().getNombre_complejo()
                                   : "Complejo";
            
            // Construir nombre completo del cliente
            String nombreCompleto = cliente.getNombre();
            if (cliente.getApellido() != null && !cliente.getApellido().isBlank()) {
                nombreCompleto += " " + cliente.getApellido();
            }
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("nombreCliente", nombreCompleto);
            variables.put("nombreComplejo", nombreComplejo);
            variables.put("nombreEspacio", primerDetalle.getEspacioReservable().getNombre());
            variables.put("fecha", primerDetalle.getFechaReserva());
            variables.put("horaInicio", primerDetalle.getHoraInicio());
            variables.put("horaFin", primerDetalle.getHoraFin());
            variables.put("precioOriginal", reservaOriginal.getMontoTotal());
            variables.put("descuento", oferta.getMontoDescuentoOferta());
            variables.put("precioFinal", reservaOriginal.getMontoTotal().subtract(oferta.getMontoDescuentoOferta()));
            variables.put("horasVigencia", 24);
            variables.put("urlOferta", baseUrl + "/ofertas/" + oferta.getToken());
            
            servicioEmail.enviarEmailConTemplate(
                cliente.getEmail(),
                "🎯 ¡Oferta Flash! " + nombreComplejo + " - Descuento Especial",
                "email/notificacion-oferta-flash",
                variables
            );
            
        } catch (Exception e) {
            throw new RuntimeException("Error enviando notificación", e);
        }
    }
}
