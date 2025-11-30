package com.example.tureserva.servicio;

import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Wrapper ligero para el SDK oficial de Mercado Pago (java-sdk).
 * Usa MPRequestOptions con accessToken por petición para soportar multi-tenant
 * y expone un método simple para crear preferences.
 */
@Component
public class MercadoPagoClient {
    private static final Logger log = LoggerFactory.getLogger(MercadoPagoClient.class);

    private final PreferenceClient preferenceClient = new PreferenceClient();

    public static class PreferenceResult {
        public final String id;
        public final String initPoint;

        public PreferenceResult(String id, String initPoint) {
            this.id = id;
            this.initPoint = initPoint;
        }
    }

    /**
     * Crea una preference usando el SDK y el access token del administrador (per-request).
     */
    public PreferenceResult createPreference(String accessToken, PreferenceRequest request) throws MPApiException, MPException {
        Objects.requireNonNull(accessToken, "accessToken es requerido");
        Objects.requireNonNull(request, "PreferenceRequest es requerido");

        MPRequestOptions options = MPRequestOptions.builder()
                .accessToken(accessToken)
                .build();

        Preference preference = preferenceClient.create(request, options);

        String id = preference.getId();
        String initPoint = preference.getInitPoint();
        log.info("Created MP preference id={} initPoint={}", id, initPoint);
        return new PreferenceResult(id, initPoint);
    }

    public static class PaymentResult {
        public final String id;
        public final String status;
        public final String externalReference;
        public final java.time.OffsetDateTime dateApproved;
        public final java.math.BigDecimal transactionAmount;

        public PaymentResult(String id, String status, String externalReference, java.time.OffsetDateTime dateApproved, java.math.BigDecimal transactionAmount) {
            this.id = id;
            this.status = status;
            this.externalReference = externalReference;
            this.dateApproved = dateApproved;
            this.transactionAmount = transactionAmount;
        }
    }

    public PaymentResult getPayment(String accessToken, String paymentId) throws MPApiException, MPException {
        MPRequestOptions options = MPRequestOptions.builder().accessToken(accessToken).build();
        com.mercadopago.client.payment.PaymentClient paymentClient = new com.mercadopago.client.payment.PaymentClient();
        Long pid;
        try {
            pid = Long.valueOf(paymentId);
        } catch (NumberFormatException nfe) {
            return null;
        }
        com.mercadopago.resources.payment.Payment payment = paymentClient.get(pid, options);
        if (payment == null) return null;

        String id = payment.getId() != null ? payment.getId().toString() : null;
        String status = payment.getStatus();
        String externalReference = payment.getExternalReference();
        java.time.OffsetDateTime dateApproved = payment.getDateApproved();
        java.math.BigDecimal transactionAmount = payment.getTransactionAmount();

        return new PaymentResult(id, status, externalReference, dateApproved, transactionAmount);
    }
}
