package com.ufc.calculadora.common;

import java.rmi.RemoteException;

/**
 * Implementação local da interface remota CalculatorService.
 * Não estende UnicastRemoteObject: será exportada pelo servidor (exportObject).
 * Reutiliza ExpressionEvaluator para avaliar expressões.
 */
public class SimpleCalculator implements CalculatorService {
    private static final long serialVersionUID = 1L;

    private final ExpressionEvaluator evaluator;

    public SimpleCalculator() {
        this.evaluator = new ExpressionEvaluator();
    }

    @Override
    public double add(double a, double b) throws RemoteException {
        return a + b;
    }

    @Override
    public double sub(double a, double b) throws RemoteException {
        return a - b;
    }

    @Override
    public double mul(double a, double b) throws RemoteException {
        return a * b;
    }

    @Override
    public double div(double a, double b) throws RemoteException {
        if (b == 0.0) {
            throw new RemoteException("Divisão por zero");
        }
        return a / b;
    }

    @Override
    public double evaluate(String expr) throws RemoteException {
        try {
            return evaluator.evaluate(expr);
        } catch (IllegalArgumentException iae) {
            // converte exceção local para RemoteException para o cliente entender
            throw new RemoteException("Erro ao avaliar expressão: " + iae.getMessage());
        } catch (Exception e) {
            throw new RemoteException("Erro interno ao avaliar expressão: " + e.getMessage());
        }
    }
}