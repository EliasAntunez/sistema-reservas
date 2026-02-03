package com.example.tureserva.servicio;

import com.example.tureserva.modelo.AuditoriaEvento;
import com.example.tureserva.modelo.TipoEvento;
import com.example.tureserva.repositorio.RepositorioAuditoriaEvento;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Servicio de auditoría que registra eventos significativos en el sistema.
 * Proporciona métodos para registrar y consultar eventos de auditoría.
 */
@Service
@Slf4j
public class ServicioAuditoria {
    
    /**
     * Tamaño máximo permitido para JSON de auditoría (64KB).
     * FASE 1: Prevenir almacenamiento de JSONs gigantes que degradan performance.
     */
    private static final int MAX_JSON_SIZE_BYTES = 65536; // 64KB
    private static final int MAX_JSON_SIZE_WARNING = 32768; // 32KB (warning threshold)
    
    private final RepositorioAuditoriaEvento repositorioAuditoria;
    private final ObjectMapper objectMapper;
    
    public ServicioAuditoria(RepositorioAuditoriaEvento repositorioAuditoria) {
        this.repositorioAuditoria = repositorioAuditoria;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        
        // Configurar para evitar referencias circulares y manejar Hibernate lazy loading
        this.objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Pretty print para JSON legible con indentación
        this.objectMapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
        
        // Omitir valores nulos y campos vacíos
        this.objectMapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        this.objectMapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY);
        
        // Registrar módulo de Hibernate6 para Jakarta EE
        try {
            Class.forName("com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module");
            com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module hibernateModule = 
                new com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module();
            hibernateModule.configure(com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module.Feature.FORCE_LAZY_LOADING, false);
            this.objectMapper.registerModule(hibernateModule);
        } catch (ClassNotFoundException e) {
            // Módulo de Hibernate no disponible, continuar sin él
        }
        
