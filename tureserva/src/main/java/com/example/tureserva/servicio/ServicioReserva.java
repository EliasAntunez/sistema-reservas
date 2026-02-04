package com.example.tureserva.servicio;

import com.example.tureserva.evento.ReservaCanceladaEvent;
import com.example.tureserva.evento.ReservaConfirmadaEvent;
import com.example.tureserva.evento.ReservaCreadaEvent;
import com.example.tureserva.evento.ReservaFinalizadaEvent;
import com.example.tureserva.modelo.*;
import com.example.tureserva.modelo.enums.EstadoReserva;
import com.example.tureserva.modelo.enums.MetodoPago;
import com.example.tureserva.modelo.enums.TipoPago;
import com.example.tureserva.repositorio.RepositorioEspacioReservable; 
import com.example.tureserva.repositorio.RepositorioDetalleReserva;
import com.example.tureserva.repositorio.RepositorioReserva;
import com.example.tureserva.repositorio.RepositorioPago;
import com.example.tureserva.util.AuditoriaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/**
 * Servicio para gestión de reservas.
 */
@Service
@Transactional(readOnly = true)
public class ServicioReserva {
    
    private static final Logger logger = LoggerFactory.getLogger(ServicioReserva.class);
    private static final int DURACION_INTERVALO_MINUTOS = 60;
    
    private final RepositorioDetalleReserva repositorioDetalleReserva;
    private final RepositorioEspacioReservable repositorioEspacioReservable;
    private final RepositorioReserva repositorioReserva;
    private final RepositorioPago repositorioPago;
    private final ServicioServicioAdicional servicioServicioAdicional;
    private final com.example.tureserva.repositorio.RepositorioDetalleServicioAdicional repositorioDetalleServicioAdicional;
    private final EntityManager entityManager;
    private final ServicioEmail servicioEmail;
    private final ServicioOfertas servicioOfertas; // Lazy injection para evitar ciclo
    private final ServicioGeneradorCodigos servicioGeneradorCodigos;
    private final ApplicationEventPublisher eventPublisher;

