package com.example.tureserva.tarea;

import com.example.tureserva.repositorio.RepositorioBloqueoTemporal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Tarea programada para limpiar bloqueos temporales expirados.
 * Ejecuta cada hora para mantener la base de datos limpia.
 */
@Component
public class TareaLimpiezaBloqueos {

    private static final Logger log = LoggerFactory.getLogger(TareaLimpiezaBloqueos.class);

    private final RepositorioBloqueoTemporal repositorioBloqueoTemporal;

    public TareaLimpiezaBloqueos(RepositorioBloqueoTemporal repositorioBloqueoTemporal) {
        this.repositorioBloqueoTemporal = repositorioBloqueoTemporal;
    }

    /**
     * Ejecuta cada hora para eliminar bloqueos temporales expirados.
     * Los bloqueos expiran automáticamente a los 10 minutos de su creación,
     * pero esta tarea los elimina físicamente de la base de datos.
     */
    @Scheduled(cron = "0 0 * * * ?") // Cada hora, al minuto 0
    public void limpiarBloqueosExpirados() {
        try {
            LocalDateTime ahora = LocalDateTime.now();
            int eliminados = repositorioBloqueoTemporal.eliminarBloqueosExpirados(ahora);
            
            if (eliminados > 0) {
                log.info("Limpieza de bloqueos expirados: {} registros eliminados", eliminados);
            } else {
                log.debug("Limpieza de bloqueos expirados: no hay registros para eliminar");
            }
        } catch (Exception e) {
            log.error("Error al limpiar bloqueos expirados: {}", e.getMessage(), e);
        }
    }
}
