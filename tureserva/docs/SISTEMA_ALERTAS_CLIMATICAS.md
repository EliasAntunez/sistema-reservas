# Sistema Automatizado de Alertas Climáticas

## 📋 Descripción General

Sistema completo que monitorea el clima para reservas confirmadas y notifica automáticamente a los clientes cuando se pronostican condiciones adversas, permitiéndoles mantener, reprogramar o cancelar su reserva mediante un email interactivo.

## 🏗️ Arquitectura del Sistema

### Componentes Principales

1. **Configuración de Alertas** (`ConfiguracionAlertaClima`)
   - Configuración por complejo deportivo
   - Dos estrategias de notificación
   - Umbral de probabilidad personalizable

2. **Consulta de Clima** (`ServicioOpenMeteo`)
   - Integración con Open-Meteo API v1
   - Interpretación de códigos WMO
   - Manejo robusto de errores

3. **Scheduler** (`AlertaClimaticaScheduler`)
   - Ejecución automática cada hora
   - Búsqueda inteligente de reservas
   - Envío de notificaciones

4. **Notificación por Email** (`ServicioEmail` + plantilla)
   - Email HTML responsive
   - 3 botones de acción directa
   - Información detallada del pronóstico

5. **Controlador de Acciones** (`ControladorAlertaClima`)
   - Endpoints para mantener/reprogramar/cancelar
   - Validaciones de seguridad
   - Integración con flujos existentes

## 📊 Modelo de Datos

### ConfiguracionAlertaClima

```java
@Entity
public class ConfiguracionAlertaClima {
    @OneToOne
    private ComplejoDeportivo complejo;
    
    @Enumerated(EnumType.STRING)
    private EstrategiaAlerta estrategia;  // HORAS_ANTES | HORARIO_FIJO
    
    private Integer horasAnticipacion;     // Solo para HORAS_ANTES (ej: 24)
    private LocalTime horarioFijo;         // Solo para HORARIO_FIJO (ej: 06:00)
    private Integer umbralProbabilidad;    // 0-100, default 50
    private Boolean activo;                // default true
}
```

**Validaciones:**
- Si `estrategia = HORAS_ANTES`: `horasAnticipacion` debe ser > 0
- Si `estrategia = HORARIO_FIJO`: `horarioFijo` debe ser not null
- `umbralProbabilidad` entre 0 y 100

### Reserva (campo agregado)

```java
@Column(name = "alerta_enviada")
private Boolean alertaEnviada = false;
```

Previene envío de múltiples alertas para la misma reserva.

## 🌦️ Integración con Open-Meteo API

### Endpoint
```
https://api.open-meteo.com/v1/forecast
```

### Parámetros
- `latitude`, `longitude`: Coordenadas del complejo
- `hourly`: temperature_2m, precipitation_probability, weathercode
- `timezone`: auto

### Códigos WMO de Mal Clima

El sistema considera los siguientes 24 códigos WMO como condiciones adversas:

| Código | Descripción |
|--------|-------------|
| 51, 53, 55 | Llovizna (ligera, moderada, densa) |
| 61, 63, 65 | Lluvia (ligera, moderada, fuerte) |
| 66, 67 | Lluvia helada |
| 71, 73, 75 | Nieve (ligera, moderada, fuerte) |
| 77 | Granizo |
| 80, 81, 82 | Chubascos (ligeros, moderados, violentos) |
| 85, 86 | Chubascos de nieve |
| 95, 96, 99 | Tormentas |

### Lógica de Detección

```java
boolean hayMalClima = 
    (probabilidadPrecipitacion >= umbralProbabilidad) ||
    CODIGOS_MAL_CLIMA.contains(codigoClima);
```

## ⚙️ Estrategias de Alerta

### 1. HORAS_ANTES

Envía alerta X horas antes de cada reserva.

**Ejemplo:**
- Configuración: 24 horas antes
- Reserva: 02/12/2025 a las 18:00
- Alerta: 01/12/2025 a las 18:00

