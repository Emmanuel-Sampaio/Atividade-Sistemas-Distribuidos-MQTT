package com.ufc.calculadora.mqtt;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.ufc.calculadora.common.ExpressionEvaluator;
import com.ufc.calculadora.common.SimpleCalculator;
import org.eclipse.paho.client.mqttv3.*;

public class MqttCalculatorService {
    private static final String BROKER = "tcp://localhost:1883";
    private static final String REQ_TOPIC = "calculadora/request";
    private static final String RESP_TOPIC_PREFIX = "calculadora/response/"; // + {id}
    private static final Gson GSON = new Gson();

    private final MqttClient client;
    private final ExpressionEvaluator evaluator;

    public MqttCalculatorService(String clientId) throws MqttException {
        evaluator = new ExpressionEvaluator(new SimpleCalculator());
        client = new MqttClient(BROKER, clientId);
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setAutomaticReconnect(true);
        opts.setCleanSession(true);
        client.connect(opts);

        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                System.err.println("Connection lost: " + cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                handleRequest(new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) { }
        });

        client.subscribe(REQ_TOPIC, 0);
        System.out.println("Subscribed to " + REQ_TOPIC);
    }

    private void handleRequest(String payload) {
        String reqId = null;
        try {
            JsonObject obj = GSON.fromJson(payload, JsonObject.class);
            if (obj == null || !obj.has("id") || !obj.has("expr")) {
                System.err.println("Invalid request payload: " + payload);
                return;
            }
            reqId = obj.get("id").getAsString();
            String expr = obj.get("expr").getAsString();

            double result = evaluator.evaluate(expr);

            JsonObject resp = new JsonObject();
            resp.addProperty("id", reqId);
            resp.addProperty("result", result);
            resp.add("error", null);

            publishResponse(reqId, resp.toString());
            System.out.println("Handled request " + reqId + " -> " + result);
        } catch (JsonParseException e) {
            System.err.println("JSON parse error: " + e.getMessage() + " payload=" + payload);
            if (reqId != null) publishError(reqId, "Invalid JSON");
        } catch (IllegalArgumentException | ArithmeticException e) {
            System.err.println("Eval error: " + e.getMessage());
            if (reqId != null) publishError(reqId, e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            if (reqId != null) publishError(reqId, "Internal error");
        }
    }

    private void publishResponse(String reqId, String json) {
        try {
            String topic = RESP_TOPIC_PREFIX + reqId;
            MqttMessage msg = new MqttMessage(json.getBytes());
            msg.setQos(0);
            client.publish(topic, msg);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void publishError(String reqId, String errorMsg) {
        JsonObject resp = new JsonObject();
        resp.addProperty("id", reqId);
        resp.add("result", null);
        resp.addProperty("error", errorMsg);
        publishResponse(reqId, resp.toString());
    }

    public void disconnect() {
        try {
            if (client.isConnected()) client.disconnect();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        String clientId = "calculator-service-" + java.util.UUID.randomUUID();
        var svc = new MqttCalculatorService(clientId);
        Runtime.getRuntime().addShutdownHook(new Thread(svc::disconnect));
        System.out.println("Calculator service running. Waiting requests...");
        Thread.currentThread().join();
    }
}