package com.ufc.calculadora.http;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.ufc.calculadora.common.ExpressionEvaluator;
import static spark.Spark.*;

public class HttpCalculatorServer {
    private static final Gson GSON = new Gson();
    private final ExpressionEvaluator evaluator;

    public HttpCalculatorServer() {
        this.evaluator = new ExpressionEvaluator();
    }

    /**
     * Retry apenas para falhas inesperadas. Não tenta para IllegalArgumentException (erro do cliente).
     * maxAttempts: número total de tentativas (inclui a primeira).
     * baseBackoffMs: backoff inicial (dobrando a cada tentativa), com jitter.
     */
    private double evaluateWithRetry(String expr, int maxAttempts, long baseBackoffMs) {
        int attempt = 0;
        long backoff = baseBackoffMs;
        while (true) {
            try {
                return evaluator.evaluate(expr);
            } catch (IllegalArgumentException iae) {
                throw iae; // erro do cliente, não retry
            } catch (Exception e) {
                attempt++;
                if (attempt >= maxAttempts) {
                    throw new RuntimeException("Falha após " + attempt + " tentativas: " + e.getMessage(), e);
                }
                try {
                    long jitter = (long) (Math.random() * backoff);
                    Thread.sleep(backoff + jitter);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrompido", ie);
                }
                backoff *= 2;
            }
        }
    }

    public void start(int port) {
        port(port);

        post("/calc/op", (req, res) -> {
            res.type("application/json");
            JsonObject resp = new JsonObject();
            try {
                JsonObject body = GSON.fromJson(req.body(), JsonObject.class);
                if (body == null || !body.has("op") || !body.has("a") || !body.has("b")) {
                    resp.addProperty("id", body != null && body.has("id") ? body.get("id").getAsString() : "unknown");
                    resp.add("result", JsonNull.INSTANCE);
                    resp.addProperty("error", "Invalid request: expected {op, a, b}");
                    return GSON.toJson(resp);
                }

                String id = body.has("id") ? body.get("id").getAsString() : "unknown";
                String op = body.get("op").getAsString();
                double a = body.get("a").getAsDouble();
                double b = body.get("b").getAsDouble();

                double result;
                switch (op) {
                    case "add" -> result = evaluateWithRetry(a + " + " + b, 3, 100);
                    case "sub" -> result = evaluateWithRetry(a + " - " + b, 3, 100);
                    case "mul" -> result = evaluateWithRetry(a + " * " + b, 3, 100);
                    case "div" -> result = evaluateWithRetry(a + " / " + b, 3, 100);
                    default -> throw new IllegalArgumentException("Invalid operation: " + op);
                }

                resp.addProperty("id", id);
                resp.addProperty("result", result);
                resp.add("error", JsonNull.INSTANCE);
                return GSON.toJson(resp);

            } catch (IllegalArgumentException | ArithmeticException e) {
                resp.addProperty("id", "unknown");
                resp.add("result", JsonNull.INSTANCE);
                resp.addProperty("error", e.getMessage());
                return GSON.toJson(resp);
            } catch (Exception e) {
                resp.addProperty("id", "unknown");
                resp.add("result", JsonNull.INSTANCE);
                resp.addProperty("error", "Internal server error");
                e.printStackTrace();
                return GSON.toJson(resp);
            }
        });

        post("/calc/expr", (req, res) -> {
            res.type("application/json");
            JsonObject resp = new JsonObject();
            try {
                JsonObject body = GSON.fromJson(req.body(), JsonObject.class);
                if (body == null || !body.has("expr")) {
                    resp.addProperty("id", body != null && body.has("id") ? body.get("id").getAsString() : "unknown");
                    resp.add("result", JsonNull.INSTANCE);
                    resp.addProperty("error", "Invalid request: expected {expr}");
                    return GSON.toJson(resp);
                }

                String id = body.has("id") ? body.get("id").getAsString() : "unknown";
                String expr = body.get("expr").getAsString();
                double result = evaluateWithRetry(expr, 3, 100);

                resp.addProperty("id", id);
                resp.addProperty("result", result);
                resp.add("error", JsonNull.INSTANCE);
                return GSON.toJson(resp);

            } catch (IllegalArgumentException | ArithmeticException e) {
                resp.addProperty("id", "unknown");
                resp.add("result", JsonNull.INSTANCE);
                resp.addProperty("error", e.getMessage());
                return GSON.toJson(resp);
            } catch (Exception e) {
                resp.addProperty("id", "unknown");
                resp.add("result", JsonNull.INSTANCE);
                resp.addProperty("error", "Internal server error");
                e.printStackTrace();
                return GSON.toJson(resp);
            }
        });

        System.out.println("HTTP Calculator Server started at http://localhost:" + port);
    }

    public static void main(String[] args) {
        new HttpCalculatorServer().start(4567);
    }

}