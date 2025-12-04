package com.example.tureserva.servicio;

import com.example.tureserva.modelo.Cliente;
import com.example.tureserva.modelo.CuentaCorriente;
import com.example.tureserva.modelo.MovimientoCuenta;
import com.example.tureserva.modelo.OfertaFlash;
import com.example.tureserva.modelo.Reserva;
import com.example.tureserva.modelo.enums.TipoMovimiento;
import com.example.tureserva.repositorio.RepositorioCuentaCorriente;
import com.example.tureserva.repositorio.RepositorioMovimientoCuenta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio para gestionar cuentas corrientes de clientes.
 * Maneja acreditaciones, débitos y movimientos de saldo.
 * 
 * @author TuReserva
 */
@Service
@Transactional(readOnly = true)
public class ServicioCuentaCorriente {
    
    private static final Logger logger = LoggerFactory.getLogger(ServicioCuentaCorriente.class);
    
    private final RepositorioCuentaCorriente repositorioCuenta;
    private final RepositorioMovimientoCuenta repositorioMovimiento;
    
    public ServicioCuentaCorriente(
            RepositorioCuentaCorriente repositorioCuenta,
            RepositorioMovimientoCuenta repositorioMovimiento) {
        this.repositorioCuenta = repositorioCuenta;
        this.repositorioMovimiento = repositorioMovimiento;
    }
    
    /**
     * Obtiene o crea la cuenta corriente de un cliente
     */
    @Transactional
    public CuentaCorriente obtenerOCrearCuenta(Cliente cliente) {
        if (cliente == null || cliente.getId() == null) {
            throw new IllegalArgumentException("El cliente es obligatorio");
        }
        
        return repositorioCuenta.findByCliente(cliente)
            .orElseGet(() -> {
                logger.info("Creando nueva cuenta corriente para cliente {}", cliente.getEmail());
                CuentaCorriente cuenta = new CuentaCorriente();
                cuenta.setCliente(cliente);
                cuenta.setSaldoDisponible(BigDecimal.ZERO);
                return repositorioCuenta.save(cuenta);
            });
    }
    
    /**
     * Obtiene el saldo disponible de un cliente
     */
    public BigDecimal obtenerSaldo(Cliente cliente) {
        return repositorioCuenta.findByCliente(cliente)
            .map(CuentaCorriente::getSaldoDisponible)
            .orElse(BigDecimal.ZERO);
    }
    
    /**
     * Acredita un monto a la cuenta del cliente con registro de movimiento.
     * Usa lock pessimista para evitar race conditions.
     * 
     * @param cliente Cliente a acreditar
     * @param monto Monto a acreditar
     * @param descripcion Descripción del movimiento
     * @param tipoMovimiento Tipo de movimiento (CREDITO, RECUPERO_SENIA, etc.)
     * @param oferta Oferta flash relacionada (opcional)
     * @param reserva Reserva relacionada (opcional)
     * @return Movimiento creado
     */
    @Transactional
    public MovimientoCuenta acreditarSaldo(
            Cliente cliente, 
            BigDecimal monto, 
            String descripcion,
            TipoMovimiento tipoMovimiento,
            OfertaFlash oferta,
            Reserva reserva) {
        
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }
        
        logger.info("Acreditando {} a cliente {} - Motivo: {}", 
                monto, cliente.getEmail(), descripcion);
        
        // Obtener o crear cuenta
        CuentaCorriente cuenta = obtenerOCrearCuenta(cliente);
        
        // Registrar saldo anterior
        BigDecimal saldoAnterior = cuenta.getSaldoDisponible();
        
        // Acreditar monto
        cuenta.acreditar(monto);
        cuenta = repositorioCuenta.save(cuenta);
        
        // Crear movimiento
        MovimientoCuenta movimiento = new MovimientoCuenta();
        movimiento.setCuentaCorriente(cuenta);
        movimiento.setTipoMovimiento(tipoMovimiento);
        movimiento.setMonto(monto);
        movimiento.setSaldoAnterior(saldoAnterior);
        movimiento.setSaldoNuevo(cuenta.getSaldoDisponible());
        movimiento.setDescripcion(descripcion);
        movimiento.setOfertaFlash(oferta);
        movimiento.setReserva(reserva);
        
