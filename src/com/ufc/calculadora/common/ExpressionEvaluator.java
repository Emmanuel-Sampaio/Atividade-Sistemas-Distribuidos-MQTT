package com.ufc.calculadora.common;

import java.util.*;
import java.util.regex.Pattern;


public class ExpressionEvaluator {
    private final CalculatorService calculator;

    public ExpressionEvaluator(CalculatorService calculator) {
        this.calculator = calculator;
    }

    public double evaluate(String expr) {
        if (expr == null) throw new IllegalArgumentException("Expressão nula");
        List<String> rpn = infixToRPN(expr);
        Deque<Double> stack = new ArrayDeque<>();
        for (String token : rpn) {
            if (isNumber(token)) {
                stack.push(Double.parseDouble(token));
            } else {
                if (stack.size() < 2) throw new IllegalArgumentException("Expressão inválida");
                double b = stack.pop();
                double a = stack.pop();
                double res;
                switch (token) {
                    case "+" -> res = calculator.add(a, b);
                    case "-" -> res = calculator.subtract(a, b);
                    case "*" -> res = calculator.multiply(a, b);
                    case "/" -> res = calculator.divide(a, b);
                    default -> throw new IllegalArgumentException("Operador não suportado: " + token);
                }
                stack.push(res);
            }
        }
        if (stack.size() != 1) throw new IllegalArgumentException("Expressão inválida");
        return stack.pop();
    }

    // Converte infix para RPN com shunting-yard
    private List<String> infixToRPN(String expr) {
        List<String> output = new ArrayList<>();
        Deque<String> ops = new ArrayDeque<>();
        String cleaned = expr.replaceAll("\\s+", "");
        int i = 0;
        while (i < cleaned.length()) {
            char c = cleaned.charAt(i);
            if (Character.isDigit(c) || c == '.' || ((c == '+' || c == '-') && (i == 0 || cleaned.charAt(i-1) == '(' || "+-*/".indexOf(cleaned.charAt(i-1)) >= 0))) {
                int j = i + 1;
                while (j < cleaned.length() && (Character.isDigit(cleaned.charAt(j)) || cleaned.charAt(j) == '.')) j++;
                output.add(cleaned.substring(i, j));
                i = j;
            } else if ("+-*/".indexOf(c) >= 0) {
                String o1 = String.valueOf(c);
                while (!ops.isEmpty()) {
                    String o2 = ops.peek();
                    if (isOperator(o2) && (precedence(o1) <= precedence(o2))) {
                        output.add(ops.pop());
                    } else break;
                }
                ops.push(o1);
                i++;
            } else if (c == '(') {
                ops.push("(");
                i++;
            } else if (c == ')') {
                while (!ops.isEmpty() && !ops.peek().equals("(")) output.add(ops.pop());
                if (ops.isEmpty() || !ops.peek().equals("(")) throw new IllegalArgumentException("Parênteses descompassados");
                ops.pop(); // remove '('
                i++;
            } else {
                throw new IllegalArgumentException("Caracter inválido na expressão: " + c);
            }
        }
        while (!ops.isEmpty()) {
            String t = ops.pop();
            if (t.equals("(") || t.equals(")")) throw new IllegalArgumentException("Parênteses descompassados");
            output.add(t);
        }
        return output;
    }

    private boolean isOperator(String s) {
        return s != null && s.length() == 1 && "+-*/".contains(s);
    }

    private int precedence(String op) {
        return switch (op) {
            case "+", "-" -> 1;
            case "*", "/" -> 2;
            default -> 0;
        };
    }

    private boolean isNumber(String token) {
        if (token == null) return false;
        // simples verificação; aceita "-3.5", "2", ".5"
        return Pattern.matches("[+-]?\\d*\\.?\\d+", token);
    }
}