    // Helper key para agrupar por franja horaria
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
            return java.util.Objects.equals(fecha, that.fecha) && java.util.Objects.equals(inicio, that.inicio) && java.util.Objects.equals(fin, that.fin);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(fecha, inicio, fin);
        }
    }
    
    public ServicioReserva(RepositorioDetalleReserva repositorioDetalleReserva,
                         RepositorioEspacioReservable repositorioEspacioReservable,
                         RepositorioReserva repositorioReserva,
                         RepositorioPago repositorioPago,
                         ServicioServicioAdicional servicioServicioAdicional,
                         com.example.tureserva.repositorio.RepositorioDetalleServicioAdicional repositorioDetalleServicioAdicional,
                         EntityManager entityManager,
                         ServicioEmail servicioEmail,
                         @Lazy ServicioOfertas servicioOfertas,
                         ServicioGeneradorCodigos servicioGeneradorCodigos,
                         ApplicationEventPublisher eventPublisher) {
        this.repositorioDetalleReserva = repositorioDetalleReserva;
        this.repositorioEspacioReservable = repositorioEspacioReservable;
        this.repositorioReserva = repositorioReserva;
        this.repositorioPago = repositorioPago;
        this.servicioServicioAdicional = servicioServicioAdicional;
        this.repositorioDetalleServicioAdicional = repositorioDetalleServicioAdicional;
        this.entityManager = entityManager;
        this.servicioEmail = servicioEmail;
        this.servicioOfertas = servicioOfertas;
        this.servicioGeneradorCodigos = servicioGeneradorCodigos;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Pagina las reservas de un cliente usando la estrategia de dos pasos (IDs paginados + fetch por IDs)
     */
    public Page<Reserva> paginarReservasPorCliente(Cliente cliente, EstadoReserva estado, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Long> idsPage;
        if (estado != null) {
            idsPage = repositorioReserva.findIdsByClienteAndEstado(cliente, estado, pageable);
        } else {
            idsPage = repositorioReserva.findIdsByCliente(cliente, pageable);
        }

        List<Long> ids = idsPage.getContent();
        List<Reserva> reservas;
        if (ids.isEmpty()) {
            reservas = Collections.emptyList();
        } else {
            reservas = repositorioReserva.findByIdInWithDetalles(ids);

            // Preserve order of ids
            Map<Long, Reserva> byId = reservas.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Reserva::getId, r -> r, (a,b) -> a, LinkedHashMap::new));

            reservas = ids.stream().map(byId::get).filter(Objects::nonNull).collect(Collectors.toList());
        }

        return new PageImpl<>(reservas, pageable, idsPage.getTotalElements());
    }
    
    /**
     * Genera intervalos de tiempo disponibles para un espacio en una fecha específica.
     * Respeta la jerarquía de configuraciones: personalizado > tipo > master.
     * Filtra horarios que ya pasaron (requiere al menos 1 hora de margen).
     */
    public List<IntervaloDisponible> generarIntervalosDisponibles(EspacioReservable espacio, LocalDate fecha) {
        List<IntervaloDisponible> intervalos = new ArrayList<>();
        
        try {
            // Re-cargar el espacio en contexto transaccional para acceder a relaciones LAZY
            EspacioReservable espacioConectado = repositorioEspacioReservable
                .findById(espacio.getId())
                .orElseThrow(() -> new EntityNotFoundException("Espacio no encontrado con ID: " + espacio.getId()));

            // Obtener configuración de horario efectiva (jerarquía: personalizado > tipo > master)
            ConfiguracionHorario configuracion = espacioConectado.getConfiguracionHorarioEfectiva();
            
            if (configuracion == null) {
                logger.warn("No hay configuración de horario para espacio {}", espacioConectado.getId());
                return intervalos;
            }
            
            if (!configuracion.estaActiva()) {
                logger.debug("Configuración de horario inactiva para espacio {}", espacioConectado.getId());
                return intervalos;
            }
            
            List<RangoHorario> rangosHorario = configuracion.getRangosHorario();
            if (rangosHorario.isEmpty()) {
                logger.warn("La configuración {} no tiene rangos horarios", configuracion.getId());
                return intervalos;
            }
            
            // Filtrar rangos para el día de la semana solicitado
            DayOfWeek diaSemana = fecha.getDayOfWeek();
            List<RangoHorario> rangosDelDia = rangosHorario.stream()
                    .filter(rango -> rango.getDiaSemana() == diaSemana)
                    .toList();
            
            if (rangosDelDia.isEmpty()) {
                logger.debug("No hay rangos horarios configurados para {} en espacio {}", diaSemana, espacioConectado.getId());
                return intervalos;
            }
            
                // Obtener reservas existentes (ignorar detalles pertenecientes a reservas CANCELADAS o REPROGRAMADAS)
                // REPROGRAMADA no ocupa espacio porque fue movida a otra fecha/hora
                List<DetalleReserva> reservasExistentes = repositorioDetalleReserva
                    .findByEspacioReservableAndFechaReservaAndReservaEstadoNotIn(
                        espacioConectado, 
                        fecha, 
                        java.util.Arrays.asList(
                            com.example.tureserva.modelo.enums.EstadoReserva.CANCELADA,
                            com.example.tureserva.modelo.enums.EstadoReserva.REPROGRAMADA
                        )
                    );
            
            // Generar intervalos para cada rango horario del día
            for (RangoHorario rango : rangosDelDia) {
                generarIntervalosDeRango(rango, reservasExistentes, intervalos, fecha);
            }
            
            logger.debug("Espacio {} - {} intervalos generados para {}", espacioConectado.getId(), intervalos.size(), fecha);
            return intervalos;
            
        } catch (Exception e) {
            logger.error("Error generando intervalos para espacio {}: {}", espacio.getId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Genera horarios de inicio disponibles para un rango horario específico.
     * Crea un horario cada hora desde apertura hasta cierre (exclusive).
     * Filtra horarios que ya pasaron o tienen menos de 1 hora de margen.
     */
    private void generarIntervalosDeRango(RangoHorario rango, 
                                          List<DetalleReserva> reservasExistentes,
                                          List<IntervaloDisponible> intervalos,
                                          LocalDate fechaReserva) {
        
        LocalTime horaActual = rango.getHoraApertura();
        LocalTime horaCierre = rango.getHoraCierre();
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime fechaHoraReserva = LocalDateTime.of(fechaReserva, horaActual);
        
        // Calcular el tiempo mínimo requerido (ahora + 1 hora)
        LocalDateTime tiempoMinimoRequerido = ahora.plusHours(1);
        
        while (!horaActual.equals(horaCierre)) {
            fechaHoraReserva = LocalDateTime.of(fechaReserva, horaActual);
            
            // Filtrar horarios que ya pasaron o tienen menos de 1 hora de margen
            if (fechaHoraReserva.isBefore(tiempoMinimoRequerido)) {
                horaActual = horaActual.plusMinutes(DURACION_INTERVALO_MINUTOS);
                continue; // Saltar este horario
            }
            
            LocalTime horaFinReserva = horaActual.plusMinutes(DURACION_INTERVALO_MINUTOS);
            boolean disponible = !estaOcupado(horaActual, horaFinReserva, reservasExistentes);
            intervalos.add(new IntervaloDisponible(horaActual, disponible));
            horaActual = horaActual.plusMinutes(DURACION_INTERVALO_MINUTOS);
        }
    }
    
    /**
     * Verifica si un horario de inicio está ocupado por alguna reserva existente.
     * Un horario está ocupado si solapa con alguna reserva.
     */
    private boolean estaOcupado(LocalTime horaInicio, LocalTime horaFin, 
                                  List<DetalleReserva> reservasExistentes) {
        return reservasExistentes.stream()
                .anyMatch(detalle -> solapa(horaInicio, horaFin, 
                                            detalle.getHoraInicio(), 
                                            detalle.getHoraFin()));
    }
    
    /**
     * Verifica si dos rangos de tiempo se solapan.
     * Maneja correctamente el caso de horarios que cruzan medianoche.
     */
    private boolean solapa(LocalTime inicio1, LocalTime fin1, LocalTime inicio2, LocalTime fin2) {
        // Detectar si algún rango cruza medianoche (00:00)
        boolean rango1CruzaMedianoche = fin1.equals(LocalTime.MIDNIGHT) || fin1.isBefore(inicio1);
        boolean rango2CruzaMedianoche = fin2.equals(LocalTime.MIDNIGHT) || fin2.isBefore(inicio2);
        
        // Caso 1: Ninguno cruza medianoche - lógica simple
        if (!rango1CruzaMedianoche && !rango2CruzaMedianoche) {
            return inicio1.isBefore(fin2) && fin1.isAfter(inicio2);
        }
        
        // Caso 2: Rango1 cruza medianoche (ej: 22:00-00:00 o 22:00-02:00)
        if (rango1CruzaMedianoche && !rango2CruzaMedianoche) {
            // Rango1 ocupa desde inicio1 hasta 23:59 y desde 00:00 hasta fin1
            // Solapa si rango2 está en cualquiera de esos períodos
            return inicio2.compareTo(inicio1) >= 0 || fin2.compareTo(fin1) <= 0 || fin2.isAfter(inicio1);
        }
        
        // Caso 3: Rango2 cruza medianoche
        if (!rango1CruzaMedianoche && rango2CruzaMedianoche) {
            // Invertir la lógica del caso 2
            return inicio1.compareTo(inicio2) >= 0 || fin1.compareTo(fin2) <= 0 || fin1.isAfter(inicio2);
        }
        
        // Caso 4: Ambos cruzan medianoche - siempre solapan
        return true;
    }
    
    // ==================== MÉTODOS DE CREACIÓN DE RESERVAS ====================
    
    /**
     * Crea una reserva con múltiples espacios desde DatosReservaTemp.
     * Procesa todos los items (espacios) agregados en la sesión.
     * 
     * @param cliente Cliente que realiza la reserva
     * @param datosReserva Datos temporales con la lista de espacios y horarios
     * @return Reserva creada y guardada con todos sus detalles
     */
    @Transactional
    public Reserva crearReservaDesdeDatosTemp(Cliente cliente, DatosReservaTemp datosReserva) {
        return crearReservaDesdeDatosTemp(cliente, datosReserva, null);
    }

    /**
     * Calcula de forma no persistente el monto total estimado de una
     * reserva a partir de los datos temporales. Se usa para validar
     * montos antes de crear la reserva definitiva (por ejemplo: comparar
     * con el monto de la seña pagada).
     *
     * Solo considera el precio por hora del espacio y la duración de
     * cada item. No incluye servicios adicionales cuando no están
     * provistos en el DTO.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.math.BigDecimal calcularMontoEstimado(DatosReservaTemp datosReserva) {
        return calcularMontoEstimado(datosReserva, null);
    }

    /**
     * Calcula el monto estimado incluyendo servicios adicionales cuando se
     * provee el mapa `serviciosPorItem`.
     *
     * @param serviciosPorItem mapa: key = índice del item en la lista de `datosReserva.items`, value = mapa (idServicio -> cantidad)
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.math.BigDecimal calcularMontoEstimado(DatosReservaTemp datosReserva, Map<Integer, Map<Long, Integer>> serviciosPorItem) {
        if (datosReserva == null || !datosReserva.tieneEspacios()) {
            return java.math.BigDecimal.ZERO;
        }

        java.math.BigDecimal total = java.math.BigDecimal.ZERO;

        for (int idx = 0; idx < datosReserva.getItems().size(); idx++) {
            ItemReserva item = datosReserva.getItems().get(idx);
            if (item == null || item.getEspacioId() == null) continue;

            Optional<EspacioReservable> espacioOpt = repositorioEspacioReservable.findById(item.getEspacioId());
            if (espacioOpt.isEmpty()) {
                throw new IllegalArgumentException("Espacio no encontrado: " + item.getEspacioId());
            }

            EspacioReservable espacio = espacioOpt.get();
            Double precioPorHora = espacio.getPrecioPorHora();
            if (precioPorHora == null) precioPorHora = 0.0;

            java.math.BigDecimal precio = java.math.BigDecimal.valueOf(precioPorHora);
            java.math.BigDecimal duracion = java.math.BigDecimal.valueOf(item.getDuracionHoras());
            java.math.BigDecimal subtotal = precio.multiply(duracion);

            // Incluir servicios adicionales si se proveen
            if (serviciosPorItem != null && serviciosPorItem.containsKey(idx)) {
                Map<Long, Integer> serviciosSeleccionados = serviciosPorItem.get(idx);
                if (serviciosSeleccionados != null && !serviciosSeleccionados.isEmpty()) {
                    for (Map.Entry<Long, Integer> e : serviciosSeleccionados.entrySet()) {
                        Long idServicio = e.getKey();
                        Integer cantidad = e.getValue() == null ? 0 : e.getValue();
                        if (cantidad <= 0) continue;

                        var svcOpt = servicioServicioAdicional.obtenerPorId(idServicio);
                        if (svcOpt.isEmpty()) {
                            throw new IllegalArgumentException("Servicio adicional no encontrado: " + idServicio);
                        }
                        ServicioAdicional svc = svcOpt.get();
                        java.math.BigDecimal precioSvc = svc.getPrecio() == null ? java.math.BigDecimal.ZERO : svc.getPrecio();
                        subtotal = subtotal.add(precioSvc.multiply(java.math.BigDecimal.valueOf(cantidad)));
                    }
                }
            }

            total = total.add(subtotal);
        }

        return total;
    }

    /**
     * Crea una reserva desde los datos temporales y además procesa las selecciones
     * de servicios por item. El mapa `serviciosPorItem` tiene clave = índice del item
     * en la lista de `DatosReservaTemp.items` y valor = mapa (idServicio -> cantidad).
     */
    @Transactional
    public Reserva crearReservaDesdeDatosTemp(Cliente cliente, DatosReservaTemp datosReserva, Map<Integer, Map<Long, Integer>> serviciosPorItem) {
        return crearReservaDesdeDatosTemp(cliente, datosReserva, serviciosPorItem, true);
    }
    
    /**
     * Crea una reserva desde los datos temporales con control sobre el envío de email.
     * 
     * @param cliente Cliente que realiza la reserva
     * @param datosReserva Datos temporales de la reserva
     * @param serviciosPorItem Servicios adicionales por item
     * @param enviarEmail Si es true, envía el email de confirmación inmediatamente
     * @return Reserva creada y guardada
     */
    @Transactional
    public Reserva crearReservaDesdeDatosTemp(Cliente cliente, DatosReservaTemp datosReserva, Map<Integer, Map<Long, Integer>> serviciosPorItem, boolean enviarEmail) {
        
        logger.info("Creando reserva para cliente {} con {} espacios para fecha {}", 
            cliente.getId(), datosReserva.cantidadEspacios(), datosReserva.getFecha());
        
        if (!datosReserva.tieneEspacios()) {
            throw new IllegalArgumentException("No hay espacios agregados a la reserva");
        }
        
        // Crear entidad Reserva
        Reserva reserva = new Reserva();
        reserva.setCliente(cliente);
        reserva.setFechaReserva(datosReserva.getFecha());
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        
        // Map para acumular la cantidad solicitada por servicio por franja (antes de persistir)
        Map<Long, Map<FranjaKey, Integer>> solicitadoPorServicio = new java.util.HashMap<>();

        // Procesar cada item (espacio + horario)
        for (int idx = 0; idx < datosReserva.getItems().size(); idx++) {
            ItemReserva item = datosReserva.getItems().get(idx);
            // Obtener el espacio
            EspacioReservable espacio = repositorioEspacioReservable.findById(item.getEspacioId())
                .orElseThrow(() -> new IllegalStateException("El espacio " + item.getEspacioId() + " no existe"));
            
            // Validar disponibilidad del horario
            List<IntervaloDisponible> intervalos = generarIntervalosDisponibles(espacio, datosReserva.getFecha());
            
            for (int i = 0; i < item.getDuracionHoras(); i++) {
                LocalTime horaVerificar = item.getHoraInicio().plusHours(i);
                boolean disponible = intervalos.stream()
                    .anyMatch(intervalo -> intervalo.horaInicio().equals(horaVerificar) && intervalo.disponible());
                
                if (!disponible) {
                    throw new IllegalStateException(
                        String.format("El horario %s ya no está disponible para el espacio %s", 
                            horaVerificar, espacio.getNombre()));
                }
            }
            
            // Crear DetalleReserva para este item
            DetalleReserva detalle = new DetalleReserva();
            detalle.setEspacioReservable(espacio);
            detalle.setFechaReserva(datosReserva.getFecha());
            detalle.setHoraInicio(item.getHoraInicio());
            detalle.setHoraFin(item.getHoraFin());
            detalle.setPrecioPorHora(BigDecimal.valueOf(espacio.getPrecioPorHora()));
            
            // Calcular duración y subtotal
            detalle.calcularDuracion();
            detalle.calcularSubtotal();
            
            // Agregar detalle a la reserva
            reserva.agregarDetalle(detalle);

            // Procesar servicios adicionales seleccionados para este item (si hay)
            if (serviciosPorItem != null) {
                Map<Long, Integer> serviciosSeleccionados = serviciosPorItem.get(idx);
                if (serviciosSeleccionados != null && !serviciosSeleccionados.isEmpty()) {
                    for (Map.Entry<Long, Integer> e : serviciosSeleccionados.entrySet()) {
                        Long idServicio = e.getKey();
                        Integer cantidad = e.getValue();
                        if (cantidad == null || cantidad <= 0) continue;

                        var servicioOpt = servicioServicioAdicional.obtenerPorId(idServicio);
                        if (servicioOpt.isEmpty()) {
                            throw new IllegalStateException("Servicio adicional no encontrado: " + idServicio);
                        }

                        ServicioAdicional svc = servicioOpt.get();

                        // Validación de cantidad máxima si está definida
                        if (svc.getMaximoCantidad() != null && cantidad > svc.getMaximoCantidad()) {
                            throw new IllegalStateException("La cantidad solicitada para el servicio '" + svc.getNombre() + "' excede el máximo permitido: " + svc.getMaximoCantidad());
                        }

                        // Validar aplicabilidad al tipo de espacio (si aplica)
                        if (svc.getAplicableA() != null && !"AMBOS".equals(svc.getAplicableA().toString()) && item.getTipoEspacio() != null) {
                            if (!svc.getAplicableA().toString().equals(item.getTipoEspacio())) {
                                throw new IllegalStateException("El servicio '" + svc.getNombre() + "' no es aplicable al tipo de espacio: " + item.getTipoEspacio());
                            }
                        }

                        // Crear DetalleServicioAdicional y asociarlo
                        DetalleServicioAdicional detSvc = new DetalleServicioAdicional();
                        detSvc.setServicioAdicional(svc);
                        detSvc.setCantidad(cantidad);
                        detSvc.setPrecioUnitario(svc.getPrecio());
                        detSvc.calcularSubtotal();

                        detalle.agregarServicioAdicional(detSvc);

                        // Registrar en el mapa de solicitado por servicio+franja
                        FranjaKey fk = new FranjaKey(detalle.getFechaReserva(), detalle.getHoraInicio(), detalle.getHoraFin());
                        solicitadoPorServicio
                            .computeIfAbsent(idServicio, k -> new java.util.HashMap<>())
                            .merge(fk, cantidad, Integer::sum);
                    }
                }
            }
            
            logger.debug("Detalle agregado - Espacio: {}, Horario: {} - {}, Subtotal: ${}", 
                espacio.getNombre(), item.getHoraInicio(), item.getHoraFin(), detalle.getSubtotal());
        }
        
        // Antes de persistir, validar disponibilidad de servicios por franja
        for (Map.Entry<Long, Map<FranjaKey, Integer>> entry : solicitadoPorServicio.entrySet()) {
            Long svcId = entry.getKey();
            // Lock pesimista sobre el servicio para evitar race conditions
            ServicioAdicional svc = entityManager.find(ServicioAdicional.class, svcId, LockModeType.PESSIMISTIC_WRITE);
            if (svc == null) throw new IllegalStateException("Servicio adicional no encontrado: " + svcId);

            Integer capacidad = svc.getCapacidadTotal() != null ? svc.getCapacidadTotal() : svc.getMaximoCantidad();
            if (capacidad == null) {
                // Sin límite global definido -> no validar
                continue;
            }

            for (Map.Entry<FranjaKey, Integer> fe : entry.getValue().entrySet()) {
                FranjaKey fk = fe.getKey();
                Integer solicitado = fe.getValue() == null ? 0 : fe.getValue();

                Integer existente = repositorioDetalleServicioAdicional.sumCantidadParaServicioEnFranja(svcId, fk.fecha, fk.inicio, fk.fin);
                existente = existente == null ? 0 : existente;

                if (existente + solicitado > capacidad) {
                    throw new IllegalStateException("No hay suficiente disponibilidad para el servicio '" + svc.getNombre() + "' en la franja " + fk.inicio + "-" + fk.fin + ". Disponible: " + capacidad + ", ya reservado: " + existente + ", solicitado: " + solicitado);
                }
            }
        }

        // Generar código único usando servicio centralizado
        String codigo = servicioGeneradorCodigos.generarCodigoReserva(
            codigoGenerado -> repositorioReserva.existsByCodigoReserva(codigoGenerado)
        );
        reserva.setCodigoReserva(codigo);
        
        // Capturar complejoId del primer espacio ANTES de persistir (para auditoría)
        Long complejoIdParaAuditoria = null;
        String complejoNombreParaAuditoria = null;
        if (!reserva.getDetalles().isEmpty()) {
            DetalleReserva primerDetalle = reserva.getDetalles().get(0);
            if (primerDetalle.getEspacioReservable() != null && 
                primerDetalle.getEspacioReservable().getComplejoDeportivo() != null) {
                complejoIdParaAuditoria = primerDetalle.getEspacioReservable().getComplejoDeportivo().getId_complejo();
                complejoNombreParaAuditoria = primerDetalle.getEspacioReservable().getComplejoDeportivo().getNombre_complejo();
            }
        }
        
        // Persistir (cascade guardará también los detalles y los servicios adicionales)
        Reserva reservaGuardada = repositorioReserva.save(reserva);

        // Recalcular subtotales de cada detalle incluyendo los servicios adicionales
        // y actualizar el montoTotal de la reserva. Esto cubre el caso donde
        // Hibernate insertó los detalles antes que los servicios y el subtotal
        // quedó sin incluir los servicios adicionales.
        try {
            List<com.example.tureserva.modelo.DetalleServicioAdicional> serviciosPersistidos = repositorioDetalleServicioAdicional.findByReservaIdWithServicioAdicional(reservaGuardada.getId());

            java.util.Map<Long, java.util.List<com.example.tureserva.modelo.DetalleServicioAdicional>> serviciosPorDetalle = serviciosPersistidos.stream()
                    .collect(java.util.stream.Collectors.groupingBy(s -> s.getDetalleReserva().getId()));

            java.math.BigDecimal nuevoMontoTotal = java.math.BigDecimal.ZERO;

            for (DetalleReserva det : reservaGuardada.getDetalles()) {
                // Recalcular subtotal espacio
                java.math.BigDecimal subtotalEspacio = det.getPrecioPorHora() == null || det.getDuracionHoras() == null
                        ? java.math.BigDecimal.ZERO
                        : det.getPrecioPorHora().multiply(det.getDuracionHoras());

                // Sumar servicios asociados (si existen)
                java.util.List<com.example.tureserva.modelo.DetalleServicioAdicional> listaSvc = serviciosPorDetalle.get(det.getId());
                java.math.BigDecimal subtotalServicios = java.math.BigDecimal.ZERO;
                det.getServiciosAdicionales().clear();
                if (listaSvc != null) {
                    for (com.example.tureserva.modelo.DetalleServicioAdicional s : listaSvc) {
                        subtotalServicios = subtotalServicios.add(s.getSubtotal() == null ? java.math.BigDecimal.ZERO : s.getSubtotal());
                        det.getServiciosAdicionales().add(s);
                    }
                }

                java.math.BigDecimal subtotalDet = subtotalEspacio.add(subtotalServicios);
                det.setSubtotal(subtotalDet);
                nuevoMontoTotal = nuevoMontoTotal.add(subtotalDet);
            }

            reservaGuardada.setMontoTotal(nuevoMontoTotal);
            reservaGuardada.calcularMontoRestante();

            // Guardar los cambios si hubo diferencia
            repositorioReserva.save(reservaGuardada);

            logger.info("Reserva {} creada exitosamente con {} detalles. Total: ${}", 
                reservaGuardada.getCodigoReserva(), 
                reservaGuardada.getDetalles().size(), 
                reservaGuardada.getMontoTotal());

        } catch (Exception exRecalc) {
            // Si falla la recalculación, registrar pero seguir con el flujo (enviar email con lo que haya)
            logger.warn("No fue posible recalcular subtotales tras persistir reserva {}: {}", reservaGuardada.getId(), exRecalc.getMessage());
        }

        // Preparar DTO ligero para el envío de correo y evitar problemas de LazyInitialization
        try {
            String nombreCliente = reservaGuardada.getCliente() != null ? reservaGuardada.getCliente().getNombre() : null;
            String destinatario = reservaGuardada.getCliente() != null ? reservaGuardada.getCliente().getEmail() : null;
            String nombreComplejo = "";
            if (!reservaGuardada.getDetalles().isEmpty()) {
                var d = reservaGuardada.getDetalles().get(0);
                if (d.getEspacioReservable() != null && d.getEspacioReservable().getComplejoDeportivo() != null) {
                    nombreComplejo = d.getEspacioReservable().getComplejoDeportivo().getNombre_complejo();
                }
            }

            com.example.tureserva.servicio.dto.EmailReservaDTO dto = new com.example.tureserva.servicio.dto.EmailReservaDTO(
                nombreCliente,
                reservaGuardada.getCodigoReserva(),
                reservaGuardada.getFechaReserva(),
                nombreComplejo,
                reservaGuardada.getMontoTotal()
            );

            // Agregar información de seña y subtotales
            dto.setSubtotalEspacios(reservaGuardada.calcularSubtotalEspacios());
            dto.setSubtotalServicios(reservaGuardada.calcularSubtotalServicios());
            dto.setMontoSenia(reservaGuardada.getMontoSenia());
            dto.setMontoRestante(reservaGuardada.getMontoRestante());
            dto.setRequirioSenia(reservaGuardada.requirioSenia());

            // Llenar detalles y servicios adicionales
            java.util.List<com.example.tureserva.servicio.dto.EmailDetalleDTO> detallesDto = new java.util.ArrayList<>();
            java.util.Set<String> politicas = new java.util.HashSet<>();

            for (DetalleReserva det : reservaGuardada.getDetalles()) {
                com.example.tureserva.servicio.dto.EmailDetalleDTO detDto = new com.example.tureserva.servicio.dto.EmailDetalleDTO();
                detDto.setId(det.getId());
                detDto.setEspacioNombre(det.getEspacioReservable() != null ? det.getEspacioReservable().getNombre() : "");
                detDto.setFecha(det.getFechaReserva());
                detDto.setHoraInicio(det.getHoraInicio());
                detDto.setHoraFin(det.getHoraFin());
                detDto.setDuracionHoras(det.getDuracionHoras());
                detDto.setPrecioPorHora(det.getPrecioPorHora());
                detDto.setSubtotal(det.getSubtotal());

                // Politica de cancelación del espacio (si aplica)
                if (det.getEspacioReservable() != null && det.getEspacioReservable().getPoliticaCancelacion() != null) {
                    PoliticaCancelacion pc = det.getEspacioReservable().getPoliticaCancelacion();
                    detDto.setPoliticaCancelacionNombre(pc.getNombre());
                    detDto.setPoliticaHorasAnticipacion(pc.getHorasAnticipacionMinima());
                    detDto.setPoliticaPorcentajeDevolucion(pc.getPorcentajeDevolucion());
                    politicas.add(pc.getNombre() + " (" + pc.getHorasAnticipacionMinima() + "h antes, " + pc.getPorcentajeDevolucion() + "% devolución)");
                }

                // Servicios adicionales asociados al detalle
                if (det.getServiciosAdicionales() != null) {
                    for (DetalleServicioAdicional s : det.getServiciosAdicionales()) {
                        var svc = s.getServicioAdicional();
                        com.example.tureserva.servicio.dto.EmailServicioAdicionalDTO svcDto = new com.example.tureserva.servicio.dto.EmailServicioAdicionalDTO();
                        svcDto.setId(svc != null ? svc.getId() : null);
                        svcDto.setNombre(svc != null ? svc.getNombre() : "");
                        svcDto.setCantidad(s.getCantidad());
                        svcDto.setPrecioUnitario(s.getPrecioUnitario());
                        svcDto.setSubtotal(s.getSubtotal());
                        detDto.getServicios().add(svcDto);
                    }
                }

                detallesDto.add(detDto);
            }

            dto.setDetalles(detallesDto);

            // Recordatorios básicos (puedes extender esto)
            dto.getRecordatorios().add("Por favor presentarse 10 minutos antes del horario reservado.");
            if (!politicas.isEmpty()) {
                dto.setPoliticaCancelacionResumen(String.join("; ", politicas));
            } else {
                dto.setPoliticaCancelacionResumen("Política de cancelación por defecto: reembolso completo si cancela con al menos 1 hora de anticipación.");
            }

            if (enviarEmail) {
                servicioEmail.enviarConfirmacionReserva(dto, destinatario);
                logger.debug("Email de confirmación enviado para reserva {}", reservaGuardada.getCodigoReserva());
            } else {
                logger.debug("Email de confirmación omitido para reserva {} (será enviado posteriormente)", reservaGuardada.getCodigoReserva());
            }
        } catch (Exception e) {
            logger.error("Error al preparar/enviar correo para reserva {}: {}", reservaGuardada.getCodigoReserva(), e.getMessage(), e);
        }

        // Publicar evento para auditoría (se ejecutará después del commit)
        if (complejoIdParaAuditoria != null) {
            try {
                // Recopilar nombres de espacios reservados
                List<String> espaciosReservados = reservaGuardada.getDetalles().stream()
                    .map(d -> d.getEspacioReservable() != null ? d.getEspacioReservable().getNombre() : "Sin nombre")
                    .distinct()
                    .toList();
                
                eventPublisher.publishEvent(new ReservaCreadaEvent(
                    reservaGuardada.getId(),
                    reservaGuardada.getCodigoReserva(),
                    complejoIdParaAuditoria,
                    complejoNombreParaAuditoria,
                    reservaGuardada.getCliente().getEmail(),
                    reservaGuardada.getMontoTotal(),
                    espaciosReservados
                ));
                logger.debug("📢 Evento ReservaCreadaEvent publicado para auditoría (reserva ID: {})", reservaGuardada.getId());
            } catch (Exception e) {
                logger.warn("No se pudo publicar evento de auditoría para reserva {}: {}", 
                           reservaGuardada.getId(), e.getMessage());
            }
        } else {
            logger.warn("No se pudo publicar evento de auditoría: complejoId es null para reserva {}", 
                       reservaGuardada.getId());
        }

        return reservaGuardada;
    }
    
    /**
     * Obtiene una reserva por su ID con sus detalles cargados.
     */
    public Optional<Reserva> obtenerReservaPorId(Long id) {
        return repositorioReserva.findByIdWithDetalles(id);
    }

    /**
     * Obtiene una reserva por id incluyendo detalles y servicios adicionales (pre-fetch).
     */
    public Optional<Reserva> obtenerReservaPorIdConServicios(Long id) {
        Optional<Reserva> resOpt = repositorioReserva.findByIdWithDetalles(id);
        if (resOpt.isEmpty()) return resOpt;

        Reserva reserva = resOpt.get();

        // Cargar servicios adicionales en una consulta separada para evitar MultipleBagFetchException
        List<com.example.tureserva.modelo.DetalleServicioAdicional> servicios = repositorioDetalleServicioAdicional.findByReservaIdWithServicioAdicional(id);

        // Agrupar por detalleReserva.id
        java.util.Map<Long, java.util.List<com.example.tureserva.modelo.DetalleServicioAdicional>> porDetalle = servicios.stream()
                .collect(java.util.stream.Collectors.groupingBy(s -> s.getDetalleReserva().getId()));

        // Asociar a cada detalle las entidades cargadas
        for (com.example.tureserva.modelo.DetalleReserva det : reserva.getDetalles()) {
            java.util.List<com.example.tureserva.modelo.DetalleServicioAdicional> lista = porDetalle.get(det.getId());
            det.getServiciosAdicionales().clear();
            if (lista != null) {
                det.getServiciosAdicionales().addAll(lista);
            }
        }

        // Recalcular subtotales de detalle y montoTotal de la reserva teniendo en cuenta los servicios
        try {
            java.math.BigDecimal nuevoMontoTotal = java.math.BigDecimal.ZERO;
            for (com.example.tureserva.modelo.DetalleReserva det : reserva.getDetalles()) {
                java.math.BigDecimal subtotalEspacio = java.math.BigDecimal.ZERO;
                if (det.getPrecioPorHora() != null && det.getDuracionHoras() != null) {
                    subtotalEspacio = det.getPrecioPorHora().multiply(det.getDuracionHoras());
                }

                java.math.BigDecimal subtotalServicios = det.getServiciosAdicionales().stream()
                        .map(s -> s.getSubtotal() == null ? java.math.BigDecimal.ZERO : s.getSubtotal())
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

                java.math.BigDecimal subtotalDet = subtotalEspacio.add(subtotalServicios);
                det.setSubtotal(subtotalDet);
                nuevoMontoTotal = nuevoMontoTotal.add(subtotalDet);
            }

            reserva.setMontoTotal(nuevoMontoTotal);
            reserva.calcularMontoRestante();
        } catch (Exception ex) {
            logger.warn("No fue posible recalcular montos para reserva {}: {}", reserva.getId(), ex.getMessage());
        }

        return Optional.of(reserva);
    }
    
    // ==================== MÉTODOS DE CANCELACIÓN ====================
    
    /**
     * Cancela una reserva validando permisos, tiempo límite y políticas.
     * 
     * <p><b>FASE 2 - Migrado a Eventos:</b> Este método ya no usa @Auditable,
     * sino que publica un {@link ReservaCanceladaEvent} al final de la transacción.
     * 
     * @param reservaId ID de la reserva a cancelar
     * @param cliente Cliente que solicita la cancelación
     * @param motivo Motivo de la cancelación
     * @return ResultadoCancelacion con información sobre el resultado
     * @throws IllegalStateException si la reserva no puede ser cancelada
     */
    @Transactional(rollbackFor = Exception.class)
    public ResultadoCancelacion cancelarReserva(Long reservaId, Cliente cliente, String motivo) {
        
        logger.info("🔄 Iniciando cancelación de reserva {} por cliente {}", reservaId, cliente.getEmail());
        
        // 1. Obtener la reserva con sus detalles
        Reserva reserva = repositorioReserva.findByIdWithDetalles(reservaId)
            .orElseThrow(() -> new EntityNotFoundException("Reserva no encontrada con ID: " + reservaId));
        
        // 2. Validar que la reserva pertenece al cliente
        if (!reserva.getCliente().getId().equals(cliente.getId())) {
            throw new IllegalStateException("No tienes permiso para cancelar esta reserva");
        }
        
        // 3. Validar que no esté ya cancelada
        if (reserva.estaCancelada()) {
            throw new IllegalStateException("Esta reserva ya ha sido cancelada");
        }
        
        // 4. Validar que no esté finalizada
        if (reserva.getEstado() == EstadoReserva.FINALIZADA) {
            throw new IllegalStateException("No se puede cancelar una reserva finalizada");
        }
        
        // 5. Validar tiempo límite según política de cancelación
        ResultadoCancelacion resultado = validarTiempoLimiteCancelacion(reserva);
        
        if (!resultado.isPuedeSerCancelada()) {
            return resultado; // Retornar el resultado con el mensaje de error
        }
        
        // 6. Cancelar la reserva: marcar como CANCELADA pero mantener los detalles
        // para conservar el historial y evitar inconsistencias en validaciones (montoTotal > 0)
        reserva.cancelar(motivo);
        reserva = repositorioReserva.save(reserva);
        
        logger.info("Reserva {} cancelada por cliente {}. Motivo: {}", reservaId, cliente.getId(), motivo);
        
        // 7. ESTRATEGIA 50/50: Verificar si debe generar Oferta Flash (cancelación tardía con seña)
        boolean ofertaGenerada = false;
        
        // Verificar si la cancelación está fuera del plazo de la política
        boolean esCancelacionTardia = false;
        if (reserva.getDetalles() != null && !reserva.getDetalles().isEmpty()) {
            DetalleReserva primerDetalle = reserva.getDetalles().get(0);
            PoliticaCancelacion politica = primerDetalle.getEspacioReservable().getPoliticaCancelacion();
            
            if (politica != null) {
                LocalDateTime fechaHoraReserva = LocalDateTime.of(
                    primerDetalle.getFechaReserva(),
                    primerDetalle.getHoraInicio()
                );
                LocalDateTime ahora = LocalDateTime.now();
                long horasRestantes = java.time.Duration.between(ahora, fechaHoraReserva).toHours();
                
                // Es cancelación tardía si quedan menos horas que las requeridas por la política
                esCancelacionTardia = horasRestantes <= politica.getHorasAnticipacionMinima();
            }
        }
        
        // Generar Oferta Flash si es cancelación tardía Y tiene seña pagada
        if (esCancelacionTardia && 
            reserva.getMontoSenia() != null && 
            reserva.getMontoSenia().compareTo(BigDecimal.ZERO) > 0) {
            
            logger.info("🎯 Generando Oferta Flash para reserva {} (cancelación tardía)", reservaId);
            
            // CRÍTICO: Si falla la generación de la oferta, debe hacer rollback de toda la cancelación
            // El cliente cancela para recuperar su seña mediante la oferta
            if (this.servicioOfertas == null) {
                logger.error("❌ ServicioOfertas es null, no se puede generar oferta");
                throw new IllegalStateException("No se puede generar Oferta Flash: servicio no disponible");
            }
            
            try {
                com.example.tureserva.modelo.OfertaFlash oferta = 
                    this.servicioOfertas.generarOferta(reserva);
                
                resultado.setOfertaFlashGenerada(oferta);
                ofertaGenerada = true;
                
                logger.info("✅ Oferta Flash {} generada exitosamente. Token: {}", 
                        oferta.getId(), oferta.getToken());
                        
            } catch (Exception e) {
                // Si falla la generación de oferta, hacer rollback explícito
                logger.error("❌ Error al generar Oferta Flash: {}", e.getMessage(), e);
                throw new IllegalStateException(
                    "No se pudo completar la cancelación: Error al generar la Oferta Flash. " + e.getMessage(), e);
            }
        } else {
            logger.info("ℹ️  No se genera Oferta Flash: esCancelacionTardia={}, tieneSeña={}", 
                    esCancelacionTardia, 
                    reserva.getMontoSenia() != null && reserva.getMontoSenia().compareTo(BigDecimal.ZERO) > 0);
        }
        
        // 8. Retornar resultado exitoso
        resultado.setReservaCancelada(true);
        if (ofertaGenerada) {
            resultado.setMensaje("Reserva cancelada exitosamente. " +
                "Se generó una Oferta Flash. Si se vende, recuperarás el 50% de tu seña.");
        } else {
            resultado.setMensaje("Reserva cancelada exitosamente");
        }
        
        // FASE 2: Publicar evento de dominio para auditoría
        // El evento se procesa DESPUÉS de que la transacción se confirma (AFTER_COMMIT)
        eventPublisher.publishEvent(new ReservaCanceladaEvent(
            this,
            reserva,
            motivo,
            resultado.getMontoDevolucion(),
            resultado.getMontoPenalizacion(),
            resultado.isPuedeSerCancelada(),
            AuditoriaContextUtil.obtenerUsuarioEmail(),
            AuditoriaContextUtil.obtenerUsuarioRol(),
            AuditoriaContextUtil.obtenerIpAddress()
        ));
        
        logger.debug("✅ Evento ReservaCanceladaEvent publicado para reserva {}", reservaId);
        
        return resultado;
    }
    
    /**
     * Valida si una reserva puede ser cancelada según el tiempo límite de la política.
     * 
     * @param reserva La reserva a validar
     * @return ResultadoCancelacion con información sobre si puede cancelarse
     */
    private ResultadoCancelacion validarTiempoLimiteCancelacion(Reserva reserva) {
        
        ResultadoCancelacion resultado = new ResultadoCancelacion();
        resultado.setPuedeSerCancelada(false);
        resultado.setReservaCancelada(false);
        
        // Obtener el primer detalle para determinar la hora de inicio
        if (reserva.getDetalles().isEmpty()) {
            resultado.setMensaje("La reserva no tiene detalles");
            return resultado;
        }
        
        DetalleReserva primerDetalle = reserva.getDetalles().get(0);
        LocalDateTime fechaHoraReserva = LocalDateTime.of(
            primerDetalle.getFechaReserva(),
            primerDetalle.getHoraInicio()
        );
        
        // Validar que la reserva no haya empezado
        LocalDateTime ahora = LocalDateTime.now();
        if (fechaHoraReserva.isBefore(ahora) || fechaHoraReserva.equals(ahora)) {
            resultado.setMensaje("No puedes cancelar una reserva que ya comenzó");
            return resultado;
        }
        
        // Obtener política de cancelación del espacio
        EspacioReservable espacio = primerDetalle.getEspacioReservable();
        PoliticaCancelacion politica = espacio.getPoliticaCancelacion();
        
        // Si NO hay política de cancelación, usar política por defecto (1 hora)
        if (politica == null) {
            LocalDateTime limiteDefault = fechaHoraReserva.minusHours(1);
            long horasRestantes = java.time.Duration.between(ahora, fechaHoraReserva).toHours();
            
            resultado.setPuedeSerCancelada(true); // ✅ Siempre permitir
            resultado.setHorasRestantes(horasRestantes);
            
            if (ahora.isAfter(limiteDefault)) {
                // Menos de 1 hora: cancelación tardía
                resultado.setPorcentajeDevolucion(0.0);
                resultado.setMensaje("Cancelación tardía (menos de 1 hora). Se generará Oferta Flash.");
            } else {
                // Más de 1 hora: devolución completa
                resultado.setPorcentajeDevolucion(100.0);
                resultado.setMensaje("Cancelación dentro del plazo");
            }
            
            return resultado;
        }
        
        // Si hay política, calcular horas restantes
        int horasAnticipacion = politica.getHorasAnticipacionMinima();
        LocalDateTime tiempoLimite = fechaHoraReserva.minusHours(horasAnticipacion);
        long horasRestantes = java.time.Duration.between(ahora, fechaHoraReserva).toHours();
        
        // SIEMPRE permitir cancelar, pero informar sobre el estado
        resultado.setPuedeSerCancelada(true); // ✅ Siempre TRUE
        resultado.setHorasRestantes(horasRestantes);
        
        // Si está fuera del plazo, será cancelación tardía (generará oferta flash)
        if (ahora.isAfter(tiempoLimite)) {
            // Cancelación tardía: no recupera el 100%, pero se genera oferta flash
            resultado.setPorcentajeDevolucion(0.0); // 0% de devolución inmediata
            resultado.setMensaje(String.format(
                "Cancelación tardía (menos de %d horas). Se generará Oferta Flash.",
                horasAnticipacion
            ));
        } else {
            // Cancelación dentro del plazo: recupera según política
            double porcentajeDevolucion = politica.getPorcentajeDevolucion();
            resultado.setPorcentajeDevolucion(porcentajeDevolucion);
            resultado.setMensaje("Cancelación dentro del plazo");
        }
        
        resultado.setPoliticaNombre(politica.getNombre());
        
        return resultado;
    }
    
    /**
     * Valida si una cancelación cumple con la política del complejo.
     * Retorna información sobre si se pierde la seña y los mensajes apropiados.
     */
    public ResultadoValidacionCancelacion validarCancelacion(Reserva reserva) {
        ResultadoValidacionCancelacion resultado = new ResultadoValidacionCancelacion();
        resultado.setPuedeCancelar(true);
        resultado.setCumplePolitica(true);
        resultado.setRequiereSenia(reserva.requirioSenia());
        resultado.setPierdeSenia(false);
        
        if (reserva.getDetalles().isEmpty()) {
            resultado.setPuedeCancelar(false);
            resultado.setMensaje("La reserva no tiene detalles");
            return resultado;
        }
        
        DetalleReserva primerDetalle = reserva.getDetalles().get(0);
        LocalDateTime fechaHoraReserva = LocalDateTime.of(
            primerDetalle.getFechaReserva(),
            primerDetalle.getHoraInicio()
        );
        
        // Validar que la reserva no haya empezado
        LocalDateTime ahora = LocalDateTime.now();
        if (fechaHoraReserva.isBefore(ahora) || fechaHoraReserva.equals(ahora)) {
            resultado.setPuedeCancelar(false);
            resultado.setMensaje("No puedes cancelar una reserva que ya comenzó");
            return resultado;
        }
        
        // Obtener política de cancelación del espacio
        EspacioReservable espacio = primerDetalle.getEspacioReservable();
        com.example.tureserva.modelo.PoliticaCancelacion politica = espacio.getPoliticaCancelacion();
        
        // Si NO hay política, usar regla por defecto (1 hora antes)
        if (politica == null) {
            LocalDateTime limiteDefault = fechaHoraReserva.minusHours(1);
            if (ahora.isAfter(limiteDefault)) {
                resultado.setCumplePolitica(false);
                if (reserva.requirioSenia()) {
                    resultado.setPierdeSenia(true);
                    resultado.setMensaje("Cancelar con menos de 1 hora de anticipación implica perder la seña pagada. El complejo retendrá el monto como compensación.");
                } else {
                    resultado.setMensaje("No puedes cancelar con menos de 1 hora de anticipación");
                }
            }
            return resultado;
        }
        
        // Si hay política, validar las horas de anticipación mínima
        int horasAnticipacion = politica.getHorasAnticipacionMinima();
        LocalDateTime tiempoLimite = fechaHoraReserva.minusHours(horasAnticipacion);
        resultado.setPoliticaNombre(politica.getNombre());
        resultado.setHorasAnticipacionRequeridas(horasAnticipacion);
        
        if (ahora.isAfter(tiempoLimite)) {
            resultado.setCumplePolitica(false);
            if (reserva.requirioSenia()) {
                resultado.setPierdeSenia(true);
                resultado.setMensaje(String.format(
                    "Cancelar con menos de %d hora(s) de anticipación implica perder la seña pagada. El complejo retendrá el monto según su política '%s'.",
                    horasAnticipacion, politica.getNombre()
                ));
            } else {
                resultado.setMensaje(String.format(
                    "No puedes cancelar con menos de %d hora(s) de anticipación (política: %s)",
                    horasAnticipacion, politica.getNombre()
                ));
            }
        }
        
        return resultado;
    }
    
    /**
     * Valida si una reprogramación cumple con la política de cancelación.
     * Similar a cancelación pero adaptado para reprogramaciones.
     */
    public ResultadoValidacionReprogramacion validarReprogramacion(Reserva reserva) {
        ResultadoValidacionReprogramacion resultado = new ResultadoValidacionReprogramacion();
        resultado.setPuedeReprogramar(true);
        resultado.setCumplePolitica(true);
        resultado.setRequiereSenia(reserva.requirioSenia());
        resultado.setPierdeSenia(false);
        
        if (reserva.getDetalles().isEmpty()) {
            resultado.setPuedeReprogramar(false);
            resultado.setMensaje("La reserva no tiene detalles");
            return resultado;
        }
        
        DetalleReserva primerDetalle = reserva.getDetalles().get(0);
        LocalDateTime fechaHoraReserva = LocalDateTime.of(
            primerDetalle.getFechaReserva(),
            primerDetalle.getHoraInicio()
        );
        
        // Validar que la reserva no haya empezado
        LocalDateTime ahora = LocalDateTime.now();
        if (fechaHoraReserva.isBefore(ahora) || fechaHoraReserva.equals(ahora)) {
            resultado.setPuedeReprogramar(false);
            resultado.setMensaje("No puedes reprogramar una reserva que ya comenzó");
            return resultado;
        }
        
        // Obtener política de cancelación del espacio
        EspacioReservable espacio = primerDetalle.getEspacioReservable();
        com.example.tureserva.modelo.PoliticaCancelacion politica = espacio.getPoliticaCancelacion();
        
        // Si NO hay política, usar regla por defecto (1 hora antes)
        if (politica == null) {
            LocalDateTime limiteDefault = fechaHoraReserva.minusHours(1);
            if (ahora.isAfter(limiteDefault)) {
                resultado.setCumplePolitica(false);
                if (reserva.requirioSenia()) {
                    resultado.setPierdeSenia(true);
                    resultado.setMensaje("Reprogramar con menos de 1 hora de anticipación implica perder la seña pagada. El complejo retendrá la seña original y deberás pagar una nueva seña para asegurar la nueva fecha y horario.");
                } else {
                    resultado.setMensaje("Se requiere al menos 1 hora de anticipación para reprogramar sin costo adicional");
                }
            }
            return resultado;
        }
        
        // Si hay política, validar las horas de anticipación mínima
        int horasAnticipacion = politica.getHorasAnticipacionMinima();
        LocalDateTime tiempoLimite = fechaHoraReserva.minusHours(horasAnticipacion);
        resultado.setPoliticaNombre(politica.getNombre());
        resultado.setHorasAnticipacionRequeridas(horasAnticipacion);
        
        if (ahora.isAfter(tiempoLimite)) {
            resultado.setCumplePolitica(false);
            if (reserva.requirioSenia()) {
                resultado.setPierdeSenia(true);
                resultado.setMensaje(String.format(
                    "Reprogramar con menos de %d hora(s) de anticipación implica perder la seña pagada. El complejo retendrá la seña original según su política '%s' y deberás pagar una nueva seña para asegurar la nueva fecha y horario.",
                    horasAnticipacion, politica.getNombre()
                ));
            } else {
                resultado.setMensaje(String.format(
                    "Se requieren al menos %d hora(s) de anticipación para reprogramar sin costo adicional (política: %s)",
                    horasAnticipacion, politica.getNombre()
                ));
            }
        }
        
        return resultado;
    }
    
    /**
     * Clase interna para representar el resultado de una validación de cancelación.
     */
    public static class ResultadoValidacionCancelacion {
        private boolean puedeCancelar;
        private boolean cumplePolitica;
        private boolean requiereSenia;
        private boolean pierdeSenia;
        private String mensaje;
        private String politicaNombre;
        private Integer horasAnticipacionRequeridas;
        
        // Getters y setters
        public boolean isPuedeCancelar() { return puedeCancelar; }
        public void setPuedeCancelar(boolean puedeCancelar) { this.puedeCancelar = puedeCancelar; }
        
        public boolean isCumplePolitica() { return cumplePolitica; }
        public void setCumplePolitica(boolean cumplePolitica) { this.cumplePolitica = cumplePolitica; }
        
        public boolean isRequiereSenia() { return requiereSenia; }
        public void setRequiereSenia(boolean requiereSenia) { this.requiereSenia = requiereSenia; }
        
        public boolean isPierdeSenia() { return pierdeSenia; }
        public void setPierdeSenia(boolean pierdeSenia) { this.pierdeSenia = pierdeSenia; }
        
        public String getMensaje() { return mensaje; }
        public void setMensaje(String mensaje) { this.mensaje = mensaje; }
        
        public String getPoliticaNombre() { return politicaNombre; }
        public void setPoliticaNombre(String politicaNombre) { this.politicaNombre = politicaNombre; }
        
        public Integer getHorasAnticipacionRequeridas() { return horasAnticipacionRequeridas; }
        public void setHorasAnticipacionRequeridas(Integer horasAnticipacionRequeridas) { this.horasAnticipacionRequeridas = horasAnticipacionRequeridas; }
    }
    
    /**
     * Clase interna para representar el resultado de una validación de reprogramación.
     */
    public static class ResultadoValidacionReprogramacion {
        private boolean puedeReprogramar;
        private boolean cumplePolitica;
        private boolean requiereSenia;
        private boolean pierdeSenia;
        private String mensaje;
        private String politicaNombre;
        private Integer horasAnticipacionRequeridas;
        
        // Getters y setters
        public boolean isPuedeReprogramar() { return puedeReprogramar; }
        public void setPuedeReprogramar(boolean puedeReprogramar) { this.puedeReprogramar = puedeReprogramar; }
        
        public boolean isCumplePolitica() { return cumplePolitica; }
        public void setCumplePolitica(boolean cumplePolitica) { this.cumplePolitica = cumplePolitica; }
        
        public boolean isRequiereSenia() { return requiereSenia; }
        public void setRequiereSenia(boolean requiereSenia) { this.requiereSenia = requiereSenia; }
        
        public boolean isPierdeSenia() { return pierdeSenia; }
        public void setPierdeSenia(boolean pierdeSenia) { this.pierdeSenia = pierdeSenia; }
        
        public String getMensaje() { return mensaje; }
        public void setMensaje(String mensaje) { this.mensaje = mensaje; }
        
        public String getPoliticaNombre() { return politicaNombre; }
        public void setPoliticaNombre(String politicaNombre) { this.politicaNombre = politicaNombre; }
        
        public Integer getHorasAnticipacionRequeridas() { return horasAnticipacionRequeridas; }
        public void setHorasAnticipacionRequeridas(Integer horasAnticipacionRequeridas) { this.horasAnticipacionRequeridas = horasAnticipacionRequeridas; }
    }
    
    /**
     * Clase interna para representar el resultado de una cancelación.
     */
    public static class ResultadoCancelacion {
        private boolean puedeSerCancelada;
        private boolean reservaCancelada;
        private String mensaje;
        private Long horasRestantes;
        private Double porcentajeDevolucion;
        private String politicaNombre;
        private OfertaFlash ofertaFlashGenerada;
        private BigDecimal montoDevolucion = BigDecimal.ZERO;
        private BigDecimal montoPenalizacion = BigDecimal.ZERO;
        
        // Getters y setters
        public boolean isPuedeSerCancelada() { return puedeSerCancelada; }
        public void setPuedeSerCancelada(boolean puedeSerCancelada) { this.puedeSerCancelada = puedeSerCancelada; }
        
        public boolean isReservaCancelada() { return reservaCancelada; }
        public void setReservaCancelada(boolean reservaCancelada) { this.reservaCancelada = reservaCancelada; }
        
        public String getMensaje() { return mensaje; }
        public void setMensaje(String mensaje) { this.mensaje = mensaje; }
        
        public Long getHorasRestantes() { return horasRestantes; }
        public void setHorasRestantes(Long horasRestantes) { this.horasRestantes = horasRestantes; }
        
        public Double getPorcentajeDevolucion() { return porcentajeDevolucion; }
        public void setPorcentajeDevolucion(Double porcentajeDevolucion) { this.porcentajeDevolucion = porcentajeDevolucion; }
        
        public String getPoliticaNombre() { return politicaNombre; }
        public void setPoliticaNombre(String politicaNombre) { this.politicaNombre = politicaNombre; }
        
        public OfertaFlash getOfertaFlashGenerada() { return ofertaFlashGenerada; }
        public void setOfertaFlashGenerada(OfertaFlash ofertaFlashGenerada) { this.ofertaFlashGenerada = ofertaFlashGenerada; }
        
        public BigDecimal getMontoDevolucion() { return montoDevolucion; }
        public void setMontoDevolucion(BigDecimal montoDevolucion) { this.montoDevolucion = montoDevolucion; }
        
        public BigDecimal getMontoPenalizacion() { return montoPenalizacion; }
        public void setMontoPenalizacion(BigDecimal montoPenalizacion) { this.montoPenalizacion = montoPenalizacion; }
    }
    
    // ==================== MÉTODOS PARA ADMINISTRADOR DE COMPLEJO ====================
    
    /**
     * Confirma una reserva pendiente (usado por admin del complejo).
     * 
     * <p><b>FASE 2 - Migrado a Eventos:</b> Publica {@link ReservaConfirmadaEvent}
     * en lugar de usar @Auditable.
     */
    @Transactional
    public void confirmarReservaPendiente(Long reservaId) {
        Reserva reserva = repositorioReserva.findById(reservaId)
            .orElseThrow(() -> new EntityNotFoundException("Reserva no encontrada con ID: " + reservaId));
        
        if (reserva.getEstado() != EstadoReserva.PENDIENTE) {
            throw new IllegalStateException("Solo se pueden confirmar reservas en estado PENDIENTE");
        }
        
        reserva.confirmar();
        repositorioReserva.save(reserva);
        
        logger.info("Reserva {} confirmada por administrador", reservaId);
        
        // FASE 2: Publicar evento de dominio
        eventPublisher.publishEvent(new ReservaConfirmadaEvent(
            this,
            reserva,
            AuditoriaContextUtil.obtenerUsuarioEmail(),
            AuditoriaContextUtil.obtenerUsuarioRol(),
            AuditoriaContextUtil.obtenerIpAddress()
        ));
        
        logger.debug("✅ Evento ReservaConfirmadaEvent publicado para reserva {}", reservaId);
    }
    
    /**
     * Finaliza una reserva confirmada registrando el pago completo (usado por admin del complejo).
     * 
     * <p><b>FASE 2 - Migrado a Eventos:</b> Publica {@link ReservaFinalizadaEvent}
     * en lugar de usar @Auditable.
     */
    @Transactional
    public void finalizarReservaConPago(Long reservaId, MetodoPago metodoPago, 
                                       String numeroComprobante, String notas, 
                                       Usuario registradoPor) {
        Reserva reserva = repositorioReserva.findById(reservaId)
            .orElseThrow(() -> new EntityNotFoundException("Reserva no encontrada con ID: " + reservaId));
        
        if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            throw new IllegalStateException("Solo se pueden finalizar reservas en estado CONFIRMADA");
        }
        
        // Validar que la reserva ya haya terminado
        if (!reserva.getDetalles().isEmpty()) {
            DetalleReserva ultimoDetalle = reserva.getDetalles().stream()
                .max((d1, d2) -> d1.getHoraFin().compareTo(d2.getHoraFin()))
                .orElseThrow(() -> new IllegalStateException("La reserva no tiene detalles"));
            
            LocalDateTime fechaHoraFin = LocalDateTime.of(
                ultimoDetalle.getFechaReserva(),
                ultimoDetalle.getHoraFin()
            );
            
            LocalDateTime ahora = LocalDateTime.now();
            
            if (ahora.isBefore(fechaHoraFin)) {
                throw new IllegalStateException("No se puede finalizar una reserva que aún no ha terminado. Termina el: " 
                    + fechaHoraFin.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            }
        }
        
        // Crear registro del pago completo
        Pago pago = new Pago();
        pago.setReserva(reserva);
        pago.setTipoPago(TipoPago.PAGO_COMPLETO);
        pago.setMetodoPago(metodoPago);
        pago.setMonto(reserva.getMontoRestante());
        pago.setFechaPago(LocalDateTime.now()); // FIX: Asignar fecha de pago para pagos manuales
        pago.setNumeroComprobante(numeroComprobante);
        pago.setNotas(notas);
        pago.setRegistradoPor(registradoPor);
        
        repositorioPago.save(pago);
        
        // Cambiar estado de la reserva
        reserva.setEstado(EstadoReserva.FINALIZADA);
        repositorioReserva.save(reserva);
        
        logger.info("Reserva {} finalizada - pago completo registrado por {} con método {}", 
                    reservaId, registradoPor.getEmail(), metodoPago);
        
        // FASE 2: Publicar evento de dominio
        eventPublisher.publishEvent(new ReservaFinalizadaEvent(
            this,
            reserva,
            metodoPago,
            numeroComprobante,
            reserva.getMontoRestante(),
            notas,
            AuditoriaContextUtil.obtenerUsuarioEmail(),
            AuditoriaContextUtil.obtenerUsuarioRol(),
            AuditoriaContextUtil.obtenerIpAddress()
        ));
        
        logger.debug("✅ Evento ReservaFinalizadaEvent publicado para reserva {}", reservaId);
    }
    
    /**
     * Finaliza una reserva confirmada (sin registro de pago - versión antigua).
     * @deprecated Usar finalizarReservaConPago() para incluir detalles del pago.
     */
    @Deprecated
    @Transactional
    public void finalizarReserva(Long reservaId) {
        Reserva reserva = repositorioReserva.findById(reservaId)
            .orElseThrow(() -> new EntityNotFoundException("Reserva no encontrada con ID: " + reservaId));
        
        if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            throw new IllegalStateException("Solo se pueden finalizar reservas en estado CONFIRMADA");
        }
        
        // Validar que la reserva ya haya terminado
        if (!reserva.getDetalles().isEmpty()) {
            DetalleReserva ultimoDetalle = reserva.getDetalles().stream()
                .max((d1, d2) -> d1.getHoraFin().compareTo(d2.getHoraFin()))
                .orElseThrow(() -> new IllegalStateException("La reserva no tiene detalles"));
            
            LocalDateTime fechaHoraFin = LocalDateTime.of(
                ultimoDetalle.getFechaReserva(),
                ultimoDetalle.getHoraFin()
            );
            
            LocalDateTime ahora = LocalDateTime.now();
            
            if (ahora.isBefore(fechaHoraFin)) {
                throw new IllegalStateException("No se puede finalizar una reserva que aún no ha terminado. Termina el: " 
                    + fechaHoraFin.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            }
        }
        
        reserva.setEstado(EstadoReserva.FINALIZADA);
        repositorioReserva.save(reserva);
        
        logger.info("Reserva {} finalizada - pago completo registrado", reservaId);
    }
    
    /**
     * Cancela una reserva por parte del administrador del complejo.
     * 
     * <p><b>FASE 2 - Migrado a Eventos:</b> Publica {@link ReservaCanceladaEvent}
     * en lugar de usar @Auditable.
     */
    @Transactional
    public void cancelarReservaPorAdmin(Long reservaId, String motivo) {
        Reserva reserva = repositorioReserva.findById(reservaId)
            .orElseThrow(() -> new EntityNotFoundException("Reserva no encontrada con ID: " + reservaId));
        
        if (reserva.estaCancelada()) {
            throw new IllegalStateException("La reserva ya está cancelada");
        }
        
        if (reserva.getEstado() == EstadoReserva.FINALIZADA) {
            throw new IllegalStateException("No se puede cancelar una reserva finalizada");
        }
        
        String motivoFinal = motivo != null && !motivo.trim().isEmpty() 
            ? motivo 
            : "Cancelada por el administrador del complejo";
        
        reserva.cancelar(motivoFinal);

        // Mantener detalles para auditoría; la lógica de disponibilidad debe ignorar reservas CANCELADAS
        repositorioReserva.save(reserva);
        
        logger.info("Reserva {} cancelada por administrador. Motivo: {}", reservaId, motivoFinal);
        
        // FASE 2: Publicar evento de dominio
        eventPublisher.publishEvent(new ReservaCanceladaEvent(
            this,
            reserva,
            motivoFinal,
            BigDecimal.ZERO,  // No hay devolución cuando es admin quien cancela
            BigDecimal.ZERO,  // No hay penalización
            false,            // No aplica "fuera de términos" para admin
            AuditoriaContextUtil.obtenerUsuarioEmail(),
            AuditoriaContextUtil.obtenerUsuarioRol(),
            AuditoriaContextUtil.obtenerIpAddress()
        ));
        
        logger.debug("✅ Evento ReservaCanceladaEvent publicado para reserva {} (admin)", reservaId);
    }
    
    /**
     * Busca reservas confirmadas que se superpongan con un rango horario específico.
     * Usado para validar disponibilidad antes de crear un pago de seña.
     * 
     * @param espacioId ID del espacio a verificar
     * @param fecha Fecha de la reserva
     * @param horaInicio Hora de inicio del rango a verificar
     * @param horaFin Hora de fin del rango a verificar
     * @return Lista de detalles de reserva que se superponen con el rango especificado
     */
    public List<DetalleReserva> buscarReservasEnRango(Long espacioId, LocalDate fecha, 
                                                       LocalTime horaInicio, LocalTime horaFin) {
        // Buscar todas las reservas confirmadas (no canceladas) para este espacio y fecha
        EspacioReservable espacio = repositorioEspacioReservable.findById(espacioId)
            .orElseThrow(() -> new EntityNotFoundException("Espacio no encontrado con ID: " + espacioId));
        
        List<DetalleReserva> reservasDelDia = repositorioDetalleReserva
            .findByEspacioReservableAndFechaReservaAndReservaEstadoNot(
                espacio, fecha, EstadoReserva.CANCELADA);
        
        // Filtrar solo las que se superponen con el rango solicitado
        return reservasDelDia.stream()
            .filter(detalle -> {
                // Verificar si hay superposición de horarios
                // Hay superposición si: horaInicio < detalle.horaFin AND horaFin > detalle.horaInicio
                return horaInicio.isBefore(detalle.getHoraFin()) && 
                       horaFin.isAfter(detalle.getHoraInicio());
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Obtiene los horarios de inicio disponibles para un espacio en una fecha específica.
     * Útil para reprogramación de reservas por alertas climáticas.
     * 
     * @param espacioId ID del espacio
     * @param fecha Fecha para la que se buscan horarios
     * @return Lista de horarios de inicio disponibles (LocalTime)
     */
    public List<LocalTime> obtenerHorariosDisponibles(Long espacioId, LocalDate fecha) {
        EspacioReservable espacio = repositorioEspacioReservable.findById(espacioId)
            .orElseThrow(() -> new EntityNotFoundException("Espacio no encontrado con ID: " + espacioId));
        
        List<IntervaloDisponible> intervalos = generarIntervalosDisponibles(espacio, fecha);
        
        return intervalos.stream()
            .map(intervalo -> intervalo.horaInicio())
            .sorted()
            .distinct()
            .collect(Collectors.toList());
    }
    
    /**
     * Crea una nueva reserva por reprogramación de una reserva existente.
     * Mantiene todos los datos de la reserva original (cliente, espacio, monto, pagos)
     * pero con nueva fecha y hora.
     * 
     * <p><b>NOTA FASE 2:</b> Este método mantiene @Auditable porque requiere el objeto
     * de resultado. Podría migrarse a eventos en una futura iteración si es necesario.
     * Por ahora no causa problemas porque el resultado es una Reserva nueva y limpia.
     * 
     * @param reservaOriginal Reserva original que se está reprogramando
     * @param nuevaFecha Nueva fecha para la reserva
     * @param nuevaHora Nueva hora de inicio
     * @return Nueva reserva creada y guardada con estado CONFIRMADA
     */
    @Transactional
    public Reserva crearReservaPorReprogramacion(Reserva reservaOriginal, 
                                                  LocalDate nuevaFecha, 
                                                  LocalTime nuevaHora) {
        logger.info("Creando nueva reserva por reprogramación de reserva {}", reservaOriginal.getId());
        
        // Validaciones iniciales
        if (nuevaFecha == null || nuevaHora == null) {
            throw new IllegalArgumentException("La fecha y hora son obligatorias para reprogramar");
        }
        
        // Cargar la reserva original con sus detalles y servicios adicionales en sesión
        Reserva reservaCargada = repositorioReserva.findByIdWithDetalles(reservaOriginal.getId())
            .orElseThrow(() -> new EntityNotFoundException("Reserva no encontrada: " + reservaOriginal.getId()));
        
        // CRÍTICO: Validar que la reserva tenga detalles
        if (reservaCargada.getDetalles() == null || reservaCargada.getDetalles().isEmpty()) {
            throw new IllegalStateException("La reserva no tiene detalles para reprogramar");
        }
        
        // Cargar servicios adicionales
        List<com.example.tureserva.modelo.DetalleServicioAdicional> servicios = 
            repositorioDetalleServicioAdicional.findByReservaIdWithServicioAdicional(reservaCargada.getId());
        
        // Agrupar servicios por detalle
        Map<Long, List<com.example.tureserva.modelo.DetalleServicioAdicional>> serviciosPorDetalle = 
            servicios.stream().collect(java.util.stream.Collectors.groupingBy(s -> s.getDetalleReserva().getId()));
        
        // Asociar servicios a cada detalle
        for (DetalleReserva det : reservaCargada.getDetalles()) {
            List<com.example.tureserva.modelo.DetalleServicioAdicional> lista = serviciosPorDetalle.get(det.getId());
            det.getServiciosAdicionales().clear();
            if (lista != null) {
                det.getServiciosAdicionales().addAll(lista);
            }
        }
        
        // Crear nueva reserva con datos de la original
        Reserva nuevaReserva = new Reserva();
        nuevaReserva.setCliente(reservaCargada.getCliente());
        nuevaReserva.setFechaReserva(nuevaFecha);
        nuevaReserva.setFechaCreacion(LocalDateTime.now());
        nuevaReserva.setEstado(EstadoReserva.CONFIRMADA);
        nuevaReserva.setMontoTotal(reservaCargada.getMontoTotal());
        nuevaReserva.setMontoSenia(reservaCargada.getMontoSenia());
        nuevaReserva.setMontoRestante(reservaCargada.getMontoRestante());
        nuevaReserva.setReservaOrigenId(reservaCargada.getId());
        nuevaReserva.setAlertaEnviada(false);
        
        // Generar nuevo código de reserva único usando servicio centralizado
        String codigo = servicioGeneradorCodigos.generarCodigoReserva(
            codigoGenerado -> repositorioReserva.existsByCodigoReserva(codigoGenerado)
        );
        nuevaReserva.setCodigoReserva(codigo);
        
        // Copiar detalles con nueva fecha/hora
        for (DetalleReserva detalleOriginal : reservaCargada.getDetalles()) {
            DetalleReserva nuevoDetalle = new DetalleReserva();
            nuevoDetalle.setReserva(nuevaReserva);
            nuevoDetalle.setEspacioReservable(detalleOriginal.getEspacioReservable());
            nuevoDetalle.setFechaReserva(nuevaFecha);
            nuevoDetalle.setHoraInicio(nuevaHora);
            
            // Calcular duración original y aplicarla
            long duracionHoras = java.time.Duration.between(
                detalleOriginal.getHoraInicio(), 
                detalleOriginal.getHoraFin()
            ).toHours();
            nuevoDetalle.setHoraFin(nuevaHora.plusHours(duracionHoras));
            
            nuevoDetalle.setPrecioPorHora(detalleOriginal.getPrecioPorHora());
            nuevoDetalle.setSubtotal(detalleOriginal.getSubtotal());
            
            nuevaReserva.getDetalles().add(nuevoDetalle);
        }
        
        // Copiar servicios adicionales si los había
        for (DetalleReserva detalleOriginal : reservaCargada.getDetalles()) {
            DetalleReserva nuevoDetalle = nuevaReserva.getDetalles().get(
                reservaCargada.getDetalles().indexOf(detalleOriginal)
            );
            
            for (com.example.tureserva.modelo.DetalleServicioAdicional servicioOriginal : 
                 detalleOriginal.getServiciosAdicionales()) {
                com.example.tureserva.modelo.DetalleServicioAdicional nuevoServicio = 
                    new com.example.tureserva.modelo.DetalleServicioAdicional();
                nuevoServicio.setDetalleReserva(nuevoDetalle);
                nuevoServicio.setServicioAdicional(servicioOriginal.getServicioAdicional());
                nuevoServicio.setCantidad(servicioOriginal.getCantidad());
                nuevoServicio.setPrecioUnitario(servicioOriginal.getPrecioUnitario());
                nuevoServicio.setSubtotal(servicioOriginal.getSubtotal());
                
                nuevoDetalle.getServiciosAdicionales().add(nuevoServicio);
            }
        }
        
        // Guardar la nueva reserva
        Reserva reservaGuardada = repositorioReserva.save(nuevaReserva);
        
        // CRÍTICO: Reasignar todos los pagos de la reserva original a la nueva
        // Esto mantiene el historial de pagos (seña, pago completo) asociados correctamente
        List<Pago> pagosOriginales = repositorioPago.findByReservaOrderByFechaPagoAsc(reservaCargada);
        if (pagosOriginales != null && !pagosOriginales.isEmpty()) {
            logger.info("Reasignando {} pago(s) de reserva {} a reserva {}", 
                    pagosOriginales.size(), 
                    reservaCargada.getCodigoReserva(), 
                    reservaGuardada.getCodigoReserva());
            
            for (Pago pago : pagosOriginales) {
                if (pago != null) {
                    pago.setReserva(reservaGuardada);
                    repositorioPago.save(pago);
                    logger.debug("Pago {} ({}) reasignado: {} → {}", 
                            pago.getId(), 
                            pago.getTipoPago(), 
                            reservaCargada.getCodigoReserva(), 
                            reservaGuardada.getCodigoReserva());
                }
            }
        }
        
        logger.info("Reserva reprogramada: original {} → nueva {} (fecha: {} → {}, hora: {} → {})",
                reservaOriginal.getCodigoReserva(),
                reservaGuardada.getCodigoReserva(),
                reservaOriginal.getFechaReserva(),
                nuevaFecha,
                reservaOriginal.getDetalles().get(0).getHoraInicio(),
                nuevaHora);
        
        return reservaGuardada;
    }
    
    /**
     * Envía el email de confirmación de una reserva existente, incluyendo el crédito aplicado si corresponde.
     * Este método recarga la reserva desde la BD para obtener todos los datos actualizados.
     * 
     * @param reserva Reserva ya creada y guardada
     * @param creditoAplicado Monto del crédito aplicado (puede ser null o ZERO si no se aplicó)
     */
    @Transactional(readOnly = true)
    public void enviarEmailConfirmacionCompleto(Reserva reserva, BigDecimal creditoAplicado) {
        try {
            // Recargar la reserva desde la BD para obtener el montoRestante actualizado
            Reserva reservaActualizada = repositorioReserva.findByIdWithDetalles(reserva.getId())
                .orElse(reserva);
            
            String nombreCliente = reservaActualizada.getCliente() != null ? reservaActualizada.getCliente().getNombre() : null;
            String destinatario = reservaActualizada.getCliente() != null ? reservaActualizada.getCliente().getEmail() : null;
            String nombreComplejo = "";
            
            if (!reservaActualizada.getDetalles().isEmpty()) {
                var d = reservaActualizada.getDetalles().get(0);
                if (d.getEspacioReservable() != null && d.getEspacioReservable().getComplejoDeportivo() != null) {
                    nombreComplejo = d.getEspacioReservable().getComplejoDeportivo().getNombre_complejo();
                }
            }

            // DEBUG: Ver valores de la entidad Reserva antes de crear el DTO
            logger.info("DEBUG ENTITY - Reserva {} antes de crear DTO: montoTotal={}, montoSenia={}, montoRestante={}, creditoAplicado={}, requirioSenia={}",
                reservaActualizada.getCodigoReserva(), reservaActualizada.getMontoTotal(), 
                reservaActualizada.getMontoSenia(), reservaActualizada.getMontoRestante(), 
                reservaActualizada.getCreditoAplicado(), reservaActualizada.requirioSenia());

            com.example.tureserva.servicio.dto.EmailReservaDTO dto = new com.example.tureserva.servicio.dto.EmailReservaDTO(
                nombreCliente,
                reservaActualizada.getCodigoReserva(),
                reservaActualizada.getFechaReserva(),
                nombreComplejo,
                reservaActualizada.getMontoTotal()
            );

            // Agregar información de seña y subtotales
            dto.setSubtotalEspacios(reservaActualizada.calcularSubtotalEspacios());
            dto.setSubtotalServicios(reservaActualizada.calcularSubtotalServicios());
            dto.setMontoSenia(reservaActualizada.getMontoSenia());
            dto.setMontoRestante(reservaActualizada.getMontoRestante());
            dto.setRequirioSenia(reservaActualizada.requirioSenia());
            
            // Agregar crédito aplicado si existe
            logger.info("DEBUG EMAIL - creditoAplicado recibido: {} (null? {})", 
                creditoAplicado, creditoAplicado == null);
            if (creditoAplicado != null && creditoAplicado.compareTo(BigDecimal.ZERO) > 0) {
                dto.setCreditoAplicado(creditoAplicado);
                logger.info("DEBUG EMAIL - creditoAplicado seteado en DTO: {}", creditoAplicado);
            } else {
                logger.warn("DEBUG EMAIL - creditoAplicado NO seteado en DTO (null o <= 0)");
            }

            // Llenar detalles y servicios adicionales
            java.util.List<com.example.tureserva.servicio.dto.EmailDetalleDTO> detallesDto = new java.util.ArrayList<>();
            java.util.Set<String> politicas = new java.util.HashSet<>();

            for (DetalleReserva det : reservaActualizada.getDetalles()) {
                com.example.tureserva.servicio.dto.EmailDetalleDTO detDto = new com.example.tureserva.servicio.dto.EmailDetalleDTO();
                detDto.setId(det.getId());
                detDto.setEspacioNombre(det.getEspacioReservable() != null ? det.getEspacioReservable().getNombre() : "");
                detDto.setFecha(det.getFechaReserva());
                detDto.setHoraInicio(det.getHoraInicio());
                detDto.setHoraFin(det.getHoraFin());
                detDto.setDuracionHoras(det.getDuracionHoras());
                detDto.setPrecioPorHora(det.getPrecioPorHora());
                detDto.setSubtotal(det.getSubtotal());

                // Politica de cancelación del espacio (si aplica)
                if (det.getEspacioReservable() != null && det.getEspacioReservable().getPoliticaCancelacion() != null) {
                    PoliticaCancelacion pc = det.getEspacioReservable().getPoliticaCancelacion();
                    detDto.setPoliticaCancelacionNombre(pc.getNombre());
                    detDto.setPoliticaHorasAnticipacion(pc.getHorasAnticipacionMinima());
                    detDto.setPoliticaPorcentajeDevolucion(pc.getPorcentajeDevolucion());
                    politicas.add(pc.getNombre() + " (" + pc.getHorasAnticipacionMinima() + "h antes, " + pc.getPorcentajeDevolucion() + "% devolución)");
                }

                // Servicios adicionales asociados al detalle
                if (det.getServiciosAdicionales() != null) {
                    for (DetalleServicioAdicional s : det.getServiciosAdicionales()) {
                        var svc = s.getServicioAdicional();
                        com.example.tureserva.servicio.dto.EmailServicioAdicionalDTO svcDto = new com.example.tureserva.servicio.dto.EmailServicioAdicionalDTO();
                        svcDto.setId(svc != null ? svc.getId() : null);
                        svcDto.setNombre(svc != null ? svc.getNombre() : "");
                        svcDto.setCantidad(s.getCantidad());
                        svcDto.setPrecioUnitario(s.getPrecioUnitario());
                        svcDto.setSubtotal(s.getSubtotal());
                        detDto.getServicios().add(svcDto);
                    }
                }

                detallesDto.add(detDto);
            }

            dto.setDetalles(detallesDto);

            // Recordatorios
            dto.getRecordatorios().add("Por favor presentarse 10 minutos antes del horario reservado.");
            if (creditoAplicado != null && creditoAplicado.compareTo(BigDecimal.ZERO) > 0) {
                dto.getRecordatorios().add("💰 Se aplicó un crédito de $" + creditoAplicado + " de tu cuenta corriente.");
            }
            
            if (!politicas.isEmpty()) {
                dto.setPoliticaCancelacionResumen(String.join("; ", politicas));
            } else {
                dto.setPoliticaCancelacionResumen("Política de cancelación por defecto: reembolso completo si cancela con al menos 1 hora de anticipación.");
            }

            servicioEmail.enviarConfirmacionReserva(dto, destinatario);
            
            if (creditoAplicado != null && creditoAplicado.compareTo(BigDecimal.ZERO) > 0) {
                logger.info("Email de confirmación enviado con crédito aplicado ({}) y monto restante actualizado ({}) para reserva {}", 
                    creditoAplicado, reservaActualizada.getMontoRestante(), reservaActualizada.getCodigoReserva());
            } else {
                logger.info("Email de confirmación enviado para reserva {} - Monto restante: {}", 
                    reservaActualizada.getCodigoReserva(), reservaActualizada.getMontoRestante());
            }
            
        } catch (Exception e) {
            logger.error("Error al enviar email de confirmación para reserva {}: {}", 
                reserva.getCodigoReserva(), e.getMessage(), e);
        }
    }
}