        movimiento = repositorioMovimiento.save(movimiento);
        
        logger.info("Saldo acreditado. Nuevo saldo: {} (anterior: {})", 
                cuenta.getSaldoDisponible(), saldoAnterior);
        
        return movimiento;
    }
    
    /**
     * Debita un monto de la cuenta del cliente con registro de movimiento.
     * Valida que haya saldo suficiente.
     * 
     * @param cliente Cliente a debitar
     * @param monto Monto a debitar
     * @param descripcion Descripción del movimiento
     * @param reserva Reserva relacionada
     * @return Movimiento creado
     * @throws IllegalStateException si no hay saldo suficiente
     */
    @Transactional
    public MovimientoCuenta debitarSaldo(
            Cliente cliente, 
            BigDecimal monto, 
            String descripcion,
            Reserva reserva) {
        
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }
        
        logger.info("Debitando {} de cliente {} - Motivo: {}", 
                monto, cliente.getEmail(), descripcion);
        
        // Obtener cuenta
        CuentaCorriente cuenta = repositorioCuenta.findByCliente(cliente)
            .orElseThrow(() -> new IllegalStateException(
                "El cliente no tiene cuenta corriente"));
        
        // Validar saldo suficiente
        if (!cuenta.tieneSaldoSuficiente(monto)) {
            throw new IllegalStateException(String.format(
                "Saldo insuficiente. Disponible: %s, Requerido: %s", 
                cuenta.getSaldoDisponible(), monto));
        }
        
        // Registrar saldo anterior
        BigDecimal saldoAnterior = cuenta.getSaldoDisponible();
        
        // Debitar monto
        cuenta.debitar(monto);
        cuenta = repositorioCuenta.save(cuenta);
        
        // Crear movimiento
        MovimientoCuenta movimiento = new MovimientoCuenta();
        movimiento.setCuentaCorriente(cuenta);
        movimiento.setTipoMovimiento(TipoMovimiento.USO_CREDITO_RESERVA);
        movimiento.setMonto(monto);
        movimiento.setSaldoAnterior(saldoAnterior);
        movimiento.setSaldoNuevo(cuenta.getSaldoDisponible());
        movimiento.setDescripcion(descripcion);
        movimiento.setReserva(reserva);
        
        movimiento = repositorioMovimiento.save(movimiento);
        
        logger.info("Saldo debitado. Nuevo saldo: {} (anterior: {})", 
                cuenta.getSaldoDisponible(), saldoAnterior);
        
        return movimiento;
    }
    
    /**
     * Obtiene el historial de movimientos de un cliente
     */
    public List<MovimientoCuenta> obtenerMovimientos(Cliente cliente) {
        CuentaCorriente cuenta = repositorioCuenta.findByCliente(cliente)
            .orElse(null);
        
        if (cuenta == null) {
            return List.of();
        }
        
        return repositorioMovimiento.findByCuentaCorrienteOrderByFechaMovimientoDesc(cuenta);
    }
    
    /**
     * Obtiene movimientos de un cliente en un rango de fechas
     */
    public List<MovimientoCuenta> obtenerMovimientosPorFecha(
            Cliente cliente, 
            LocalDateTime inicio, 
            LocalDateTime fin) {
        
        CuentaCorriente cuenta = repositorioCuenta.findByCliente(cliente)
            .orElse(null);
        
        if (cuenta == null) {
            return List.of();
        }
        
        return repositorioMovimiento.findByCuentaAndFechaBetween(cuenta, inicio, fin);
    }
    
    /**
     * Verifica si un cliente tiene saldo suficiente
     */
    public boolean tieneSaldoSuficiente(Cliente cliente, BigDecimal monto) {
        BigDecimal saldoActual = obtenerSaldo(cliente);
        return saldoActual.compareTo(monto) >= 0;
    }
}
