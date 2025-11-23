package com.ufc.calculadora.RMI;

import com.ufc.calculadora.common.CalculatorService;
import com.ufc.calculadora.common.SimpleCalculator;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;


public class RMIServer {
    public static void main(String[] args) {
        try {
            int port = 1099;
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(port);
                System.out.println("RMI registry criado na porta " + port);
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(port);
                System.out.println("Usando RMI registry existente na porta " + port);
            }

            // instancia implementação local
            SimpleCalculator calc = new SimpleCalculator();

            // exporta o objeto local para obter um stub remoto
            CalculatorService stub = (CalculatorService) UnicastRemoteObject.exportObject(calc, 0);

            String name = "CalculatorService";
            registry.rebind(name, stub);
            System.out.println("CalculatorService registrado como '" + name + "'. Servidor pronto.");
            // servidor continua rodando esperando chamadas remotas
        } catch (Exception e) {
            System.err.println("Erro no servidor RMI: " + e.getMessage());
            e.printStackTrace();
        }
    }
}