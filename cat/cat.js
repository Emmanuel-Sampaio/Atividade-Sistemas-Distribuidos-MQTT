// cat.js (CommonJS)
const mqtt = require("mqtt");

const BROKER = process.env.BROKER || "mqtt://localhost:1883";
const CLIENT_ID = (process.env.CLIENT_ID || "cat-service") + "-" + Math.random().toString(16).slice(2,6);
const TOPIC_SENSOR = process.env.TOPIC_SENSOR || "sensores/temperatura/#";
const TOPIC_EVENT_BASE = process.env.TOPIC_EVENT_BASE || "cat/alerta";
const WINDOW_MS = parseInt(process.env.WINDOW_MS || "120000", 10); // 120s
const SUDDEN_THRESHOLD = parseFloat(process.env.SUDDEN_THRESHOLD || "5"); // graus
const TEMP_HIGH_THRESHOLD = parseFloat(process.env.TEMP_HIGH_THRESHOLD || "200");

const client = mqtt.connect(BROKER, { clientId: CLIENT_ID, reconnectPeriod: 2000, clean: true });

let readings = []; // { ts:number, value:number }
let lastAvg = null;

client.on("connect", () => {
    console.log(`[CAT] conectado ao broker (${BROKER}) clientId=${CLIENT_ID}`);
    client.subscribe(TOPIC_SENSOR, { qos: 1 }, err => {
        if (err) console.error("[CAT] subscribe error:", err.message || err);
        else console.log(`[CAT] subscrito em '${TOPIC_SENSOR}'`);
    });
});

client.on("error", err => console.error("[CAT] erro MQTT:", err && err.message ? err.message : err));

client.on("message", (topic, payload) => {
    const raw = payload && payload.toString ? payload.toString() : String(payload);
    let value = null;
    let ts = Date.now();


    try {
        const p = JSON.parse(raw);
        value = Number(p.value ?? p.temp ?? p.temperature ?? p.v);
        if (p.ts) {
            const tnum = typeof p.ts === "number" ? (p.ts > 1e12 ? p.ts : p.ts*1000) : Date.parse(p.ts);
            if (!isNaN(tnum)) ts = tnum;
        }
    } catch (e) {
        const v = parseFloat(raw);
        if (Number.isFinite(v)) value = v;
    }

    if (!Number.isFinite(value)) {
        console.warn("[CAT] valor inválido recebido em", topic, "raw=", raw);
        return;
    }


    readings.push({ ts, value });
    const cutoff = Date.now() - WINDOW_MS;
    readings = readings.filter(r => r.ts >= cutoff);


    const sum = readings.reduce((a,r) => a + r.value, 0);
    const avg = readings.length ? (sum / readings.length) : 0;

    console.log(`[CAT] média (últimos ${Math.round(WINDOW_MS/1000)}s) = ${avg.toFixed(2)} (${readings.length} leituras)`);


    if (lastAvg !== null) {
        const delta = avg - lastAvg;
        if (Math.abs(delta) >= SUDDEN_THRESHOLD) {
            const tipo = delta > 0 ? "aumento_repentino" : "reducao_repentina";
            const event = {
                eventId: `${Date.now()}-${Math.random().toString(16).slice(2,8)}`,
                type: tipo,
                avg: Number(avg.toFixed(3)),
                prevAvg: Number(lastAvg.toFixed(3)),
                delta: Number(delta.toFixed(3)),
                timestamp: new Date().toISOString()
            };
            publishEvent(tipo, event);
        }
    }

    // verificar temperatura alta
    if (avg > TEMP_HIGH_THRESHOLD) {
        const event = {
            eventId: `${Date.now()}-${Math.random().toString(16).slice(2,8)}`,
            type: "temperatura_alta",
            avg: Number(avg.toFixed(3)),
            timestamp: new Date().toISOString()
        };
        publishEvent("temperatura_alta", event);
    }

    lastAvg = avg;
});

function publishEvent(type, obj) {
    const topic = `${TOPIC_EVENT_BASE}/${type}`;
    const payload = JSON.stringify(obj);
    client.publish(topic, payload, { qos: 1 }, err => {
        if (err) console.error("[CAT] erro ao publicar evento:", err);
        else console.log(`[CAT][EVT] ${topic} -> ${payload}`);
    });
}