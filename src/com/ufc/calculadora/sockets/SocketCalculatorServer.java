package com.ufc.calculadora.sockets;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.ufc.calculadora.common.ExpressionEvaluator;
import com.ufc.calculadora.common.SimpleCalculator;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketCalculatorServer {
    private static final int PORT = 5000;
    private static final Gson GSON = new Gson();
    private final ExpressionEvaluator evaluator;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public SocketCalculatorServer() {
        evaluator = new ExpressionEvaluator(new SimpleCalculator());
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("SocketCalculatorServer listening on port " + PORT);
            while (true) {
                Socket client = serverSocket.accept();
                pool.submit(() -> handleClient(client));
            }
        } finally {
            pool.shutdown();
        }
    }

    private void handleClient(Socket socket) {
        String clientInfo = socket.getRemoteSocketAddress().toString();
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            // Read a single line JSON request
            String line = in.readLine();
            if (line == null) return;

            // Parse and handle
            JsonObject respJson = new JsonObject();
            try {
                JsonObject req = GSON.fromJson(line, JsonObject.class);
                if (req == null || !req.has("id") || !req.has("expr")) {
                    respJson.addProperty("id", req != null && req.has("id") ? req.get("id").getAsString() : "unknown");
                    respJson.add("result", null);
                    respJson.addProperty("error", "Invalid request (expected {\"id\":\"..\",\"expr\":\"..\"})");
                } else {
                    String id = req.get("id").getAsString();
                    String expr = req.get("expr").getAsString();
                    double result = evaluator.evaluate(expr);

                    respJson.addProperty("id", id);
                    respJson.addProperty("result", result);
                    respJson.add("error", null);
                    System.out.println("Handled " + clientInfo + " -> " + id + " : " + expr + " = " + result);
                }
                } catch (JsonParseException e) {
                    respJson.addProperty("id", "unknown");
                    respJson.add("result", JsonNull.INSTANCE);
                    respJson.addProperty("error", "JSON parse error: " + e.getMessage());
                } catch (IllegalArgumentException | ArithmeticException e) {
                    respJson.addProperty("id", "unknown");
                    respJson.add("result", JsonNull.INSTANCE);
                    respJson.addProperty("error", e.getMessage());
                } catch (Exception e) {
                    respJson.addProperty("id", "unknown");
                    respJson.add("result", JsonNull.INSTANCE);
                    respJson.addProperty("error", "Internal server error");
                    e.printStackTrace();
                }

            // Send response as single line JSON + newline
            out.write(GSON.toJson(respJson));
            out.newLine();
            out.flush();
        } catch (IOException e) {
            System.err.println("I/O error with client " + clientInfo + ": " + e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        new SocketCalculatorServer().start();
    }
}