package com.example.tureserva.servicio.dto;

import java.util.Map;

public class InitSeniaRequest {
    private Long complejoId;
    private String clienteEmail;
    private String referencia;
    private Boolean aplicarCredito;

    // estructura: { indiceItem : { idServicio : cantidad } }
    private Map<Integer, Map<Long, Integer>> serviciosPorItem;

    public Long getComplejoId() { return complejoId; }
    public void setComplejoId(Long complejoId) { this.complejoId = complejoId; }

    public Map<Integer, Map<Long, Integer>> getServiciosPorItem() { return serviciosPorItem; }
    public void setServiciosPorItem(Map<Integer, Map<Long, Integer>> serviciosPorItem) { this.serviciosPorItem = serviciosPorItem; }

    public String getClienteEmail() { return clienteEmail; }
    public void setClienteEmail(String clienteEmail) { this.clienteEmail = clienteEmail; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public Boolean getAplicarCredito() { return aplicarCredito; }
    public void setAplicarCredito(Boolean aplicarCredito) { this.aplicarCredito = aplicarCredito; }
}
