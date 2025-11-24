// sensor.js (CommonJS)
const mqtt = require("mqtt");

const BROKER = process.env.BROKER || "mqtt://localhost:1883";
const CLIENT_ID = (process.env.CLIENT_ID || "sensor-sim") + "-" + Math.random().toString(16).slice(2,6);
const sensorId = process.env.SENSOR_ID || "sensor1";
const TOPIC = `sensores/temperatura/${sensorId}`;
const INTERVAL_MS = parseInt(process.env.INTERVAL_MS || "10000", 10); // 10s por padrão

const client = mqtt.connect(BROKER, { clientId: CLIENT_ID, reconnectPeriod: 2000 });

client.on("connect", () => {
    console.log(`[Sensor] conectado ao broker (${BROKER}) clientId=${CLIENT_ID}`);
    setInterval(publishReading, INTERVAL_MS);
    publishReading(); // publica imediatamente também
});

client.on("error", err => console.error("[Sensor] erro MQTT:", err && err.message ? err.message : err));

function publishReading() {

    const value = Number((Math.random() * 100 + 150).toFixed(2));
    const payload = JSON.stringify({ sensorId, value, ts: new Date().toISOString() });
    client.publish(TOPIC, payload, { qos: 1 }, err => {
        if (err) console.error("[Sensor] publish error:", err);
        else console.log(`[Sensor] ${TOPIC} -> ${payload}`);
    });
}