# Test de Filtros de Auditoría

## Flujo del Error

### 1. HTML (listar.html línea 86-127)
```html
<form th:action="@{/admin-complejo/auditoria/{id}(id=${complejo.id_complejo})}" method="get">
    <select name="tipoEvento" id="tipoEvento">
        <option value="">Todos los eventos</option>  <!-- 🔴 PROBLEMA: Envía "" -->
        <option th:each="tipo : ${tiposEvento}" 
                th:value="${tipo}"  <!-- CREAR_COMPLEJO, ACTUALIZAR_COMPLEJO, etc -->
                ...>
    </select>
</form>
```

### 2. Controlador (ControladorAuditoria.java línea 57)
```java
@GetMapping("/{complejoId}")
public String listarAuditoria(
    @RequestParam(required = false) TipoEvento tipoEvento,  // 🔴 Spring intenta convertir "" a enum
    ...
)
```

### 3. Posible Error
Spring intenta convertir string vacío "" al enum TipoEvento y falla con:
- `Failed to convert value of type 'java.lang.String' to required type 'com.example.tureserva.modelo.TipoEvento'`
- `IllegalArgumentException: No enum constant com.example.tureserva.modelo.TipoEvento.`

## Solución Propuesta

Cambiar el controlador para recibir String y convertir manualmente:

```java
@GetMapping("/{complejoId}")
public String listarAuditoria(
    @RequestParam(required = false) String tipoEvento,  // String en vez de enum
    ...
) {
    TipoEvento tipoEventoEnum = null;
    if (tipoEvento != null && !tipoEvento.trim().isEmpty()) {
        try {
            tipoEventoEnum = TipoEvento.valueOf(tipoEvento);
        } catch (IllegalArgumentException e) {
            log.warn("Tipo de evento inválido: {}", tipoEvento);
        }
    }
    // ... resto del código usando tipoEventoEnum
}
```
