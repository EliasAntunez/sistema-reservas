package com.example.tureserva.aspecto;

import com.example.tureserva.anotacion.Auditable;
import com.example.tureserva.servicio.ServicioAuditoria;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * Aspecto AOP que intercepta métodos anotados con @Auditable
 * y registra automáticamente eventos de auditoría.
 * 
 * Este aspecto se ejecuta alrededor del método anotado, capturando:
 * - Contexto de seguridad (usuario autenticado)
 * - Request HTTP (para obtener IP)
 * - Argumentos del método
 * - Resultado del método
 * - Datos antes/después (si se configura)
 */
@Aspect
@Component
@Slf4j
public class AspectoAuditoria {
    
    private final ServicioAuditoria servicioAuditoria;
    private final ObjectMapper objectMapper;
    private final ExpressionParser parser = new SpelExpressionParser();
    
    /**
     * FASE 1: Caches para reducir overhead de reflexión.
     * Thread-safe usando ConcurrentHashMap.
     */
    private final java.util.concurrent.ConcurrentHashMap<Class<?>, java.lang.reflect.Field> cacheRepositorios = 
        new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Class<?>, java.lang.reflect.Field> cacheCamposId = 
        new java.util.concurrent.ConcurrentHashMap<>();
    
    public AspectoAuditoria(ServicioAuditoria servicioAuditoria, ObjectMapper objectMapper) {
        this.servicioAuditoria = servicioAuditoria;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Intercepta todos los métodos anotados con @Auditable.
     */
    @Around("@annotation(com.example.tureserva.anotacion.Auditable)")
    public Object auditarMetodo(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Auditable auditable = method.getAnnotation(Auditable.class);
        
        Object resultado = null;
        Object datosAnteriores = null;
        Object datosNuevos = null;
        
        try {
            // Crear contexto de evaluación SpEL
            StandardEvaluationContext context = new StandardEvaluationContext();
            String[] parameterNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();
            
            // Registrar argumentos en el contexto
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
                context.setVariable("p" + i, args[i]); // También como p0, p1, p2...
            }
            
            // Capturar datos anteriores si se solicita (antes de ejecutar el método)
            if (auditable.capturarDatosAnteriores()) {
                Object objetoParaCapturar = null;
                
                // CASO 1: Método de ELIMINACIÓN - Recibe ID como Long
                if (esMetodoEliminacion(method.getName()) && args.length > 0 && args[0] instanceof Long) {
                    Long id = (Long) args[0];
                    objetoParaCapturar = buscarObjetoPorIdYTipo(id, joinPoint);
                    if (objetoParaCapturar != null) {
                        log.debug("✅ Capturado objeto para eliminación desde BD (ID: {})", id);
                        // Registrar en contexto SpEL con nombre inferido del tipo
                        String nombreVariable = inferirNombreVariable(objetoParaCapturar);
                        context.setVariable(nombreVariable, objetoParaCapturar);
                        log.debug("✅ Objeto registrado en contexto SpEL como #{}", nombreVariable);
                    }
                }
                // CASO 2: Método de ACTUALIZACIÓN - Recibe objeto con ID
                else if (esMetodoActualizacion(method.getName()) && args.length > 0 && tieneId(args[0])) {
                    Long id = extraerId(args[0]);
                    objetoParaCapturar = buscarObjetoOriginalDeBD(args[0], id, joinPoint);
                    if (objetoParaCapturar != null) {
                        log.debug("✅ Capturado objeto original de BD para auditoría de actualización");
                    }
                }
                // CASO 3: Método recibe ID como primer parámetro (cancelar, finalizar, etc.)
                else if (args.length > 0 && args[0] instanceof Long) {
                    Long id = (Long) args[0];
                    objetoParaCapturar = buscarObjetoPorIdYTipo(id, joinPoint);
                    if (objetoParaCapturar != null) {
                        log.debug("✅ Capturado objeto desde BD para método con ID (ID: {})", id);
                        // Registrar en contexto SpEL con nombre inferido del tipo
                        String nombreVariable = inferirNombreVariable(objetoParaCapturar);
                        context.setVariable(nombreVariable, objetoParaCapturar);
                        log.debug("✅ Objeto registrado en contexto SpEL como #{}", nombreVariable);
                    }
                }
                // CASO 4: Otros casos - usar primer argumento
                else if (args.length > 0) {
                    objetoParaCapturar = args[0];
                }
                
                if (objetoParaCapturar != null) {
                    datosAnteriores = clonarObjeto(objetoParaCapturar);
                }
            }
            
            // Ejecutar el método original
            resultado = joinPoint.proceed();
            
            // Registrar resultado en el contexto
            context.setVariable("result", resultado);
            
            // Capturar datos nuevos si se solicita
            if (auditable.capturarDatosNuevos()) {
                Object objetoNuevo = null;
                
                // CASO 1: Método de ELIMINACIÓN (baja lógica) - Recibe ID, buscar objeto modificado
                if (esMetodoEliminacion(method.getName()) && args.length > 0 && args[0] instanceof Long) {
                    Long id = (Long) args[0];
                    objetoNuevo = buscarObjetoPorIdYTipo(id, joinPoint);
                    if (objetoNuevo != null) {
                        log.debug("✅ Capturado objeto después de baja lógica (ID: {})", id);
                    }
                }
                // CASO 2: Método de CREACIÓN - Resultado es void, buscar el objeto guardado
                else if (esMetodoCreacion(method.getName()) && resultado == null && args.length > 0) {
                    // El objeto fue guardado, intentar obtenerlo de BD si tiene ID
                    Object objetoGuardado = args[0];
                    if (tieneId(objetoGuardado)) {
                        Long id = extraerId(objetoGuardado);
                        if (id != null) {
                            objetoNuevo = buscarObjetoOriginalDeBD(objetoGuardado, id, joinPoint);
                            if (objetoNuevo != null) {
                                log.debug("✅ Capturado objeto creado desde BD (ID: {})", id);
                            }
                        }
                    }
                    // Si no tiene ID o no se encontró, usar el parámetro
                    if (objetoNuevo == null) {
                        objetoNuevo = objetoGuardado;
                    }
                }
                // CASO 3: Método de ACTUALIZACIÓN - Buscar objeto modificado
                else if (esMetodoActualizacion(method.getName()) && resultado == null && args.length > 0) {
                    Object objetoModificado = args[0];
                    if (tieneId(objetoModificado)) {
                        Long id = extraerId(objetoModificado);
                        objetoNuevo = buscarObjetoOriginalDeBD(objetoModificado, id, joinPoint);
                        if (objetoNuevo == null) {
                            objetoNuevo = objetoModificado;
                        }
                    } else {
                        objetoNuevo = objetoModificado;
                    }
                }
                // CASO 4: Método recibe ID como primer parámetro - buscar objeto actualizado
                else if (args.length > 0 && args[0] instanceof Long) {
                    Long id = (Long) args[0];
                    objetoNuevo = buscarObjetoPorIdYTipo(id, joinPoint);
                    if (objetoNuevo != null) {
                        log.debug("✅ Capturado objeto después de operación con ID (ID: {})", id);
                    }
                }
                // CASO 5: El método retorna algo - usar el resultado
                else if (resultado != null) {
                    objetoNuevo = resultado;
                }
                // CASO 6: Fallback - usar primer argumento
                else if (args.length > 0) {
                    objetoNuevo = args[0];
                }
                
                if (objetoNuevo != null) {
                    datosNuevos = clonarObjeto(objetoNuevo);
                }
            }
            
            // Extraer información del evento
            Long complejoId = extraerComplejoId(auditable, context, args);
            String complejoNombre = extraerComplejoNombre(auditable, context, args);
            String descripcion = construirDescripcion(auditable, context, args);
            String recursoTipo = determinarRecursoTipo(auditable, method);
            Long recursoId = extraerRecursoId(auditable, context, args, resultado);
            
            // Obtener contexto HTTP y autenticación
            HttpServletRequest request = obtenerRequestActual();
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            // Registrar el evento
            servicioAuditoria.registrarEvento(
                auditable.evento(),
                complejoId,
                complejoNombre,
                descripcion,
                datosAnteriores,
                datosNuevos,
                recursoTipo,
                recursoId,
                request,
                authentication
            );
            
            return resultado;
            
        } catch (Exception e) {
            // Si hay error en auditoría, no fallar la operación principal
            log.error("❌ Error en aspecto de auditoría para método {}: {}", 
                     method.getName(), e.getMessage(), e);
            
            // Si no se ejecutó el método original, ejecutarlo ahora
            if (resultado == null) {
                return joinPoint.proceed();
            }
            return resultado;
        }
    }
    