**Búsqueda:**
```java
LocalDateTime fechaObjetivo = ahora.plusHours(horasAnticipacion);
// Buscar reservas en fechaObjetivo ± 30 minutos
```

### 2. HORARIO_FIJO

Envía alerta a una hora específica del día para todas las reservas de ese día.

**Ejemplo:**
- Configuración: 06:00 AM
- Reservas del día: 18:00, 19:00, 20:00
- Alerta: Todas a las 06:00 AM

**Búsqueda:**
```java
if (horaActual == horarioFijo) {
    // Buscar todas las reservas de HOY
}
```

## 📧 Estructura del Email

### Contenido
- **Header:** Alerta visual con gradiente naranja-rojo
- **Saludo:** Personalizado con nombre del cliente
- **Detalles de Reserva:**
  - Código de reserva
  - Fecha y hora
  - Espacio reservado
  - Complejo deportivo
- **Pronóstico del Clima:**
  - Condiciones (texto descriptivo)
  - Probabilidad de lluvia (badge rojo)
- **Botones de Acción:**
  - 🟢 Mantener Reserva
  - 🟡 Reprogramar
  - 🔴 Cancelar
- **Nota:** Recordatorio sobre cambios en pronóstico

### URLs de los Botones

```html
/alerta-clima/mantener/{reservaId}
/alerta-clima/reprogramar/{reservaId}
/alerta-clima/cancelar/{reservaId}
```

## 🔄 Flujo de Ejecución

### 1. Scheduler (cada hora en punto)

```
00:00 → Verificar alertas
├─ Obtener configuraciones activas
├─ Para cada configuración:
│  ├─ Validar coordenadas del complejo
│  ├─ Buscar reservas según estrategia
│  └─ Para cada reserva:
│     ├─ Consultar clima (Open-Meteo)
│     ├─ Si hay mal clima:
│     │  ├─ Enviar email
│     │  └─ Marcar alertaEnviada = true
│     └─ Si no hay mal clima: continuar
└─ Fin
```

### 2. Cliente Recibe Email

```
Cliente abre email
├─ Opción 1: Mantener Reserva
│  └─ Redirect a mis-reservas con mensaje de confirmación
├─ Opción 2: Reprogramar
│  └─ Redirect a flujo de cancelación + mensaje info
└─ Opción 3: Cancelar
   └─ Redirect a flujo de cancelación + mensaje info
```

### 3. Validaciones de Seguridad

```java
private boolean esReservaDelUsuario(Reserva reserva) {
    String emailAutenticado = auth.getName();
    Cliente clienteAutenticado = repositorioCliente.findByEmail(emailAutenticado);
    return clienteReserva.getId().equals(clienteAutenticado.getId());
}
```

## 📁 Archivos Creados

### Entidades y Enums
- `EstrategiaAlerta.java` - Enum con estrategias
- `ConfiguracionAlertaClima.java` - Entidad de configuración
- `RepositorioConfiguracionAlertaClima.java` - Repositorio

### Servicios
- `ServicioOpenMeteo.java` (219 líneas) - Integración con API
- `RespuestaClimaDTO.java` - DTO de respuesta

### Tareas Programadas
- `AlertaClimaticaScheduler.java` (176 líneas) - Scheduler principal

### Controladores
- `ControladorAlertaClima.java` (151 líneas) - Manejo de acciones

### Vistas
- `templates/email/alerta-clima.html` - Plantilla de email

### Modificaciones
- `Reserva.java` - Campo `alertaEnviada`
- `RepositorioReserva.java` - Queries para alertas
- `ServicioEmail.java` - Método `enviarAlertaClimatica()`

## 🚀 Configuración Inicial

### 1. Habilitar Scheduling

En `TureservaApplication.java`:

```java
@SpringBootApplication
@EnableScheduling  // ← Agregar esta anotación
public class TureservaApplication {
    // ...
}
```

### 2. Configurar Email (application.properties)

