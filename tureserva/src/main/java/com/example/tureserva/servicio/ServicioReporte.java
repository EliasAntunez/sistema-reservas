package com.example.tureserva.servicio;

import com.example.tureserva.repositorio.OcupacionHorariaDTO;
import com.example.tureserva.repositorio.RepositorioReserva;
import com.example.tureserva.servicio.dto.OcupacionMatrixDTO;
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
     * Retorna filas en el orden de horas: 8..23,0
     */
    public OcupacionMatrixDTO obtenerOcupacionMatrix(Long complejoId, LocalDate inicio, LocalDate fin) {
        List<OcupacionHorariaDTO> raw = repo.obtenerOcupacionHoraria(complejoId, inicio, fin);

        // Logging diagnóstico
        try {
            log.info("obtenerOcupacionMatrix - params: complejoId={}, inicio={}, fin={}", complejoId, inicio, fin);
            if (raw == null) {
                log.info("obtenerOcupacionMatrix - raw result is null");
            } else {
                log.info("obtenerOcupacionMatrix - raw size={}", raw.size());
                int i = 0;
                for (OcupacionHorariaDTO r : raw) {
                    if (r == null) {
                        log.debug("raw[{}] = null", i++);
                    } else {
                        log.debug("raw[{}] = day={}, hour={}, count={}", i++, r.getDayOfWeek(), r.getHour(), r.getCount());
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Error al loggear raw de ocupacion: {}", ex.getMessage());
        }

        long[][] mat = new long[24][7]; // [hour][dayIndex 0=Mon..6=Sun]
        long max = 0L;
        if (raw != null) {
            for (OcupacionHorariaDTO r : raw) {
                if (r == null) continue;
                Integer isoDay = r.getDayOfWeek();
                Integer hour = r.getHour();
                Long cnt = r.getCount() == null ? 0L : r.getCount();
                if (isoDay == null || hour == null) continue;
                int dayIdx = Math.max(1, isoDay) - 1;
                if (dayIdx < 0 || dayIdx > 6 || hour < 0 || hour > 23) continue;
                mat[hour][dayIdx] = cnt;
                if (cnt > max) max = cnt;
            }
        }

        // Horas en el orden solicitado: 8..23,0
        List<Integer> horas = new ArrayList<>();
        for (int h = 16; h <= 23; h++) horas.add(h);
        horas.add(0);

        List<List<Long>> matrix = new ArrayList<>();
        for (Integer h : horas) {
            List<Long> row = new ArrayList<>(7);
            for (int d = 0; d < 7; d++) {
                row.add(mat[h][d]);
            }
            matrix.add(row);
        }

        // Calcular totales por día y total general
        List<Long> totalesPorDia = new ArrayList<>();
        long totalGeneral = 0L;
        for (int d = 0; d < 7; d++) {
            long s = 0L;
            for (int r = 0; r < matrix.size(); r++) {
                s += matrix.get(r).get(d);
            }
            totalesPorDia.add(s);
            totalGeneral += s;
        }

        OcupacionMatrixDTO dto = new OcupacionMatrixDTO(horas, matrix, max);
        dto.setTotalesPorDia(totalesPorDia);
        dto.setTotalGeneral(totalGeneral);

        log.info("obtenerOcupacionMatrix - totals per day={} totalGeneral={}", totalesPorDia, totalGeneral);

        return dto;
    }
}
