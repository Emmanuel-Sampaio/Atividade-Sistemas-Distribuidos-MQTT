package com.ufc.calculadora.common;

import java.util.*;


public class ExpressionEvaluator {

    public ExpressionEvaluator() {
        // sem estado
    }

    public double evaluate(String expr) {
        if (expr == null) throw new IllegalArgumentException("Expressão nula");
        String s = expr.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Expressão vazia");

        List<Token> tokens = tokenize(s);
        List<Token> rpn = toRPN(tokens);
        return evalRPN(rpn);
    }

    // ---------- Tokenização ----------
    private enum Type {NUMBER, OP, LPAREN, RPAREN}

    private static class Token {
        final Type type;
        final String text;
        final double value; // only for NUMBER

        Token(Type type, String text) {
            this(type, text, Double.NaN);
        }
        Token(Type type, String text, double value) {
            this.type = type;
            this.text = text;
            this.value = value;
        }

        boolean isOperator() { return type == Type.OP; }
    }

    private List<Token> tokenize(String s) {
        List<Token> out = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '(') {
                out.add(new Token(Type.LPAREN, "(")); i++; continue;
            }
            if (c == ')') {
                out.add(new Token(Type.RPAREN, ")")); i++; continue;
            }
            if (isOperatorChar(c)) {
                String op = String.valueOf(c);
                out.add(new Token(Type.OP, op)); i++; continue;
            }
            if (Character.isDigit(c) || c == '.') {
                int j = i;
                boolean dotSeen = false;
                while (j < n) {
                    char cj = s.charAt(j);
                    if (cj == '.') {
                        if (dotSeen) break;
                        dotSeen = true;
                        j++;
                        continue;
                    }
                    if (Character.isDigit(cj)) { j++; continue; }
                    break;
                }
                String numStr = s.substring(i, j);
                try {
                    double v = Double.parseDouble(numStr);
                    out.add(new Token(Type.NUMBER, numStr, v));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Número inválido: " + numStr);
                }
                i = j;
                continue;
            }
            throw new IllegalArgumentException("Token inválido na expressão em índice " + i + ": '" + c + "'");
        }
        return out;
    }

    private boolean isOperatorChar(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '^';
    }

    // ---------- Shunting-yard to RPN ----------
    private List<Token> toRPN(List<Token> tokens) {
        List<Token> output = new ArrayList<>();
        Deque<Token> ops = new ArrayDeque<>();

        Token prev = null;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type == Type.NUMBER) {
                output.add(t);
            } else if (t.type == Type.OP) {
                String op = t.text;
                // unary minus detection: treat "-x" as "0 - x"
                if (op.equals("-") && (prev == null || prev.type == Type.OP || prev.type == Type.LPAREN)) {
                    output.add(new Token(Type.NUMBER, "0", 0.0));
                }
                while (!ops.isEmpty() && ops.peek().type == Type.OP) {
                    Token top = ops.peek();
                    if ( (isLeftAssoc(op) && precedence(op) <= precedence(top.text))
                            || (!isLeftAssoc(op) && precedence(op) < precedence(top.text)) ) {
                        output.add(ops.pop());
                    } else break;
                }
                ops.push(t);
            } else if (t.type == Type.LPAREN) {
                ops.push(t);
            } else if (t.type == Type.RPAREN) {
                boolean foundLeft = false;
                while (!ops.isEmpty()) {
                    Token pop = ops.pop();
                    if (pop.type == Type.LPAREN) { foundLeft = true; break; }
                    output.add(pop);
                }
                if (!foundLeft) throw new IllegalArgumentException("Parênteses desencontrados");
            }
            prev = t;
        }
        while (!ops.isEmpty()) {
            Token p = ops.pop();
            if (p.type == Type.LPAREN || p.type == Type.RPAREN) throw new IllegalArgumentException("Parênteses desencontrados");
            output.add(p);
        }
        return output;
    }

    private int precedence(String op) {
        switch (op) {
            case "+":
            case "-": return 2;
            case "*":
            case "/": return 3;
            case "^": return 4;
            default: throw new IllegalArgumentException("Operador desconhecido: " + op);
        }
    }

    private boolean isLeftAssoc(String op) {
        return !op.equals("^");
    }

    // ---------- Avaliação do RPN ----------
    private double evalRPN(List<Token> rpn) {
        Deque<Double> st = new ArrayDeque<>();
        for (Token t : rpn) {
            if (t.type == Type.NUMBER) {
                st.push(t.value);
            } else if (t.type == Type.OP) {
                if (st.size() < 2) throw new IllegalArgumentException("Expressão inválida (operadores faltando operandos).");
                double b = st.pop();
                double a = st.pop();
                double res;
                switch (t.text) {
                    case "+": res = a + b; break;
                    case "-": res = a - b; break;
                    case "*": res = a * b; break;
                    case "/":
                        if (b == 0.0) throw new IllegalArgumentException("Divisão por zero");
                        res = a / b; break;
                    case "^": res = Math.pow(a, b); break;
                    default: throw new IllegalArgumentException("Operador desconhecido: " + t.text);
                }
                st.push(res);
            } else {
                throw new IllegalArgumentException("Token inesperado no RPN: " + t.text);
            }
        }
        if (st.size() != 1) throw new IllegalArgumentException("Expressão inválida (pilha final com " + st.size() + " elementos).");
        return st.pop();
    }
}