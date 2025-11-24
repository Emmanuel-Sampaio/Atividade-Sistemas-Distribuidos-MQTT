package com.ufc.calculadora.sockets;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
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


    public static void main(String[] args) throws Exception {
        String host = (args.length > 0) ? args[0] : "localhost";
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : 5000;
        int timeoutMs = 5000;
        Scanner sc = new Scanner(System.in);
        System.out.println("Socket client conectado a " + host + ":" + port + ". Digite expressões. 'sair' para encerrar.");
        while (true) {
            System.out.print("> ");
            String line = sc.nextLine();
            if (line == null) break;
            line = line.trim();
            if (line.equalsIgnoreCase("sair") || line.equalsIgnoreCase("exit")) break;
            if (line.isEmpty()) continue;
            String id = java.util.UUID.randomUUID().toString();
            try {
                JsonObject resp = request(host, port, id, line, timeoutMs);
                System.out.println("Resp: " + GSON.toJson(resp));
            } catch (Exception e) {
                System.err.println("Erro: " + e.getMessage());
            }
        }
        sc.close();
        System.out.println("Socket client encerrado.");
    }
}