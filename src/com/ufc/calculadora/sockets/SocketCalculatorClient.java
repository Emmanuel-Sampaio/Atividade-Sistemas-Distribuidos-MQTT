package com.ufc.calculadora.sockets;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
import java.util.UUID;

public class SocketCalculatorClient {
    private static final Gson GSON = new Gson();

    public static JsonObject request(String host, int port, String id, String expr, int timeoutMs) throws IOException {
        try (Socket sock = new Socket(host, port)) {
            sock.setSoTimeout(timeoutMs);
            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()));
                 BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()))) {

                JsonObject req = new JsonObject();
                req.addProperty("id", id);
                req.addProperty("expr", expr);

                // send request
                out.write(GSON.toJson(req));
                out.newLine();
                out.flush();

                // read single-line response
                String line = in.readLine();
                if (line == null) throw new IOException("No response from server");
                return GSON.fromJson(line, JsonObject.class);
            }
        }
    }

    // Quick manual test
    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int port = 5000;
        String id = UUID.randomUUID().toString();
        String expr = "3 * 3";
        JsonObject resp = request(host, port, id, expr, 5000);
        System.out.println("Response: " + GSON.toJson(resp));
    }
}