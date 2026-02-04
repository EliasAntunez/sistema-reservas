package com.example.tureserva.servicio;

import com.example.tureserva.repositorio.OcupacionHorariaDTO;
import com.example.tureserva.repositorio.RepositorioReserva;
import com.example.tureserva.servicio.dto.OcupacionMatrixDTO;
import com.example.tureserva.modelo.ComplejoDeportivo;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ServicioReporte {
    private final RepositorioReserva repo;

    private static final Logger log = LoggerFactory.getLogger(ServicioReporte.class);

    public ServicioReporte(RepositorioReserva repo) {
        this.repo = repo;
    }

    /**
     * Construye una matriz de ocupación para rango de fechas y complejo.
     * VISTA ADAPTATIVA según periodo:
     * - ≤7 días: Muestra solo los días específicos del rango con sus fechas
     * - >7 días: Muestra Lun-Dom agregando todos los días de cada tipo
     * 
     * HORARIOS:
     * - Por defecto: 16:00 a 00:00 (horario tradicional de reservas deportivas)
     * - Filtros avanzados: Si horaInicio/horaFin están especificados, usa esos valores
     */
    public OcupacionMatrixDTO obtenerOcupacionMatrix(Long complejoId, LocalDate inicio, LocalDate fin, 
                                                      List<ComplejoDeportivo> complejosPermitidos,
                                                      Integer horaInicio, Integer horaFin) {
        
        // ========================================================================
        // HORARIOS: Por defecto o personalizados
        // ========================================================================
        Integer horaMinima;
        Integer horaMaxima;
        
        if (horaInicio != null && horaFin != null) {
            // CASO 1: Usuario especificó horarios en filtros avanzados
            horaMinima = horaInicio;
            horaMaxima = horaFin;
            log.info("Usando horarios personalizados: {}:00 a {}:00", horaMinima, horaMaxima);
        } else {
            // CASO 2: Por defecto - 16:00 a 00:00 (horario tradicional)
            horaMinima = 16;
            horaMaxima = 0; // 0 = medianoche
            log.info("Usando horarios por defecto: 16:00 a 00:00");
        }
        
        // Obtener datos de ocupación
        List<OcupacionHorariaDTO> raw = repo.obtenerOcupacionHoraria(complejoId, inicio, fin);
        
        // Filtrar datos por el rango horario detectado
        List<OcupacionHorariaDTO> rawFiltrado = filtrarPorRangoHorario(raw, horaMinima, horaMaxima);

        // Calcular días del periodo
        long diasPeriodo = java.time.temporal.ChronoUnit.DAYS.between(inicio, fin) + 1;
        
        log.info("obtenerOcupacionMatrix - complejoId={}, inicio={}, fin={}, diasPeriodo={}", 
            complejoId, inicio, fin, diasPeriodo);

        // ========================================================================
        // VISTA ADAPTATIVA: Elegir estrategia según tamaño del periodo
        // ========================================================================
        if (diasPeriodo <= 7) {
            return construirVistaEspecifica(rawFiltrado, inicio, fin, complejoId, horaMinima, horaMaxima);
        } else {
            return construirVistaAgregadaPorDiaSemana(rawFiltrado, inicio, fin, complejoId, horaMinima, horaMaxima);
        }
    }
    
    /**
     * Filtra los datos de ocupación por el rango horario especificado.
     * Permite manejar rangos que cruzan medianoche (ej: 20:00 a 02:00)
     */
    private List<OcupacionHorariaDTO> filtrarPorRangoHorario(List<OcupacionHorariaDTO> raw, 
                                                              Integer horaMin, Integer horaMax) {
        if (raw == null) return new ArrayList<>();
        
        List<OcupacionHorariaDTO> filtrado = new ArrayList<>();
        
        for (OcupacionHorariaDTO dto : raw) {
            Integer hora = dto.getHour();
            if (hora == null) continue;
            
            boolean dentroRango;
            
            if (horaMax < horaMin) {
                // Rango que cruza medianoche (ej: 20:00 a 02:00)
                dentroRango = (hora >= horaMin) || (hora <= horaMax);
            } else {
                // Rango normal (ej: 08:00 a 23:00)
                dentroRango = (hora >= horaMin) && (hora <= horaMax);
            }
            
            if (dentroRango) {
                filtrado.add(dto);
            }
        }
        
        log.debug("Filtrado horario: {} registros → {} registros (rango: {}:00-{}:00)", 
            raw.size(), filtrado.size(), horaMin, horaMax);
        
        return filtrado;
    }

    /**
     * VISTA ESPECÍFICA: Muestra solo los días del rango con fechas exactas (≤7 días)
     * Columnas: "Lun 03/02", "Mar 04/02", "Mié 05/02"...
     */
    private OcupacionMatrixDTO construirVistaEspecifica(List<OcupacionHorariaDTO> raw, 
                                                         LocalDate inicio, LocalDate fin,
                                                         Long complejoId,
                                                         Integer horaMin, Integer horaMax) {
        log.info("Usando VISTA ESPECÍFICA para periodo corto (≤7 días)");
        
        // Mapear fecha → índice de columna
        List<LocalDate> fechas = new ArrayList<>();
        LocalDate fecha = inicio;
        while (!fecha.isAfter(fin)) {
            fechas.add(fecha);
            fecha = fecha.plusDays(1);
        }
        
        int numDias = fechas.size();
        long[][] mat = new long[24][numDias]; // [hour][dayIndex]
        long max = 0L;
        
        if (raw != null) {
            for (OcupacionHorariaDTO r : raw) {
                if (r == null || r.getFecha() == null) continue;
                LocalDate diaReserva = r.getFecha();
                Integer hour = r.getHour();
                Long cnt = r.getCount() == null ? 0L : r.getCount();
                if (hour == null) continue;
                
                // Buscar índice de esta fecha
                int dayIdx = -1;
                for (int i = 0; i < fechas.size(); i++) {
                    if (fechas.get(i).equals(diaReserva)) {
                        dayIdx = i;
                        break;
                    }
                }
                
                if (dayIdx >= 0 && dayIdx < numDias && hour >= 0 && hour <= 23) {
                    mat[hour][dayIdx] = cnt;
                    if (cnt > max) max = cnt;
                }
            }
        }
        
        // Generar lista de horas DINÁMICA según el rango detectado
        List<Integer> horas = generarListaHoras(horaMin, horaMax);
        
        List<List<Long>> matrix = new ArrayList<>();
        for (Integer h : horas) {
            List<Long> row = new ArrayList<>(numDias);
            for (int d = 0; d < numDias; d++) {
                row.add(mat[h][d]);
            }
            matrix.add(row);
        }
        
        // Nombres de columnas: día de semana + fecha (ej: "Lun 03/02")
        String[] nombresDiasSemana = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
        List<String> nombresColumnas = new ArrayList<>();
        for (LocalDate f : fechas) {
            int diaSemana = f.getDayOfWeek().getValue(); // 1=Lun, 7=Dom
            String nombreDia = nombresDiasSemana[diaSemana - 1];
            String fechaFormato = f.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
            nombresColumnas.add(nombreDia + " " + fechaFormato);
        }
        
        // Calcular totales
        List<Long> totalesPorDia = calcularTotalesPorColumna(matrix, numDias);
        List<Long> totalesPorHora = calcularTotalesPorFila(matrix);
        long totalGeneral = totalesPorDia.stream().mapToLong(Long::longValue).sum();
        
        OcupacionMatrixDTO dto = new OcupacionMatrixDTO(horas, matrix, max);
        dto.setNombresColumnas(nombresColumnas);
        dto.setTipoVista("especifica");
        dto.setTotalesPorDia(totalesPorDia);
        dto.setTotalesPorHora(totalesPorHora);
        dto.setTotalGeneral(totalGeneral);
        dto.setAnalisisHorarios(analizarPatronesOcupacion(matrix, horas, totalesPorHora, totalesPorDia, nombresColumnas));
        
        return dto;
    }

    /**
     * VISTA AGREGADA: Muestra Lun-Dom agregando todos los días de cada tipo (>7 días)
     * Columnas: "Lunes", "Martes", "Miércoles"...
     * Cada celda SUMA todas las ocurrencias de ese día+hora en el periodo
     */
    private OcupacionMatrixDTO construirVistaAgregadaPorDiaSemana(List<OcupacionHorariaDTO> raw,
                                                                    LocalDate inicio, LocalDate fin,
                                                                    Long complejoId,
                                                                    Integer horaMin, Integer horaMax) {
        log.info("Usando VISTA AGREGADA por día de semana (>7 días)");
        
        long[][] mat = new long[24][7]; // [hour][dayIndex 0=Lun..6=Dom]
        long max = 0L;
        
        if (raw != null) {
            for (OcupacionHorariaDTO r : raw) {
                if (r == null) continue;
                Integer isoDay = r.getDayOfWeek(); // 1=Lun, 7=Dom
                Integer hour = r.getHour();
                Long cnt = r.getCount() == null ? 0L : r.getCount();
                if (isoDay == null || hour == null) continue;
                
                int dayIdx = Math.max(1, isoDay) - 1; // 0=Lun, 6=Dom
                if (dayIdx < 0 || dayIdx > 6 || hour < 0 || hour > 23) continue;
                
                // SUMAR porque agrega múltiples ocurrencias del mismo día de la semana
                mat[hour][dayIdx] += cnt;
                if (mat[hour][dayIdx] > max) max = mat[hour][dayIdx];
            }
        }
        
        // Generar lista de horas DINÁMICA según el rango detectado
        List<Integer> horas = generarListaHoras(horaMin, horaMax);
        
        List<List<Long>> matrix = new ArrayList<>();
        for (Integer h : horas) {
            List<Long> row = new ArrayList<>(7);
            for (int d = 0; d < 7; d++) {
                row.add(mat[h][d]);
            }
            matrix.add(row);
        }
        
        // Nombres de columnas: días de la semana completos
        List<String> nombresColumnas = java.util.Arrays.asList(
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
        );
        
        // Calcular totales
        List<Long> totalesPorDia = calcularTotalesPorColumna(matrix, 7);
        List<Long> totalesPorHora = calcularTotalesPorFila(matrix);
        long totalGeneral = totalesPorDia.stream().mapToLong(Long::longValue).sum();
        
        OcupacionMatrixDTO dto = new OcupacionMatrixDTO(horas, matrix, max);
        dto.setNombresColumnas(nombresColumnas);
        dto.setTipoVista("agregada");
        dto.setTotalesPorDia(totalesPorDia);
        dto.setTotalesPorHora(totalesPorHora);
        dto.setTotalGeneral(totalGeneral);
        dto.setAnalisisHorarios(analizarPatronesOcupacion(matrix, horas, totalesPorHora, totalesPorDia, nombresColumnas));
        
        return dto;
    }
    
    /**
     * Genera una lista de horas para mostrar en el reporte según el rango especificado.
     * Maneja correctamente rangos que cruzan medianoche (ej: 20:00 a 02:00)
     */
    private List<Integer> generarListaHoras(Integer horaMin, Integer horaMax) {
        List<Integer> horas = new ArrayList<>();
        
        if (horaMax < horaMin) {
            // Rango que cruza medianoche (ej: 20:00 a 02:00)
            for (int h = horaMin; h <= 23; h++) {
                horas.add(h);
            }
            for (int h = 0; h <= horaMax; h++) {
                horas.add(h);
            }
        } else {
            // Rango normal (ej: 08:00 a 23:00)
            for (int h = horaMin; h <= horaMax; h++) {
                horas.add(h);
            }
        }
        
        log.debug("Lista de horas generada: {} horas ({}:00 a {}:00)", horas.size(), horaMin, horaMax);
        return horas;
    }

    // Métodos auxiliares para cálculos
    private List<Long> calcularTotalesPorColumna(List<List<Long>> matrix, int numColumnas) {
        List<Long> totales = new ArrayList<>();
        for (int col = 0; col < numColumnas; col++) {
            long suma = 0L;
            for (List<Long> row : matrix) {
                suma += row.get(col);
            }
            totales.add(suma);
        }
        return totales;
    }

    private List<Long> calcularTotalesPorFila(List<List<Long>> matrix) {
        List<Long> totales = new ArrayList<>();
        for (List<Long> row : matrix) {
            long suma = row.stream().mapToLong(Long::longValue).sum();
            totales.add(suma);
        }
        return totales;
    }

    /**
     * Analiza patrones de ocupación para detectar horarios pico y valle
     */
    private java.util.Map<String, Object> analizarPatronesOcupacion(
            List<List<Long>> matrix, List<Integer> horas, 
            List<Long> totalesPorHora, List<Long> totalesPorColumna,
            List<String> nombresColumnas) {
        
        java.util.Map<String, Object> analisis = new java.util.HashMap<>();
        
        // 1. Identificar HORARIOS PICO (top 3 horarios con más ocupación)
        List<java.util.Map.Entry<Integer, Long>> horariosConTotal = new java.util.ArrayList<>();
        for (int i = 0; i < horas.size(); i++) {
            horariosConTotal.add(new java.util.AbstractMap.SimpleEntry<>(horas.get(i), totalesPorHora.get(i)));
        }
        horariosConTotal.sort((a, b) -> Long.compare(b.getValue(), a.getValue())); // descendente
        
        List<String> horariosPico = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(3, horariosConTotal.size()); i++) {
            if (horariosConTotal.get(i).getValue() > 0) {
                horariosPico.add(String.format("%02d:00", horariosConTotal.get(i).getKey()));
            }
        }
        
        // 2. Identificar HORARIOS VALLE (bottom 3 horarios con ocupación)
        List<String> horariosValle = new java.util.ArrayList<>();
        for (int i = horariosConTotal.size() - 1; i >= Math.max(0, horariosConTotal.size() - 3); i--) {
            if (i >= 0 && horariosConTotal.get(i).getValue() >= 0) {
                horariosValle.add(String.format("%02d:00", horariosConTotal.get(i).getKey()));
            }
        }
        java.util.Collections.reverse(horariosValle);
        
        // 3. Identificar periodos/días con más/menos ocupación (adaptativo)
        List<java.util.Map.Entry<String, Long>> columnasConTotal = new java.util.ArrayList<>();
        for (int i = 0; i < nombresColumnas.size(); i++) {
            columnasConTotal.add(new java.util.AbstractMap.SimpleEntry<>(nombresColumnas.get(i), totalesPorColumna.get(i)));
        }
        columnasConTotal.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        
        String periodoMasOcupado = columnasConTotal.isEmpty() || columnasConTotal.get(0).getValue() == 0 
            ? "N/A" : columnasConTotal.get(0).getKey();
        String periodoMenosOcupado = columnasConTotal.isEmpty() || columnasConTotal.get(columnasConTotal.size() - 1).getValue() < 0
            ? "N/A" : columnasConTotal.get(columnasConTotal.size() - 1).getKey();
        
        // 4. Calcular promedio de ocupación
        long suma = totalesPorColumna.stream().mapToLong(Long::longValue).sum();
        double promedio = (horas.size() > 0 && nombresColumnas.size() > 0) 
            ? (double) suma / (horas.size() * nombresColumnas.size()) : 0.0;
        
        analisis.put("horariosPico", horariosPico);
        analisis.put("horariosValle", horariosValle);
        analisis.put("periodoMasOcupado", periodoMasOcupado);
        analisis.put("periodoMenosOcupado", periodoMenosOcupado);
        analisis.put("promedioOcupacion", String.format("%.1f", promedio));
        
        return analisis;
    }
}
