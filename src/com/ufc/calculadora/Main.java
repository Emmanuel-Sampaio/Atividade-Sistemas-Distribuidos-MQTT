package com.ufc.calculadora;

import com.ufc.calculadora.common.ExpressionEvaluator;
import com.ufc.calculadora.common.SimpleCalculator;

public class Main {
    public static void main(String[] args) {
        var calc = new SimpleCalculator();
        var evaluator = new ExpressionEvaluator(calc);

        System.out.println("Calculadora de Trabalho - Teste");
        System.out.println("3 + 10 = " + evaluator.evaluate("3 + 4"));
    }
}