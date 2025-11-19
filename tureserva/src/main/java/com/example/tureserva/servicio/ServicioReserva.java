package com.example.tureserva.servicio;

import com.example.tureserva.modelo.*;
import com.example.tureserva.modelo.enums.EstadoReserva;
import com.example.tureserva.modelo.enums.MetodoPago;
import com.example.tureserva.modelo.enums.TipoPago;
import com.example.tureserva.repositorio.RepositorioEspacioReservable; 
import com.example.tureserva.repositorio.RepositorioDetalleReserva;
import com.example.tureserva.repositorio.RepositorioReserva;
import com.example.tureserva.repositorio.RepositorioPago;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;

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
                         EntityManager entityManager) {
        this.repositorioDetalleReserva = repositorioDetalleReserva;
        this.repositorioEspacioReservable = repositorioEspacioReservable;
        this.repositorioReserva = repositorioReserva;
        this.repositorioPago = repositorioPago;
        this.servicioServicioAdicional = servicioServicioAdicional;
        this.repositorioDetalleServicioAdicional = repositorioDetalleServicioAdicional;
        this.entityManager = entityManager;
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
            
                // Obtener reservas existentes (ignorar detalles pertenecientes a reservas CANCELADAS)
                List<DetalleReserva> reservasExistentes = repositorioDetalleReserva
                    .findByEspacioReservableAndFechaReservaAndReservaEstadoNot(espacioConectado, fecha, com.example.tureserva.modelo.enums.EstadoReserva.CANCELADA);
            
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
     * Crea una reserva desde los datos temporales y además procesa las selecciones
     * de servicios por item. El mapa `serviciosPorItem` tiene clave = índice del item
     * en la lista de `DatosReservaTemp.items` y valor = mapa (idServicio -> cantidad).
     */
    @Transactional
    public Reserva crearReservaDesdeDatosTemp(Cliente cliente, DatosReservaTemp datosReserva, Map<Integer, Map<Long, Integer>> serviciosPorItem) {
        
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

        // Generar código único
        String codigo;
        int intentos = 0;
        int maxIntentos = 10;
        
        do {
            if (intentos > 0) {
                logger.warn("Colisión de código de reserva. Reintentando... (Intento {})", intentos);
            }
            
            codigo = "RES-" + UUID.randomUUID().toString()
                                    .substring(0, 6)
                                    .toUpperCase();
            
            intentos++;
            
            if (intentos > maxIntentos) {
                throw new RuntimeException("No se pudo generar un código de reserva único después de " + maxIntentos + " intentos.");
            }
            
        } while (repositorioReserva.existsByCodigoReserva(codigo));
        
        reserva.setCodigoReserva(codigo);
        
        // Persistir (cascade guardará también los detalles)
        Reserva reservaGuardada = repositorioReserva.save(reserva);
        
        logger.info("Reserva {} creada exitosamente con {} detalles. Total: ${}", 
            reservaGuardada.getCodigoReserva(), 
            reservaGuardada.getDetalles().size(), 
            reservaGuardada.getMontoTotal());
        
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

        return Optional.of(reserva);
    }
    
    // ==================== MÉTODOS DE CANCELACIÓN ====================
    
    /**
     * Cancela una reserva validando permisos, tiempo límite y políticas.
     * 
     * @param reservaId ID de la reserva a cancelar
     * @param cliente Cliente que solicita la cancelación
     * @param motivo Motivo de la cancelación
     * @return ResultadoCancelacion con información sobre el resultado
     * @throws IllegalStateException si la reserva no puede ser cancelada
     */
    @Transactional
    public ResultadoCancelacion cancelarReserva(Long reservaId, Cliente cliente, String motivo) {
        
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
        repositorioReserva.save(reserva);
        
        logger.info("Reserva {} cancelada por cliente {}. Motivo: {}", reservaId, cliente.getId(), motivo);
        
        // 7. Retornar resultado exitoso
        resultado.setReservaCancelada(true);
        resultado.setMensaje("Reserva cancelada exitosamente");
        
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
        
        // Si NO hay política de cancelación, se puede cancelar hasta 1 hora antes
        if (politica == null) {
            LocalDateTime limiteDefault = fechaHoraReserva.minusHours(1);
            if (ahora.isAfter(limiteDefault)) {
                resultado.setMensaje("No puedes cancelar con menos de 1 hora de anticipación");
                return resultado;
            }
            
            resultado.setPuedeSerCancelada(true);
            resultado.setHorasRestantes(java.time.Duration.between(ahora, fechaHoraReserva).toHours());
            resultado.setPorcentajeDevolucion(100.0);
            return resultado;
        }
        
        // Si hay política, validar las horas de anticipación mínima
        int horasAnticipacion = politica.getHorasAnticipacionMinima();
        LocalDateTime tiempoLimite = fechaHoraReserva.minusHours(horasAnticipacion);
        
        if (ahora.isAfter(tiempoLimite)) {
            resultado.setMensaje(String.format(
                "No puedes cancelar esta reserva. Se requieren al menos %d hora(s) de anticipación",
                horasAnticipacion
            ));
            return resultado;
        }
        
        // Calcular horas restantes y porcentaje de devolución
        long horasRestantes = java.time.Duration.between(ahora, fechaHoraReserva).toHours();
        double porcentajeDevolucion = politica.getPorcentajeDevolucion();
        
        resultado.setPuedeSerCancelada(true);
        resultado.setHorasRestantes(horasRestantes);
        resultado.setPorcentajeDevolucion(porcentajeDevolucion);
        resultado.setPoliticaNombre(politica.getNombre());
        
        return resultado;
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
    }
    
    // ==================== MÉTODOS PARA ADMINISTRADOR DE COMPLEJO ====================
    
    /**
     * Confirma una reserva pendiente (usado por admin del complejo).
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
    }
    
    /**
     * Finaliza una reserva confirmada registrando el pago completo (usado por admin del complejo).
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
        pago.setNumeroComprobante(numeroComprobante);
        pago.setNotas(notas);
        pago.setRegistradoPor(registradoPor);
        
        repositorioPago.save(pago);
        
        // Cambiar estado de la reserva
        reserva.setEstado(EstadoReserva.FINALIZADA);
        repositorioReserva.save(reserva);
        
        logger.info("Reserva {} finalizada - pago completo registrado por {} con método {}", 
                    reservaId, registradoPor.getEmail(), metodoPago);
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
        
        reserva.cancelar(motivo != null && !motivo.trim().isEmpty() 
            ? motivo 
            : "Cancelada por el administrador del complejo");

        // Mantener detalles para auditoría; la lógica de disponibilidad debe ignorar reservas CANCELADAS
        repositorioReserva.save(reserva);
        
        logger.info("Reserva {} cancelada por administrador. Motivo: {}", reservaId, motivo);
    }
}