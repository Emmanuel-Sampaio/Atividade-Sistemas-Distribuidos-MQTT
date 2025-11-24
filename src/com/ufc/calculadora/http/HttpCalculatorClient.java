package com.ufc.calculadora.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

/**
 * HttpCalculatorClient
 * Cliente HTTP que chama um servidor que expõe dois endpoints:
 *  - POST /calc/op  -> { id?, op, a, b }  retorna { id, result, error }
 *  - POST /calc/expr -> { id?, expr }      retorna { id, result, error }
 *
 * Contém:
 *  - callOpWithRetry(...) -> chama /calc/op com retry
 *  - callExprWithRetry(...) -> chama /calc/expr com retry
 *  - evaluateExpressionServerSide(...) -> usa /calc/expr
 *  - evaluateExpressionByDecomposition(...) -> converte a expressão para RPN e chama /calc/op para cada operação
 *
 * Política de retry: maxAttempts=4, baseDelayMs=300ms, backoff exponencial + jitter
 */
public class HttpCalculatorClient {
    private static final Gson GSON = new Gson();

    // OkHttp client usado para todas as requisições
    private final OkHttpClient client;
    // Base URL do servidor (ex.: "http://localhost:8000" ou "http://localhost:4567")
    private final String baseUrl;

    public HttpCalculatorClient(String baseUrl) {
        // Normaliza baseUrl removendo '/' final, se houver
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        // Configura o OkHttp com timeout de chamada
        this.client = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Chama /calc/op com retry. Envia JSON: { id?, op, a, b }.
     * Retorna o JSON de resposta parseado (JsonObject).
     */
    public JsonObject callOpWithRetry(String op, double a, double b, String id) throws IOException, InterruptedException {
        JsonObject req = new JsonObject();
        req.addProperty("op", op);
        req.addProperty("a", a);
        req.addProperty("b", b);
        if (id != null) req.addProperty("id", id);
        String body = GSON.toJson(req);
        String resp = postWithRetry("/calc/op", body);
        return GSON.fromJson(resp, JsonObject.class);
    }

    /**
     * Chama /calc/expr com retry. Envia JSON: { id?, expr }.
     * Retorna JsonObject com { id, result, error }.
     */
    public JsonObject callExprWithRetry(String expr, String id) throws IOException, InterruptedException {
        JsonObject req = new JsonObject();
        req.addProperty("expr", expr);
        if (id != null) req.addProperty("id", id);
        String body = GSON.toJson(req);
        String resp = postWithRetry("/calc/expr", body);
        return GSON.fromJson(resp, JsonObject.class);
    }

    /**
     * Implementação da política de retry:
     *  - maxAttempts: número máximo de tentativas
     *  - baseDelayMs: tempo base para backoff exponencial
     *  - jitter: adiciona aleatoriedade para reduzir thundering herd
     *
     * Trata 5xx como retryable; para outras falhas de rede também faz retry até maxAttempts.
     */
    private String postWithRetry(String path, String json) throws IOException, InterruptedException {
        int maxAttempts = 4;
        long baseDelayMs = 300;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            RequestBody requestBody = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder().url(baseUrl + path).post(requestBody).build();
            try (Response r = client.newCall(request).execute()) {
                // Se sucesso (2xx) retorna o corpo
                if (r.isSuccessful()) return r.body().string();

                // Se status 5xx (server error) -> retry até maxAttempts
                if (r.code() >= 500 && attempt < maxAttempts) {
                    long delay = baseDelayMs * (1L << (attempt - 1));
                    Thread.sleep(delay + (long) (Math.random() * delay)); // jitter
                    continue;
                }

                // Para outros códigos, considera erro irreversível
                throw new IOException("HTTP error: " + r.code() + " - " + r.message());
            } catch (IOException e) {
                // Falha de rede: se ainda houver tentativas, aguarda e tenta novamente
                if (attempt == maxAttempts) throw e;
                long delay = baseDelayMs * (1L << (attempt - 1));
                Thread.sleep(delay + (long) (Math.random() * delay));
            }
        }
        throw new IOException("Retries exhausted");
    }

    // ------------------ helpers para as duas abordagens de avaliação ------------------

    /**
     * 1) Server-side evaluate:
     * Envia a expressão inteira para o servidor, que devolve o resultado.
     * Lança IOException se o servidor retornar um campo "error".
     */
    public double evaluateExpressionServerSide(String expr) throws IOException, InterruptedException {
        JsonObject resp = callExprWithRetry(expr, UUID.randomUUID().toString());
        if (resp.has("error") && !resp.get("error").isJsonNull()) {
            // Propaga a mensagem de erro do servidor como IOException
            throw new IOException("Server error: " + resp.get("error").getAsString());
        }
        return resp.get("result").getAsDouble();
    }

