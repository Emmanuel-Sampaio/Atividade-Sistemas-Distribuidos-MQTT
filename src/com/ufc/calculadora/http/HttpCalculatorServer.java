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
                    case "add" -> result = evaluator.evaluate(a + " + " + b);
                    case "sub" -> result = evaluator.evaluate(a + " - " + b);
                    case "mul" -> result = evaluator.evaluate(a + " * " + b);
                    case "div" -> result = evaluator.evaluate(a + " / " + b);
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
                double result = evaluator.evaluate(expr);

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