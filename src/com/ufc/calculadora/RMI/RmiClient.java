package com.ufc.calculadora.RMI;

import com.ufc.calculadora.common.CalculatorService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

public class RmiClient {
    public static void main(String[] args) {
        try {
            String host = (args.length > 0) ? args[0] : "localhost";
            int port = (args.length > 1) ? Integer.parseInt(args[1]) : 1099;
            Registry registry = LocateRegistry.getRegistry(host, port);
            CalculatorService calc = (CalculatorService) registry.lookup("CalculatorService");

            System.out.println("Conectado ao CalculatorService em " + host + ":" + port);

            // Loop interativo para evaluate()
            Scanner sc = new Scanner(System.in);
            System.out.println("\nDigite expressões para avaliar (ex: 2+3*(4-1)). 'sair' ou 'exit' para encerrar.");
            while (true) {
                System.out.print("> ");
                String line = sc.nextLine();
                if (line == null) break;
                line = line.trim();
                if (line.equalsIgnoreCase("sair") || line.equalsIgnoreCase("exit")) break;
                if (line.isEmpty()) continue;
                try {
                    double res = calc.evaluate(line);
                    System.out.println("Resultado = " + res);
                } catch (Exception e) {
                    System.out.println("Erro: " + e.getMessage());
                }
            }
            sc.close();
            System.out.println("Cliente RMI encerrado.");

        } catch (Exception e) {
            System.err.println("Erro no cliente RMI: " + e.getMessage());
            e.printStackTrace();
        }
    }
}