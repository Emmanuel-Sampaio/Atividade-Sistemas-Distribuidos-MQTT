// alarms.js (CommonJS)
const mqtt = require("mqtt");

const BROKER = process.env.BROKER || "mqtt://localhost:1883";
const CLIENT_ID = (process.env.CLIENT_ID || "alarms-service") + "-" + Math.random().toString(16).slice(2,6);
const TOPIC = process.env.TOPIC || "cat/alerta/#";

const client = mqtt.connect(BROKER, { clientId: CLIENT_ID, reconnectPeriod: 2000 });

client.on("connect", () => {
    console.log(`[Alarms] conectado ao broker (${BROKER}) clientId=${CLIENT_ID}`);
    client.subscribe(TOPIC, { qos: 1 }, err => {
        if (err) console.error("[Alarms] subscribe error:", err.message || err);
        else console.log(`[Alarms] subscrito em '${TOPIC}'`);
    });
});

client.on("error", err => console.error("[Alarms] erro MQTT:", err && err.message ? err.message : err));

client.on("message", (topic, payload) => {

    if (!topic.startsWith("cat/alerta/")) return;

    const raw = payload && payload.toString ? payload.toString() : String(payload);
    let ev = null;

    try { ev = JSON.parse(raw); }
    catch (e) {
        const v = parseFloat(raw);
        if (Number.isFinite(v)) ev = { type: topic.split("/").pop(), avg: v, raw: raw };
        else {
            console.warn("[Alarms] payload inválido ignorado:", raw);
            return;
        }
    }

    const tipo = ev.type || topic.split("/").pop();
    const ts = ev.timestamp || ev.ts || new Date().toISOString();

    // Regras de log/ação de alarme (expanda aqui se quiser alertas reais)
    if (tipo === "aumento_repentino") {
        console.log(`[ALARM][${ts}] AUMENTO REPENTINO detectado! eventId=${ev.eventId || "n/a"} delta=${ev.delta} avg=${ev.avg} prevAvg=${ev.prevAvg}`);
    } else if (tipo === "reducao_repentina" || tipo === "redução_repentina" || tipo === "reducaorepentina") {
        console.log(`[ALARM][${ts}] REDUÇÃO REPENTINA detectada! eventId=${ev.eventId || "n/a"} delta=${ev.delta} avg=${ev.avg} prevAvg=${ev.prevAvg}`);
    } else if (tipo === "temperatura_alta") {
        console.log(`[ALARM][${ts}] TEMPERATURA ALTA! eventId=${ev.eventId || "n/a"} avg=${ev.avg}`);
    } else {
        console.log(`[ALARM][${ts}] Evento '${tipo}':`, ev);
    }
});