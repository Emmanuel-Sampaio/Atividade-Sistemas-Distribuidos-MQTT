package com.ufc.calculadora.common;

public class SimpleCalculator implements CalculatorService {
    @Override
    public double add(double a, double b) { return a + b; }
    @Override
    public double subtract(double a, double b) { return a - b; }
    @Override
    public double multiply(double a, double b) { return a * b; }
    @Override
    public double divide(double a, double b) {
        if (b == 0) throw new ArithmeticException("Divisão por zero");
        return a / b;
    }
}