        // FASE 1: Configurar filtros específicos para datos sensibles
        // Solo aplicamos el filtro a las entidades que realmente tienen datos sensibles
        try {
            Class<?> usuarioClass = Class.forName("com.example.tureserva.modelo.Usuario");
            Class<?> adminComplejoClass = Class.forName("com.example.tureserva.modelo.AdministradorComplejo");
            
            this.objectMapper.addMixIn(usuarioClass, FiltroSensibleSeguridad.class);
            this.objectMapper.addMixIn(adminComplejoClass, FiltroSensibleMercadoPago.class);
            
            log.debug("✅ Filtros de seguridad configurados para Usuario y AdministradorComplejo");
        } catch (ClassNotFoundException e) {
            log.warn("⚠️ No se pudieron cargar clases para filtros de seguridad: {}", e.getMessage());
        }
    }
    
    /**
     * Filtro para ocultar campos sensibles de seguridad en Usuario.
     * Solo se aplica a la clase Usuario y sus subclases.
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({
        "contrasena", "password"
    })
    private interface FiltroSensibleSeguridad {}
    
    /**
     * Filtro para ocultar tokens de Mercado Pago en AdministradorComplejo.
     * Solo se aplica a AdministradorComplejo específicamente.
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({
        "mpAccessToken", "mpPublicKey",
        "accessToken", "refreshToken"  // Por si existen en otras clases futuras
    })
    private interface FiltroSensibleMercadoPago {}
    
    /**
     * Registra un evento de auditoría completo.
     * 
     * @param tipoEvento tipo de evento
     * @param complejoId ID del complejo afectado
     * @param complejoNombre nombre del complejo
     * @param descripcion descripción legible del evento
     * @param datosAnteriores objeto con datos antes de la modificación (null para creación)
     * @param datosNuevos objeto con datos después de la modificación (null para eliminación)
     * @param recursoTipo tipo de recurso (CANCHA, SALON, RESERVA, etc.)
     * @param recursoId ID del recurso afectado
     * @param request petición HTTP (para obtener IP)
     * @param authentication autenticación del usuario
     */
    /**
     * Registra un evento de auditoría completo con datos antes/después.
     * Este método se ejecuta en su propia transacción independiente.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarEvento(TipoEvento tipoEvento,
                                Long complejoId,
                                String complejoNombre,
                                String descripcion,
                                Object datosAnteriores,
                                Object datosNuevos,
                                String recursoTipo,
                                Long recursoId,
                                HttpServletRequest request,
                                Authentication authentication) {
        log.info("🔧 ServicioAuditoria.registrarEvento() llamado - tipoEvento={}, complejoId={}, recursoTipo={}, recursoId={}",
                 tipoEvento, complejoId, recursoTipo, recursoId);
        try {
            log.debug("Creando objeto AuditoriaEvento...");
            AuditoriaEvento evento = new AuditoriaEvento();
            evento.setFechaHora(LocalDateTime.now());
            evento.setTipoEvento(tipoEvento);
            evento.setComplejoId(complejoId);
            evento.setComplejoNombre(complejoNombre);
            evento.setDescripcion(descripcion);
            evento.setRecursoTipo(recursoTipo);
            evento.setRecursoId(recursoId);
            
            // Datos del usuario
            if (authentication != null) {
                evento.setUsuarioEmail(authentication.getName());
                evento.setUsuarioRol(obtenerRolPrincipal(authentication));
                // El nombre completo se podría obtener desde UserDetails si está disponible
            }
            
            // Dirección IP
            if (request != null) {
                evento.setIpAddress(obtenerIpReal(request));
            }
            
            // Serializar datos a JSON
            if (datosAnteriores != null) {
                evento.setDatosAnteriores(convertirAJson(datosAnteriores));
            }
            if (datosNuevos != null) {
                evento.setDatosNuevos(convertirAJson(datosNuevos));
            }
            
            log.info("💾 Intentando guardar evento en BD - tipoEvento={}, complejoId={}, recursoId={}",
                    tipoEvento, complejoId, recursoId);
            log.debug("Evento a guardar: {}", evento);
            
            AuditoriaEvento eventoGuardado = repositorioAuditoria.save(evento);
            
            log.info("✅ Evento de auditoría GUARDADO EXITOSAMENTE en BD - ID={}, tipoEvento={}, descripcion='{}', complejoId={}", 
                     eventoGuardado.getId(), tipoEvento, descripcion, complejoId);
            
        } catch (Exception e) {
            // No fallar la operación principal por un error de auditoría
            log.error("❌❌❌ ERROR CRÍTICO al registrar evento de auditoría ❌❌❌");
            log.error("Tipo de excepción: {}", e.getClass().getName());
            log.error("Mensaje: {}", e.getMessage());
            log.error("Detalles del evento que falló - tipoEvento={}, complejoId={}, recursoTipo={}, recursoId={}",
                     tipoEvento, complejoId, recursoTipo, recursoId);
            log.error("Stack trace completo:", e);
        }
    }
    
    /**
     * Sobrecarga del método registrarEvento para eventos de dominio (Fase 2).
     * Acepta información del usuario como strings en lugar de objetos HTTP/Security.
     * 
     * <p>Este método es usado por los event listeners que publican eventos de dominio,
     * donde la información del usuario ya ha sido extraída del contexto.
     * 
     * @param tipoEvento Tipo de evento de auditoría
     * @param complejoId ID del complejo involucrado
     * @param complejoNombre Nombre del complejo
     * @param recursoTipo Tipo de recurso (ej: "RESERVA", "CANCHA")
     * @param recursoId ID del recurso
     * @param datosAnteriores Estado anterior del objeto
     * @param datosNuevos Estado nuevo del objeto
     * @param descripcion Descripción del evento
     * @param usuarioEmail Email del usuario que ejecuta la acción
     * @param usuarioNombre Nombre del usuario
     * @param usuarioRol Rol del usuario (ej: "ROLE_CLIENTE")
     * @param ipAddress Dirección IP del cliente
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarEvento(TipoEvento tipoEvento,
                                Long complejoId,
                                String complejoNombre,
                                String recursoTipo,
                                Long recursoId,
                                Object datosAnteriores,
                                Object datosNuevos,
                                String descripcion,
                                String usuarioEmail,
                                String usuarioNombre,
                                String usuarioRol,
                                String ipAddress) {
        log.info("🔧 ServicioAuditoria.registrarEvento() llamado - tipoEvento={}, complejoId={}, recursoTipo={}, recursoId={}",
                 tipoEvento, complejoId, recursoTipo, recursoId);
        try {
            log.debug("Creando objeto AuditoriaEvento...");
            AuditoriaEvento evento = new AuditoriaEvento();
            evento.setFechaHora(LocalDateTime.now());
            evento.setTipoEvento(tipoEvento);
            evento.setComplejoId(complejoId);
            evento.setComplejoNombre(complejoNombre);
            evento.setDescripcion(descripcion);
            evento.setRecursoTipo(recursoTipo);
            evento.setRecursoId(recursoId);
            
            // Datos del usuario (ya extraídos del contexto)
            evento.setUsuarioEmail(usuarioEmail);
            evento.setUsuarioNombre(usuarioNombre);
            evento.setUsuarioRol(usuarioRol);
            evento.setIpAddress(ipAddress);
            
            // Serializar datos a JSON
            if (datosAnteriores != null) {
                evento.setDatosAnteriores(convertirAJson(datosAnteriores));
            }
            if (datosNuevos != null) {
                evento.setDatosNuevos(convertirAJson(datosNuevos));
            }
            
            log.info("💾 Intentando guardar evento en BD - tipoEvento={}, complejoId={}, recursoId={}",
                     tipoEvento, complejoId, recursoId);
            log.debug("Evento a guardar: {}", evento);
            
            AuditoriaEvento eventoGuardado = repositorioAuditoria.save(evento);
            
            log.info("✅ Evento de auditoría GUARDADO EXITOSAMENTE en BD - ID={}, tipoEvento={}, descripcion='{}', complejoId={}", 
                     eventoGuardado.getId(), tipoEvento, descripcion, complejoId);
            
        } catch (Exception e) {
            // No fallar la operación principal por un error de auditoría
            log.error("❌❌❌ ERROR CRÍTICO al registrar evento de auditoría ❌❌❌");
            log.error("Tipo de excepción: {}", e.getClass().getName());
            log.error("Mensaje: {}", e.getMessage());
            log.error("Detalles del evento que falló - tipoEvento={}, complejoId={}, recursoTipo={}, recursoId={}",
                     tipoEvento, complejoId, recursoTipo, recursoId);
            log.error("Stack trace completo:", e);
        }
    }
    
    /**
     * Registra un evento simple sin datos antes/después.
     * Útil para eventos que no modifican datos (ej: visualizaciones, consultas).
     */
    @Transactional
    public void registrarEventoSimple(TipoEvento tipoEvento,
                                      Long complejoId,
                                      String complejoNombre,
                                      String descripcion,
                                      String recursoTipo,
                                      Long recursoId,
                                      HttpServletRequest request,
                                      Authentication authentication) {
        registrarEvento(tipoEvento, complejoId, complejoNombre, descripcion, 
                       null, null, recursoTipo, recursoId, request, authentication);
    }
    
    /**
     * Obtiene eventos de un complejo específico con paginación.
     * CRÍTICO: Validar que el usuario autenticado tenga acceso al complejo.
     */
    public Page<AuditoriaEvento> obtenerEventosPorComplejo(Long complejoId, Pageable pageable) {
        return repositorioAuditoria.findByComplejoIdOrderByFechaHoraDesc(complejoId, pageable);
    }
    
    /**
     * Obtiene eventos de múltiples complejos (para AdminComplejo con varios complejos).
     */
    public Page<AuditoriaEvento> obtenerEventosPorComplejos(List<Long> complejosIds, Pageable pageable) {
        return repositorioAuditoria.findByComplejoIdInOrderByFechaHoraDesc(complejosIds, pageable);
    }
    
    /**
     * Búsqueda avanzada con múltiples filtros.
     * 
     * <p>Normaliza los parámetros vacíos a null para que funcionen correctamente
     * con las condiciones IS NULL en la query JPQL.
     */
    public Page<AuditoriaEvento> buscarConFiltros(Long complejoId,
                                                   TipoEvento tipoEvento,
                                                   LocalDateTime desde,
                                                   LocalDateTime hasta,
                                                   String usuarioEmail,
                                                   String textoBusqueda,
                                                   Pageable pageable) {
        log.info("🔍 SERVICIO: Entrada buscarConFiltros");
        log.info("   - complejoId: {}", complejoId);
        log.info("   - tipoEvento: {}", tipoEvento);
        log.info("   - desde: {}", desde);
        log.info("   - hasta: {}", hasta);
        log.info("   - usuarioEmail: '{}'", usuarioEmail);
        log.info("   - textoBusqueda: '{}'", textoBusqueda);
        log.info("   - pageable: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        
        try {
            // Normalizar strings vacíos a null para que funcione la query
            String usuarioEmailNormalizado = (usuarioEmail != null && !usuarioEmail.trim().isEmpty()) ? usuarioEmail.trim() : null;
            String textoBusquedaNormalizado = (textoBusqueda != null && !textoBusqueda.trim().isEmpty()) ? textoBusqueda.trim() : null;
            String tipoEventoStr = tipoEvento != null ? tipoEvento.name() : null;
            
            log.info("📝 SERVICIO: Parámetros normalizados:");
            log.info("   - tipoEventoStr: '{}'", tipoEventoStr);
            log.info("   - usuarioEmailNormalizado: '{}'", usuarioEmailNormalizado);
            log.info("   - textoBusquedaNormalizado: '{}'", textoBusquedaNormalizado);
            
            log.info("🗄️ SERVICIO: Llamando a repositorioAuditoria.buscarConFiltros...");
            Page<AuditoriaEvento> resultado = repositorioAuditoria.buscarConFiltros(
                complejoId, tipoEventoStr, desde, hasta, usuarioEmailNormalizado, textoBusquedaNormalizado, pageable);
            
            log.info("✅ SERVICIO: Query ejecutada exitosamente. Resultados: {} eventos, {} páginas totales",
                    resultado.getTotalElements(), resultado.getTotalPages());
            
            return resultado;
            
        } catch (Exception e) {
            log.error("❌ SERVICIO: ERROR en buscarConFiltros");
            log.error("   Tipo: {}", e.getClass().getName());
            log.error("   Mensaje: {}", e.getMessage());
            log.error("   Stack trace:", e);
            throw e;
        }
    }
    
    /**
     * Obtiene el historial de un recurso específico.
     */
    public Page<AuditoriaEvento> obtenerHistorialRecurso(Long complejoId,
                                                          String recursoTipo,
                                                          Long recursoId,
                                                          Pageable pageable) {
        return repositorioAuditoria.findByComplejoIdAndRecursoTipoAndRecursoIdOrderByFechaHoraDesc(
            complejoId, recursoTipo, recursoId, pageable);
    }
    
    /**
     * Obtiene estadísticas de eventos por tipo.
     */
    public long contarEventosPorTipo(Long complejoId, TipoEvento tipoEvento) {
        return repositorioAuditoria.countByComplejoIdAndTipoEvento(complejoId, tipoEvento);
    }
    
    /**
     * Elimina eventos antiguos según política de retención.
     * Debe ejecutarse mediante un job programado.
     * 
     * @param mesesRetencion cantidad de meses a retener
     * @return cantidad de registros eliminados
     */
    @Transactional
    public long limpiarEventosAntiguos(int mesesRetencion) {
        LocalDateTime fechaLimite = LocalDateTime.now().minusMonths(mesesRetencion);
        long eliminados = repositorioAuditoria.deleteByFechaHoraBefore(fechaLimite);
        
        if (eliminados > 0) {
            log.info("🧹 Limpieza de auditoría: {} eventos eliminados (anteriores a {})", 
                     eliminados, fechaLimite);
        }
        
        return eliminados;
    }
    
    // ==================== MÉTODOS AUXILIARES ====================
    
    /**
     * Convierte un objeto a JSON para almacenamiento.
     * 
     * MEJORAS FASE 1:
     * - Valida tamaño máximo (64KB)
     * - Si excede, crea DTO simplificado
     * - Loguea warnings para JSONs grandes (>32KB)
     */
    private String convertirAJson(Object objeto) {
        if (objeto == null) {
            return null;
        }
        
        try {
            // Forzar inicialización de proxies de Hibernate antes de serializar
            if (objeto instanceof org.hibernate.proxy.HibernateProxy) {
                objeto = org.hibernate.Hibernate.unproxy(objeto);
            }
            
            String json = objectMapper.writeValueAsString(objeto);
            int jsonSize = json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            
            // VALIDACIÓN DE TAMAÑO (FASE 1)
            if (jsonSize > MAX_JSON_SIZE_BYTES) {
                log.error("❌ JSON de {} excede límite: {} bytes (max: {}). Creando DTO simplificado.",
                         objeto.getClass().getSimpleName(), jsonSize, MAX_JSON_SIZE_BYTES);
                return convertirDtoSimplificadoAJson(objeto);
                
            } else if (jsonSize > MAX_JSON_SIZE_WARNING) {
                log.warn("⚠️ JSON de {} es grande: {} bytes (recomendado: <{} bytes)",
                        objeto.getClass().getSimpleName(), jsonSize, MAX_JSON_SIZE_WARNING);
            }
            
            log.debug("✅ Objeto serializado correctamente: {} ({} bytes)", 
                     objeto.getClass().getSimpleName(), jsonSize);
            return json;
            
        } catch (Exception e) {
            log.error("❌ Error al serializar objeto de tipo {}: {}", 
                     objeto.getClass().getSimpleName(), e.getMessage());
            
            // Fallback: crear DTO simplificado
            return convertirDtoSimplificadoAJson(objeto);
        }
    }
    
    /**
     * Convierte un objeto a JSON usando DTO simplificado.
     * Usado cuando el objeto original es demasiado grande o falla la serialización.
     */
    private String convertirDtoSimplificadoAJson(Object objeto) {
        try {
            Map<String, Object> dto = new java.util.HashMap<>();
            dto.put("_tipo", objeto.getClass().getSimpleName());
            dto.put("_advertencia", "DTO simplificado por tamaño o error de serialización");
            
            // Intentar extraer campos comunes
            extraerCampo(objeto, "id", dto);
            extraerCampo(objeto, "getId", dto, "id");
            extraerCampo(objeto, "nombre", dto);
            extraerCampo(objeto, "getNombre", dto, "nombre");
            extraerCampo(objeto, "capacidad", dto);
            extraerCampo(objeto, "getCapacidad", dto, "capacidad");
            extraerCampo(objeto, "precioPorHora", dto);
            extraerCampo(objeto, "getPrecioPorHora", dto, "precioPorHora");
            extraerCampo(objeto, "activo", dto);
            extraerCampo(objeto, "isActivo", dto, "activo");
            extraerCampo(objeto, "descripcion", dto);
            extraerCampo(objeto, "getDescripcion", dto, "descripcion");
            extraerCampo(objeto, "estado", dto);
            extraerCampo(objeto, "getEstado", dto, "estado");
            extraerCampo(objeto, "codigoReserva", dto);
            extraerCampo(objeto, "getCodigoReserva", dto, "codigoReserva");
            extraerCampo(objeto, "montoTotal", dto);
            extraerCampo(objeto, "getMontoTotal", dto, "montoTotal");
            
            String jsonDto = objectMapper.writeValueAsString(dto);
            log.info("✅ DTO simplificado creado: {} campos, {} bytes", 
                    dto.size(), jsonDto.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            return jsonDto;
            
        } catch (Exception e2) {
            log.error("❌ Error crítico al crear DTO simplificado: {}", e2.getMessage());
            return "{\"error\": \"No se pudo serializar\", \"tipo\": \"" + 
                   objeto.getClass().getSimpleName() + "\"}";
        }
    }
    
    /**
     * Extrae un campo de un objeto usando reflexión.
     */
    private void extraerCampo(Object objeto, String nombreMetodo, Map<String, Object> destino, String clave) {
        try {
            java.lang.reflect.Method metodo = objeto.getClass().getMethod(nombreMetodo);
            Object valor = metodo.invoke(objeto);
            if (valor != null) {
                destino.put(clave, valor);
            }
        } catch (Exception ignored) {
            // Campo no disponible
        }
    }
    
    /**
     * Extrae un campo de un objeto (versión simplificada para campos directos).
     */
    private void extraerCampo(Object objeto, String nombreCampo, Map<String, Object> destino) {
        extraerCampo(objeto, nombreCampo, destino, nombreCampo);
    }
    
    /**
     * Obtiene el rol principal del usuario autenticado.
     */
    private String obtenerRolPrincipal(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(rol -> rol.startsWith("ROLE_"))
            .findFirst()
            .orElse("ROLE_UNKNOWN");
    }
    
    /**
     * Obtiene la dirección IP real del cliente, considerando proxies.
     */
    private String obtenerIpReal(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // Si hay múltiples IPs, tomar la primera
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
}
