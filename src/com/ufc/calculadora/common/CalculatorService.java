package com.ufc.calculadora.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface remota da calculadora.
 * Define operações básicas e avaliação de expressões.
 */
public interface CalculatorService extends Remote {
    double add(double a, double b) throws RemoteException;
    double sub(double a, double b) throws RemoteException;
    double mul(double a, double b) throws RemoteException;
    double div(double a, double b) throws RemoteException;
    double evaluate(String expr) throws RemoteException;
}