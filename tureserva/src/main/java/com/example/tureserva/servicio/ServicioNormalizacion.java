package com.example.tureserva.servicio;

import org.springframework.stereotype.Service;

/**
 * Servicio centralizado para normalización de datos.
 * Elimina duplicación de código en normalización de nombres, apellidos, DNI, emails, etc.
 */
@Service
public class ServicioNormalizacion {

    /**
     * Normaliza nombre o apellido a formato "Title Case"
     * Ejemplo: "juan PEREZ" → "Juan Perez"
     * Ejemplo: "maría josé garcía lópez" → "María José García López"
     * 
     * @param valor Texto a normalizar
     * @return Texto normalizado en Title Case
     */
    public String normalizarNombreApellido(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return valor;
        }
        
        String[] palabras = valor.trim().toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < palabras.length; i++) {
            String palabra = palabras[i];
            if (palabra.length() > 0) {
                // Primera letra en mayúscula, resto en minúscula
                sb.append(Character.toUpperCase(palabra.charAt(0)))
                  .append(palabra.substring(1));
            }
            // Añadir espacio entre palabras (excepto la última)
            if (i < palabras.length - 1) {
                sb.append(" ");
            }
        }
        
        return sb.toString();
    }

    /**
     * Normaliza nombre a formato UPPERCASE completo
     * Usado principalmente en SuperAdministrador
     * Ejemplo: "juan perez" → "JUAN PEREZ"
     * 
     * @param valor Texto a normalizar
     * @return Texto en MAYÚSCULAS
     */
    public String normalizarAMayusculas(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return valor;
        }
        return valor.trim().toUpperCase();
    }

    /**
     * Normaliza DNI eliminando espacios, puntos y guiones, y convirtiéndolo a mayúsculas
     * Ejemplo: "12.345.678" → "12345678"
     * Ejemplo: "12 345 678-A" → "12345678A"
     * 
     * @param dni DNI a normalizar
     * @return DNI limpio en mayúsculas
     */
    public String normalizarDni(String dni) {
        if (dni == null || dni.trim().isEmpty()) {
            return dni;
        }
        
        // Eliminar espacios, puntos y guiones
        String limpio = dni.replaceAll("[ .\\-]", "");
        
        // Convertir a mayúsculas (para manejar letras finales como 12345678A)
        return limpio.toUpperCase();
    }

    /**
     * Normaliza email a minúsculas y elimina espacios
     * Ejemplo: "Usuario@Example.COM " → "usuario@example.com"
     * 
     * @param email Email a normalizar
     * @return Email en minúsculas sin espacios
     */
    public String normalizarEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return email;
        }
        return email.trim().toLowerCase();
    }

    /**
     * Normaliza teléfono eliminando espacios, paréntesis, guiones y otros caracteres especiales
     * Ejemplo: "(011) 4567-8901" → "01145678901"
     * Ejemplo: "+54 9 11 1234-5678" → "+5491112345678"
     * 
     * @param telefono Teléfono a normalizar
     * @return Teléfono limpio
     */
    public String normalizarTelefono(String telefono) {
        if (telefono == null || telefono.trim().isEmpty()) {
            return telefono;
        }
        
        // Eliminar espacios, paréntesis, guiones, pero mantener el + inicial si existe
        String limpio = telefono.replaceAll("[\\s()\\-]", "");
        
        return limpio.trim();
    }

    /**
     * Normaliza cualquier texto genérico eliminando espacios múltiples y trim
     * Ejemplo: "  Texto   con   espacios  " → "Texto con espacios"
     * 
     * @param texto Texto a normalizar
     * @return Texto normalizado
     */
    public String normalizarTexto(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return texto;
        }
        
        // Reemplazar múltiples espacios por uno solo y hacer trim
        return texto.trim().replaceAll("\\s+", " ");
    }

    /**
     * Valida si un string está vacío después de normalizar
     * 
     * @param valor Valor a validar
     * @return true si está vacío o es null, false en caso contrario
     */
    public boolean estaVacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }

    /**
     * Normaliza múltiples campos de usuario de una vez
     * Útil para procesar formularios completos
     * 
     * @param nombre Nombre a normalizar
     * @param apellido Apellido a normalizar
     * @param email Email a normalizar
     * @param dni DNI a normalizar
     * @return Array con [nombre, apellido, email, dni] normalizados
     */
    public String[] normalizarUsuarioCompleto(String nombre, String apellido, String email, String dni) {
        return new String[] {
            normalizarNombreApellido(nombre),
            normalizarNombreApellido(apellido),
            normalizarEmail(email),
            normalizarDni(dni)
        };
    }
}
