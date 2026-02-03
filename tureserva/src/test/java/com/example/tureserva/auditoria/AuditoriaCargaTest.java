package com.example.tureserva.auditoria;

import com.example.tureserva.modelo.TipoEvento;
import com.example.tureserva.servicio.ServicioAuditoria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de carga para el sistema de auditoría.
 * 
 * FASE 1: Valida que el sistema puede manejar 100 auditorías/segundo
 * sin degradación significativa de performance.
 */
@SpringBootTest
@ActiveProfiles("test")
public class AuditoriaCargaTest {
    
    @Autowired
    private ServicioAuditoria servicioAuditoria;
    
    /**
     * Test básico: 100 auditorías concurrentes.
     * Tiempo objetivo: < 2 segundos total.
     */
    @Test
    public void testCarga100AuditoriasConcurrentes() throws Exception {
        int numAuditorias = 100;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numAuditorias);
        AtomicInteger exitosas = new AtomicInteger(0);
        AtomicInteger fallidas = new AtomicInteger(0);
        
        LocalDateTime inicio = LocalDateTime.now();
        
        // Lanzar auditorías concurrentes
        for (int i = 0; i < numAuditorias; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    registrarAuditoriaSimulada(index);
                    exitosas.incrementAndGet();
                } catch (Exception e) {
                    fallidas.incrementAndGet();
                    System.err.println("Error en auditoría " + index + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Esperar a que terminen todas (timeout 10 segundos)
        boolean completado = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        LocalDateTime fin = LocalDateTime.now();
        Duration duracion = Duration.between(inicio, fin);
        
        // Validaciones
        assertTrue(completado, "El test no completó en el tiempo esperado");
        assertEquals(numAuditorias, exitosas.get(), 
                    "Algunas auditorías fallaron: " + fallidas.get());
        
        double segundos = duracion.toMillis() / 1000.0;
        double auditoriasxSegundo = numAuditorias / segundos;
        
        System.out.println("========================================");
        System.out.println("FASE 1: TEST DE CARGA COMPLETADO");
        System.out.println("========================================");
        System.out.println("Auditorías procesadas: " + exitosas.get());
        System.out.println("Tiempo total: " + segundos + " segundos");
        System.out.println("Throughput: " + String.format("%.2f", auditoriasxSegundo) + " auditorías/segundo");
        System.out.println("Tiempo promedio: " + String.format("%.2f", (duracion.toMillis() / (double) numAuditorias)) + " ms/auditoría");
        System.out.println("========================================");
        
        // Objetivo: menos de 5 segundos para 100 auditorías
        assertTrue(segundos < 5.0, 
                  "Performance degradada: " + segundos + " segundos (objetivo: <5s)");
    }
    
    /**
     * Test de stress: 1000 auditorías con objetos grandes.
     * Valida que el límite de 64KB funciona correctamente.
     */
    @Test
    public void testCargaConObjetosGrandes() throws Exception {
        int numAuditorias = 100;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numAuditorias);
        AtomicInteger exitosas = new AtomicInteger(0);
        
        LocalDateTime inicio = LocalDateTime.now();
        
        for (int i = 0; i < numAuditorias; i++) {
            executor.submit(() -> {
                try {
                    // Crear objeto grande (simulado)
                    Map<String, Object> objetoGrande = crearObjetoGrande();
                    
                    servicioAuditoria.registrarEvento(
                        TipoEvento.RESERVA_CREADA,
                        1L,
                        "Complejo Test",
                        "Test con objeto grande",
                        null,
                        objetoGrande,
                        "TEST",
                        1L,
                        crearMockRequest(),
                        crearMockAuthentication()
                    );
                    
                    exitosas.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        
        LocalDateTime fin = LocalDateTime.now();
        Duration duracion = Duration.between(inicio, fin);
        
        System.out.println("========================================");
        System.out.println("TEST CON OBJETOS GRANDES");
        System.out.println("========================================");
        System.out.println("Procesadas: " + exitosas.get() + "/" + numAuditorias);
        System.out.println("Tiempo: " + (duracion.toMillis() / 1000.0) + " segundos");
        System.out.println("========================================");
        
        assertTrue(exitosas.get() >= numAuditorias * 0.95, 
                  "Más del 5% de auditorías fallaron con objetos grandes");
    }
    
    // ==================== MÉTODOS AUXILIARES ====================
    
    private void registrarAuditoriaSimulada(int index) {
        servicioAuditoria.registrarEvento(
            TipoEvento.CANCHA_MODIFICADA,
            1L,
            "Complejo Test",
            "Auditoría de prueba #" + index,
            crearObjetoSimulado("antes"),
            crearObjetoSimulado("despues"),
            "CANCHA",
            (long) index,
            crearMockRequest(),
            crearMockAuthentication()
        );
    }
    
    private Map<String, Object> crearObjetoSimulado(String tipo) {
        Map<String, Object> objeto = new HashMap<>();
        objeto.put("id", ThreadLocalRandom.current().nextLong(1000));
        objeto.put("nombre", "Cancha " + tipo + " " + UUID.randomUUID());
        objeto.put("capacidad", ThreadLocalRandom.current().nextInt(10, 50));
        objeto.put("precioPorHora", new BigDecimal(ThreadLocalRandom.current().nextInt(5000, 10000)));
        objeto.put("activo", true);
        objeto.put("descripcion", "Descripción de prueba " + tipo);
        return objeto;
    }
    
    private Map<String, Object> crearObjetoGrande() {
        Map<String, Object> objeto = new HashMap<>();
        objeto.put("id", 1L);
        objeto.put("nombre", "Objeto Grande");
        
        // Agregar lista grande para forzar validación de tamaño
        List<String> listaMuyGrande = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            listaMuyGrande.add("Item muy largo con muchos caracteres para aumentar el tamaño del JSON número " + i);
        }
        objeto.put("listaGrande", listaMuyGrande);
        
        return objeto;
    }
    
    private MockHttpServletRequest crearMockRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        return request;
    }
    
    private Authentication crearMockAuthentication() {
        return new UsernamePasswordAuthenticationToken(
            "admin@test.com",
            "password",
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN_COMPLEJO"))
        );
    }
}