    /**
     * 2) Client-side decomposition:
     *  - converte a expressão infix para RPN (shunting-yard)
     *  - ao verificar um operador, chama /calc/op para executar a operação no servidor
     *  - útil para comparar overhead e latência (muitas requisições vs 1 requisição)
     */
    public double evaluateExpressionByDecomposition(String expr) throws IOException, InterruptedException {
        List<String> rpn = infixToRPN(expr);
        Deque<Double> stack = new ArrayDeque<>();
        for (String token : rpn) {
            if (isNumber(token)) {
                stack.push(Double.parseDouble(token));
            } else {
                // operador: pop dois operandos (atenção à ordem)
                double b = stack.pop();
                double a = stack.pop();
                String op = switch (token) {
                    case "+" -> "add";
                    case "-" -> "sub";
                    case "*" -> "mul";
                    case "/" -> "div";
                    default -> throw new IllegalArgumentException("Unsupported operator: " + token);
                };
                // chama o servidor para executar a operação básica
                JsonObject resp = callOpWithRetry(op, a, b, UUID.randomUUID().toString());
                if (resp.has("error") && !resp.get("error").isJsonNull()) {
                    throw new IOException("Server op error: " + resp.get("error").getAsString());
                }
                double result = resp.get("result").getAsDouble();
                stack.push(result);
            }
        }
        if (stack.size() != 1) throw new IllegalStateException("Invalid RPN evaluation");
        return stack.pop();
    }

    // ------------------ utilitário: shunting-yard (infix -> RPN) ------------------

    private List<String> infixToRPN(String expr) {
        List<String> output = new ArrayList<>();
        Deque<String> ops = new ArrayDeque<>();
        String cleaned = expr.replaceAll("\\s+", ""); // remove espaços
        int i = 0;
        while (i < cleaned.length()) {
            char c = cleaned.charAt(i);
            // número (inclui ponto decimal e sinais unários)
            if (Character.isDigit(c) || c == '.' || ((c == '+' || c == '-') && (i == 0 || cleaned.charAt(i-1) == '(' || "+-*/".indexOf(cleaned.charAt(i-1)) >= 0))) {
                int j = i + 1;
                while (j < cleaned.length() && (Character.isDigit(cleaned.charAt(j)) || cleaned.charAt(j) == '.')) j++;
                String num = cleaned.substring(i, j);
                output.add(num);
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
                ops.push(String.valueOf(c));
                i++;
            } else if (c == ')') {
                while (!ops.isEmpty() && !ops.peek().equals("(")) output.add(ops.pop());
                if (ops.isEmpty() || !ops.peek().equals("(")) throw new IllegalArgumentException("Mismatched parentheses");
                ops.pop(); // remove '('
                i++;
            } else {
                throw new IllegalArgumentException("Invalid character in expression: " + c);
            }
        }
        while (!ops.isEmpty()) {
            String t = ops.pop();
            if (t.equals("(") || t.equals(")")) throw new IllegalArgumentException("Mismatched parentheses");
            output.add(t);
        }
        return output;
    }

    private boolean isOperator(String s) { return s != null && s.length() == 1 && "+-*/".contains(s); }

    private int precedence(String op) {
        return switch (op) {
            case "+", "-" -> 1;
            case "*", "/" -> 2;
            default -> 0;
        };
    }

    private boolean isNumber(String token) {
        try {
            Double.parseDouble(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // substitua o main existente em HttpCalculatorClient.java
    public static void main(String[] args) throws Exception {
        String base = (args.length > 0) ? args[0] : "http://localhost:8000";
        HttpCalculatorClient client = new HttpCalculatorClient(base);
        Scanner sc = new Scanner(System.in);
        System.out.println("HTTP client apontando para " + base + ". Digite expressões. 'sair' para encerrar.");
        while (true) {
            System.out.print("> ");
            String line = sc.nextLine();
            if (line == null) break;
            line = line.trim();
            if (line.equalsIgnoreCase("sair") || line.equalsIgnoreCase("exit")) break;
            if (line.isEmpty()) continue;
            try {
                double result = client.evaluateExpressionServerSide(line);
                System.out.println("Resultado = " + result);
            } catch (Exception e) {
                System.err.println("Erro: " + e.getMessage());
            }
        }
        sc.close();
        System.out.println("HTTP client encerrado.");
    }

}