```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=tu-email@gmail.com
spring.mail.password=tu-app-password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

### 3. Insertar Configuración en BD

```sql
-- Ejemplo: Estrategia HORAS_ANTES (24h antes)
INSERT INTO configuracion_alerta_clima 
(complejo_id, estrategia, horas_anticipacion, umbral_probabilidad, activo)
VALUES (1, 'HORAS_ANTES', 24, 50, true);

-- Ejemplo: Estrategia HORARIO_FIJO (06:00 AM)
INSERT INTO configuracion_alerta_clima 
(complejo_id, estrategia, horario_fijo, umbral_probabilidad, activo)
VALUES (2, 'HORARIO_FIJO', '06:00:00', 60, true);
```

## 🧪 Testing Manual

### 1. Verificar Scheduler

```java
// En AlertaClimaticaScheduler.java, cambiar temporalmente:
@Scheduled(cron = "0 */5 * * * *")  // Cada 5 minutos para testing
```

### 2. Crear Reserva de Prueba

- Fecha: Mañana o pasado mañana
- Hora: Cualquiera
- Estado: CONFIRMADA
- alertaEnviada: false

### 3. Configurar Complejo

- Latitud/Longitud: Coordenadas válidas
- ConfiguracionAlertaClima: activo = true

### 4. Monitorear Logs

```bash
tail -f logs/tureserva.log | grep -i "alerta"
```

Buscar líneas como:
```
[INFO] === Iniciando verificación de alertas climáticas ===
[INFO] Procesando 1 configuraciones activas
[INFO] ¡Mal clima detectado para reserva 123! Enviando alerta...
[INFO] Alerta climática enviada exitosamente para reserva 123
```

## 🔍 Troubleshooting

### El scheduler no ejecuta

**Causa:** `@EnableScheduling` no está habilitado
**Solución:** Agregar anotación en clase principal

### Email no se envía

**Causas posibles:**
1. Configuración SMTP incorrecta
2. Gmail bloqueando app menos segura
3. Método marcado como `@Async` sin executor configurado

**Solución:** Verificar logs y configuración de `spring.mail`

### Alerta se envía múltiples veces

**Causa:** Campo `alertaEnviada` no se actualiza
**Solución:** Verificar que el scheduler ejecuta `repositorioReserva.save(reserva)`

### Open-Meteo API devuelve error

**Causa:** Límite de rate (10,000 requests/día en plan gratuito)
**Solución:** El servicio maneja el error devolviendo `hayMalClima = false`

### Coordenadas del complejo son null

**Causa:** Complejo creado antes de agregar lat/lon
**Solución:** Actualizar complejos existentes con coordenadas válidas

## 📈 Mejoras Futuras

### Funcionalidad
- [ ] Reprogramación directa desde email (sin cancelar)
- [ ] Historial de alertas enviadas
- [ ] Dashboard de estadísticas de alertas
- [ ] Notificaciones push (además de email)
- [ ] Alertas para otros eventos (temperatura extrema, viento, etc.)

### Optimización
- [ ] Cache de respuestas de Open-Meteo (1 hora)
- [ ] Batch de emails (enviar múltiples en una transacción)
- [ ] Retry con exponential backoff para API
- [ ] Health check del scheduler

### UX
- [ ] Preview del clima en la vista de reservas
- [ ] Opción de silenciar alertas por reserva
- [ ] Preferencias de notificación por cliente
- [ ] SMS para alertas críticas

## 📞 Soporte

Para cualquier duda sobre el sistema de alertas climáticas:

1. Revisar logs en `AlertaClimaticaScheduler`
2. Verificar configuración de `ConfiguracionAlertaClima` en BD
3. Testear manualmente con `ServicioOpenMeteo.consultarClima()`
4. Consultar documentación de Open-Meteo: https://open-meteo.com/en/docs

---

**Última actualización:** 30/11/2025  
**Versión:** 1.0.0  
**Estado:** ✅ Completo y compilado