    // ==================== EXTRACTORES ====================
    
    /**
     * Extrae el ID del complejo usando SpEL o detección automática.
     */
    private Long extraerComplejoId(Auditable auditable, StandardEvaluationContext context, Object[] args) {
        try {
            // Si hay expresión SpEL configurada, usarla
            if (!auditable.complejoIdExpr().isEmpty()) {
                return parser.parseExpression(auditable.complejoIdExpr()).getValue(context, Long.class);
            }
            
            // Detección automática desde el resultado si es una Reserva
            Object resultado = context.lookupVariable("result");
            if (resultado != null) {
                // Intentar acceder a detalles[0].espacioReservable.complejoDeportivo.id_complejo
                Long complejoId = buscarCampoEnObjeto(resultado, "detalles[0].espacioReservable.complejoDeportivo.id_complejo", Long.class);
                if (complejoId != null) {
                    return complejoId;
                }
            }
            
            // Detección automática: buscar en el primer argumento
            if (args.length > 0 && args[0] != null) {
                return buscarCampoEnObjeto(args[0], "complejoDeportivo.id_complejo", Long.class);
            }
            
        } catch (Exception e) {
            log.warn("⚠️ No se pudo extraer complejoId: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Extrae el nombre del complejo.
     */
    private String extraerComplejoNombre(Auditable auditable, StandardEvaluationContext context, Object[] args) {
        try {
            if (!auditable.complejoNombreExpr().isEmpty()) {
                return parser.parseExpression(auditable.complejoNombreExpr()).getValue(context, String.class);
            }
            
            // Detección automática desde el resultado si es una Reserva
            Object resultado = context.lookupVariable("result");
            if (resultado != null) {
                String complejoNombre = buscarCampoEnObjeto(resultado, "detalles[0].espacioReservable.complejoDeportivo.nombre_complejo", String.class);
                if (complejoNombre != null) {
                    return complejoNombre;
                }
            }
            
            if (args.length > 0 && args[0] != null) {
                return buscarCampoEnObjeto(args[0], "complejoDeportivo.nombre_complejo", String.class);
            }
            
        } catch (Exception e) {
            log.warn("⚠️ No se pudo extraer complejoNombre: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Extrae el ID del recurso.
     */
    private Long extraerRecursoId(Auditable auditable, StandardEvaluationContext context, 
                                   Object[] args, Object resultado) {
        try {
            if (!auditable.recursoIdExpr().isEmpty()) {
                return parser.parseExpression(auditable.recursoIdExpr()).getValue(context, Long.class);
            }
            
            // Intentar desde resultado
            if (resultado != null) {
                Long id = buscarCampoEnObjeto(resultado, "id", Long.class);
                if (id != null) return id;
            }
            
            // Intentar desde primer argumento
            if (args.length > 0 && args[0] != null) {
                return buscarCampoEnObjeto(args[0], "id", Long.class);
            }
            
        } catch (Exception e) {
            log.debug("No se pudo extraer recursoId: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Construye la descripción del evento.
     */
    private String construirDescripcion(Auditable auditable, StandardEvaluationContext context, Object[] args) {
        String descripcion = auditable.descripcion();
        
        // Reemplazar placeholders simples {0}, {1}, {2}...
        for (int i = 0; i < args.length && i < 5; i++) {
            String placeholder = "{" + i + "}";
            if (descripcion.contains(placeholder) && args[i] != null) {
                descripcion = descripcion.replace(placeholder, args[i].toString());
            }
        }
        
        return descripcion;
    }
    
    /**
     * Determina el tipo de recurso.
     */
    private String determinarRecursoTipo(Auditable auditable, Method method) {
        if (!auditable.recursoTipo().isEmpty()) {
            return auditable.recursoTipo();
        }
        
        // Inferir del nombre del método
        String nombreMetodo = method.getName().toLowerCase();
        if (nombreMetodo.contains("cancha")) return "CANCHA";
        if (nombreMetodo.contains("salon")) return "SALON";
        if (nombreMetodo.contains("reserva")) return "RESERVA";
        if (nombreMetodo.contains("horario")) return "HORARIO";
        if (nombreMetodo.contains("politica")) return "POLITICA";
        if (nombreMetodo.contains("servicio")) return "SERVICIO_ADICIONAL";
        
        return "DESCONOCIDO";
    }
    
    // ==================== UTILIDADES ====================
    
    /**
     * Busca un campo en un objeto usando navegación por puntos.
     */
    private <T> T buscarCampoEnObjeto(Object objeto, String ruta, Class<T> tipo) {
        try {
            StandardEvaluationContext ctx = new StandardEvaluationContext(objeto);
            return parser.parseExpression(ruta).getValue(ctx, tipo);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Clona un objeto (simplificado, puede mejorarse con reflection profunda).
     */
    /**
     * Clona profundamente un objeto para auditoría.
     * Implementación defensiva con múltiples estrategias de fallback.
     * 
     * MEJORAS FASE 1:
     * - Detecta y desproxyfica objetos de Hibernate
     * - Maneja lazy loading correctamente
     * - Crea DTOs simplificados cuando falla serialización completa
     * - No retorna objeto original (evita modificaciones accidentales)
     */
    private Object clonarObjeto(Object objeto) {
        if (objeto == null) {
            return null;
        }
        
        try {
            // PASO 1: Desproxyficar si es un proxy de Hibernate
            Object objetoReal = objeto;
            if (objeto instanceof org.hibernate.proxy.HibernateProxy) {
                objetoReal = org.hibernate.Hibernate.unproxy(objeto);
                log.debug("Desproxyificado: {}", objetoReal.getClass().getSimpleName());
            }
            
            // PASO 1.5: CRÍTICO - Inicializar colecciones lazy ANTES de serializar
            // Esto previene LazyInitializationException si la sesión de Hibernate se cierra
            try {
                org.hibernate.Hibernate.initialize(objetoReal);
                log.trace("Colecciones lazy inicializadas para {}", objetoReal.getClass().getSimpleName());
            } catch (Exception e) {
                // Si la sesión ya está cerrada, este initialize fallará pero no es crítico
                // El fallback de DTO simplificado manejará el caso
                log.trace("No se pudieron inicializar colecciones lazy (sesión cerrada): {}", e.getMessage());
            }
            
            // PASO 2: Intentar clonación completa por serialización JSON
            String json = objectMapper.writeValueAsString(objetoReal);
            
            // Validar tamaño antes de clonar (si es muy grande, crear DTO simplificado)
            if (json.length() > 32768) { // 32KB
                log.warn("⚠️ Objeto {} demasiado grande ({} chars), creando DTO simplificado",
                        objetoReal.getClass().getSimpleName(), json.length());
                return crearDtoSimplificado(objetoReal);
            }
            
            Object clon = objectMapper.readValue(json, objetoReal.getClass());
            log.debug("✅ Objeto clonado correctamente: {} ({} bytes)", 
                     objetoReal.getClass().getSimpleName(), json.length());
            return clon;
            
        } catch (com.fasterxml.jackson.databind.JsonMappingException e) {
            // Error común: lazy loading no inicializado
            log.debug("JsonMappingException al clonar {}, creando DTO simplificado: {}",
                     objeto.getClass().getSimpleName(), e.getMessage());
            return crearDtoSimplificado(objeto);
            
        } catch (Exception e) {
            log.warn("⚠️ No se pudo clonar objeto de tipo {}: {}. Creando DTO simplificado.",
                    objeto.getClass().getSimpleName(), e.getMessage());
            return crearDtoSimplificado(objeto);
        }
    }
    
    /**
     * Crea un DTO simplificado con campos básicos cuando falla la clonación completa.
     * Este DTO es suficiente para auditoría básica sin causar fallos.
     */
    private java.util.Map<String, Object> crearDtoSimplificado(Object objeto) {
        java.util.Map<String, Object> dto = new java.util.LinkedHashMap<>();
        
        try {
            // Desproxyficar si es necesario
            if (objeto instanceof org.hibernate.proxy.HibernateProxy) {
                objeto = org.hibernate.Hibernate.unproxy(objeto);
            }
            
            dto.put("_tipo", objeto.getClass().getSimpleName());
            dto.put("_advertencia", "DTO simplificado - algunos campos omitidos");
            
            // Extraer campos comunes usando reflexión segura
            extraerCampoSeguro(objeto, "id", dto);
            extraerCampoSeguro(objeto, "nombre", dto);
            extraerCampoSeguro(objeto, "descripcion", dto);
            extraerCampoSeguro(objeto, "activo", dto);
            extraerCampoSeguro(objeto, "capacidad", dto);
            extraerCampoSeguro(objeto, "precioPorHora", dto);
            extraerCampoSeguro(objeto, "montoTotal", dto);
            extraerCampoSeguro(objeto, "estado", dto);
            extraerCampoSeguro(objeto, "estadoOperativo", dto);
            extraerCampoSeguro(objeto, "codigoReserva", dto);
            extraerCampoSeguro(objeto, "email", dto);
            
            log.debug("✅ DTO simplificado creado con {} campos", dto.size());
            return dto;
            
        } catch (Exception e) {
            log.error("❌ Error crítico creando DTO simplificado: {}", e.getMessage());
            dto.put("_error", "No se pudo crear DTO: " + e.getMessage());
            return dto;
        }
    }
    
    /**
     * Extrae un campo de forma segura usando reflexión.
     * Prueba múltiples estrategias: getter, campo directo, ambos casos.
     */
    private void extraerCampoSeguro(Object objeto, String nombreCampo, java.util.Map<String, Object> destino) {
        try {
            // Estrategia 1: Intentar getter (getNombre, isActivo, etc.)
            String getterName = "get" + Character.toUpperCase(nombreCampo.charAt(0)) + nombreCampo.substring(1);
            try {
                java.lang.reflect.Method getter = objeto.getClass().getMethod(getterName);
                Object valor = getter.invoke(objeto);
                if (valor != null && !esColeccion(valor)) {
                    destino.put(nombreCampo, convertirValorSimple(valor));
                    return;
                }
            } catch (NoSuchMethodException e) {
                // Intentar con "is" para booleanos
                if (nombreCampo.startsWith("es") || nombreCampo.equals("activo")) {
                    String booleanGetter = "is" + Character.toUpperCase(nombreCampo.charAt(0)) + nombreCampo.substring(1);
                    try {
                        java.lang.reflect.Method getter = objeto.getClass().getMethod(booleanGetter);
                        Object valor = getter.invoke(objeto);
                        if (valor != null) {
                            destino.put(nombreCampo, valor);
                            return;
                        }
                    } catch (NoSuchMethodException ignored) {}
                }
            }
            
            // Estrategia 2: Acceso directo al campo
            java.lang.reflect.Field campo = buscarCampoEnJerarquia(objeto.getClass(), nombreCampo);
            if (campo != null) {
                campo.setAccessible(true);
                Object valor = campo.get(objeto);
                if (valor != null && !esColeccion(valor)) {
                    destino.put(nombreCampo, convertirValorSimple(valor));
                }
            }
            
        } catch (Exception e) {
            // Silencioso - el campo no existe o no es accesible
            log.trace("Campo {} no extraído: {}", nombreCampo, e.getMessage());
        }
    }
    
    /**
     * Busca un campo en la jerarquía de clases (incluyendo superclases).
     */
    private java.lang.reflect.Field buscarCampoEnJerarquia(Class<?> clazz, String nombreCampo) {
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            try {
                return currentClass.getDeclaredField(nombreCampo);
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }
    
    /**
     * Verifica si un valor es una colección o entidad relacionada.
     */
    private boolean esColeccion(Object valor) {
        return valor instanceof java.util.Collection ||
               valor instanceof java.util.Map ||
               valor.getClass().getName().startsWith("com.example.tureserva.modelo");
    }
    
    /**
     * Convierte valores complejos a String para almacenamiento simple.
     */
    private Object convertirValorSimple(Object valor) {
        if (valor instanceof java.time.LocalDate || 
            valor instanceof java.time.LocalDateTime ||
            valor instanceof java.time.LocalTime) {
            return valor.toString();
        }
        if (valor instanceof Enum) {
            return ((Enum<?>) valor).name();
        }
        return valor;
    }
    
    /**
     * Determina si un método es de actualización basándose en su nombre.
     */
    private boolean esMetodoActualizacion(String nombreMetodo) {
        return nombreMetodo != null && (
            nombreMetodo.startsWith("actualizar") || 
            nombreMetodo.startsWith("modificar") ||
            nombreMetodo.startsWith("editar") ||
            nombreMetodo.contains("Update")
        );
    }
    
    /**
     * Determina si un método es de creación basándose en su nombre.
     */
    private boolean esMetodoCreacion(String nombreMetodo) {
        return nombreMetodo != null && (
            nombreMetodo.startsWith("guardar") || 
            nombreMetodo.startsWith("crear") ||
            nombreMetodo.startsWith("agregar") ||
            nombreMetodo.startsWith("registrar") ||
            nombreMetodo.contains("Create") ||
            nombreMetodo.contains("Save") ||
            nombreMetodo.contains("Add")
        );
    }
    
    /**
     * Determina si un método es de eliminación basándose en su nombre.
     */
    private boolean esMetodoEliminacion(String nombreMetodo) {
        return nombreMetodo != null && (
            nombreMetodo.startsWith("eliminar") || 
            nombreMetodo.startsWith("borrar") ||
            nombreMetodo.startsWith("desactivar") ||
            nombreMetodo.startsWith("darDeBaja") ||
            nombreMetodo.contains("Delete") ||
            nombreMetodo.contains("Remove")
        );
    }
    
    /**
     * Verifica si un objeto tiene un campo 'id' con valor no nulo.
     */
    private boolean tieneId(Object objeto) {
        if (objeto == null) return false;
        try {
            java.lang.reflect.Field idField = buscarCampoId(objeto.getClass());
            if (idField != null) {
                idField.setAccessible(true);
                Object id = idField.get(objeto);
                return id != null;
            }
        } catch (Exception e) {
            log.trace("No se pudo verificar ID: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Extrae el ID de un objeto.
     */
    private Long extraerId(Object objeto) {
        if (objeto == null) return null;
        try {
            java.lang.reflect.Field idField = buscarCampoId(objeto.getClass());
            if (idField != null) {
                idField.setAccessible(true);
                Object id = idField.get(objeto);
                if (id instanceof Long) return (Long) id;
                if (id instanceof Integer) return ((Integer) id).longValue();
                if (id != null) return Long.parseLong(id.toString());
            }
        } catch (Exception e) {
            log.trace("No se pudo extraer ID: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Busca el campo 'id' en una clase (incluyendo superclases).
     * FASE 1: Usa cache para evitar reflexión repetitiva.
     */
    private java.lang.reflect.Field buscarCampoId(Class<?> clazz) {
        // Intentar obtener del cache primero
        java.lang.reflect.Field cachedField = cacheCamposId.get(clazz);
        if (cachedField != null) {
            return cachedField;
        }
        
        // Si no está en cache, buscar y cachear
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                java.lang.reflect.Field field = currentClass.getDeclaredField("id");
                cacheCamposId.put(clazz, field); // Guardar en cache
                log.trace("✅ Campo 'id' cacheado para {}", clazz.getSimpleName());
                return field;
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }
    
    /**
     * Intenta buscar el objeto original desde la base de datos usando el repositorio correspondiente.
     * Esto es necesario para métodos de actualización que reciben un DTO pero modifican una entidad existente.
     */
    private Object buscarObjetoOriginalDeBD(Object objetoParametro, Long id, ProceedingJoinPoint joinPoint) {
        if (id == null) return null;
        
        try {
            // Obtener el objeto target (el servicio que contiene el método)
            Object targetObject = joinPoint.getTarget();
            Class<?> targetClass = targetObject.getClass();
            
            // Buscar un repositorio inyectado en el servicio
            // Patrón común: el repositorio tiene un método findById
            for (java.lang.reflect.Field field : targetClass.getDeclaredFields()) {
                if (field.getName().startsWith("repositorio") || 
                    field.getType().getSimpleName().contains("Repositorio")) {
                    
                    field.setAccessible(true);
                    Object repositorio = field.get(targetObject);
                    
                    // Intentar llamar a findById
                    try {
                        java.lang.reflect.Method findByIdMethod = repositorio.getClass().getMethod("findById", Object.class);
                        Object optional = findByIdMethod.invoke(repositorio, id);
                        
                        // Si es Optional, extraer el valor
                        if (optional instanceof java.util.Optional) {
                            return ((java.util.Optional<?>) optional).orElse(null);
                        }
                        return optional;
                    } catch (NoSuchMethodException e) {
                        // Este repositorio no tiene findById, continuar
                    }
                }
            }
        } catch (Exception e) {
            log.trace("No se pudo buscar objeto en BD: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Busca un objeto en BD cuando solo tenemos el ID (para métodos de eliminación).
     * MEJORA CRÍTICA: Infiere el tipo de entidad desde el nombre del servicio
     * para buscar en el repositorio correcto y evitar pérdida de datos de auditoría.
     * 
     * Estrategia:
     * 1. Cache: Si ya encontramos el repo para esta clase, reusar
     * 2. Inferencia: ServicioCancha → buscar RepositorioCancha o repositorioCancha
     * 3. Fallback: Probar todos los repositorios hasta encontrar uno que devuelva el objeto
     * 
     * @param id ID de la entidad a buscar
     * @param joinPoint contexto AOP para acceder al servicio
     * @return entidad encontrada o null
     */
    private Object buscarObjetoPorIdYTipo(Long id, ProceedingJoinPoint joinPoint) {
        if (id == null) return null;
        
        try {
            // Obtener el servicio
            Object targetObject = joinPoint.getTarget();
            Class<?> targetClass = targetObject.getClass();
            
            // ESTRATEGIA 1: Intentar obtener repositorio del cache primero
            java.lang.reflect.Field repositorioCacheado = cacheRepositorios.get(targetClass);
            if (repositorioCacheado != null) {
                Object resultado = buscarConRepositorio(repositorioCacheado, targetObject, id);
                if (resultado != null) {
                    return resultado;
                }
                // Si el cache falló (ID no existe), limpiar cache y continuar
                cacheRepositorios.remove(targetClass);
                log.trace("Cache de repositorio inválido para {}, limpiando", targetClass.getSimpleName());
            }
            
            // ESTRATEGIA 2: NUEVO - Inferir nombre del repositorio desde el servicio
            // ServicioCancha → buscar "repositorioCancha" o "RepositorioCancha"
            String nombreServicio = targetClass.getSimpleName();
            if (nombreServicio.startsWith("Servicio")) {
                String tipoEntidad = nombreServicio.substring(8); // "ServicioCancha" → "Cancha"
                String nombreRepoEsperado = "repositorio" + tipoEntidad; // "repositorioCancha"
                
                log.trace("Inferido: Servicio {} debería tener repositorio '{}'", nombreServicio, nombreRepoEsperado);
                
                // Buscar el repositorio con nombre inferido
                for (java.lang.reflect.Field field : targetClass.getDeclaredFields()) {
                    String fieldName = field.getName();
                    if (fieldName.equalsIgnoreCase(nombreRepoEsperado) ||
                        fieldName.toLowerCase().equals(nombreRepoEsperado.toLowerCase())) {
                        
                        Object resultado = intentarBuscarEnRepositorio(field, targetObject, id, targetClass);
                        if (resultado != null) {
                            log.debug("✅ Encontrado por inferencia: {} → {}", nombreServicio, fieldName);
                            return resultado;
                        }
                    }
                }
            }
            
            // ESTRATEGIA 3: Fallback - Buscar en cualquier repositorio
            // Pero con mejor filtrado: evitar repositorios de otras entidades
            log.trace("Fallback: buscando en cualquier repositorio disponible");
            for (java.lang.reflect.Field field : targetClass.getDeclaredFields()) {
                String fieldName = field.getName().toLowerCase();
                
                // Filtros mejorados:
                // - Debe contener "repositorio"
                // - NO debe ser repositorio de otra entidad conocida (complejo, usuario)
                if (fieldName.contains("repositorio") && 
                    !fieldName.contains("complejo") &&
                    !fieldName.contains("usuario") &&
                    !fieldName.contains("cliente")) {
                    
                    Object resultado = intentarBuscarEnRepositorio(field, targetObject, id, targetClass);
                    if (resultado != null) {
                        log.debug("✅ Encontrado por fallback en: {}", field.getName());
                        return resultado;
                    }
                }
            }
            
            log.debug("⚠️  No se encontró objeto con ID {} en ningún repositorio de {}", 
                     id, targetClass.getSimpleName());
            
        } catch (Exception e) {
            log.warn("Error buscando objeto por ID {}: {}", id, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * NUEVO: Método helper para intentar buscar en un repositorio específico.
     * Encapsula la lógica de reflexión y manejo de errores.
     */
    private Object intentarBuscarEnRepositorio(java.lang.reflect.Field field, 
                                                Object targetObject, 
                                                Long id,
                                                Class<?> targetClass) {
        try {
            field.setAccessible(true);
            Object repositorio = field.get(targetObject);
            
            if (repositorio == null) {
                log.trace("Repositorio {} es null, omitiendo", field.getName());
                return null;
            }
            
            // Intentar findById
            java.lang.reflect.Method findByIdMethod = 
                repositorio.getClass().getMethod("findById", Object.class);
            Object optional = findByIdMethod.invoke(repositorio, id);
            
            if (optional instanceof java.util.Optional) {
                java.util.Optional<?> opt = (java.util.Optional<?>) optional;
                if (opt.isPresent()) {
                    // ÉXITO: Cachear este repositorio para futuras llamadas
                    cacheRepositorios.put(targetClass, field);
                    log.trace("✅ Repositorio cacheado: {} → {}", 
                             targetClass.getSimpleName(), field.getName());
                    return opt.get();
                }
            }
        } catch (NoSuchMethodException e) {
            log.trace("Repositorio {} no tiene findById", field.getName());
        } catch (Exception e) {
            log.trace("Error accediendo a repositorio {}: {}", field.getName(), e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Busca un objeto usando un repositorio ya identificado.
     */
    private Object buscarConRepositorio(java.lang.reflect.Field repositorioField, 
                                        Object targetObject, Long id) {
        try {
            repositorioField.setAccessible(true);
            Object repositorio = repositorioField.get(targetObject);
            
            java.lang.reflect.Method findByIdMethod = 
                repositorio.getClass().getMethod("findById", Object.class);
            Object optional = findByIdMethod.invoke(repositorio, id);
            
            if (optional instanceof java.util.Optional) {
                java.util.Optional<?> opt = (java.util.Optional<?>) optional;
                if (opt.isPresent()) {
                    log.trace("✅ Objeto encontrado usando repositorio cacheado");
                    return opt.get();
                }
            }
        } catch (Exception e) {
            log.trace("Error usando repositorio cacheado: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Infiere el nombre de la variable para usar en SpEL basándose en el tipo del objeto.
     * Por ejemplo, un objeto de tipo "Cancha" se registrará como "cancha" en el contexto.
     */
    private String inferirNombreVariable(Object objeto) {
        if (objeto == null) {
            return "objeto";
        }
        
        String nombreClase = objeto.getClass().getSimpleName();
        
        // Convertir primera letra a minúscula (Cancha -> cancha, Salon -> salon)
        if (nombreClase.length() > 0) {
            return Character.toLowerCase(nombreClase.charAt(0)) + nombreClase.substring(1);
        }
        
        return "objeto";
    }
    
    /**
     * Obtiene el HttpServletRequest actual.
     */
    private HttpServletRequest obtenerRequestActual() {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest();
        } catch (IllegalStateException e) {
            // No hay contexto de request (probablemente ejecución desde job/tarea programada)
            return null;
        }
    }
}
