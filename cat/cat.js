

const mqtt = require('mqtt');

const BROKER = process.env.BROKER || 'mqtt://localhost:1883';
const CLIENT_ID = 'cat-service';
const WINDOW_MS = parseInt(process.env.WINDOW_MS || '120000', 10); // 120s
const COMPUTE_INTERVAL_MS = parseInt(process.env.COMPUTE_INTERVAL_MS || '60000', 10); // 60s
const TOPIC_SENSOR = 'sensors/temperature/#';
const TOPIC_EVENT_BASE = 'cat/events';

const client = mqtt.connect(BROKER, { clientId: CLIENT_ID });

let readings = []; // array of { sensorId, value, ts (ms) }
let lastAvg = null;

client.on('connect', () => {
    console.log('CAT connected to broker', BROKER);
    client.subscribe(TOPIC_SENSOR, { qos: 1 }, (err) => {
        if (err) console.error('Subscribe error', err);
        else console.log('Subscribed to', TOPIC_SENSOR);
    });
    // Compute periodically
    setInterval(computeAndPublish, COMPUTE_INTERVAL_MS);
});

client.on('message', (topic, message) => {
    try {
        const payload = JSON.parse(message.toString());
        // expect payload { sensorId, value, ts } where ts is ISO8601 optional
        const ts = payload.ts ? new Date(payload.ts).getTime() : Date.now();
        if (!isFinite(ts)) {
            console.warn('Invalid timestamp from sensor:', payload);
            return;
        }
        const v = Number(payload.value);
        if (!isFinite(v)) {
            console.warn('Invalid value from sensor:', payload);
            return;
        }
        readings.push({ sensorId: payload.sensorId || extractSensorId(topic), value: v, ts });
        // Optionally prune here to keep array small
        pruneOld();
        // debug log
        console.log(`[IN ] ${topic} => ${payload.sensorId} = ${v} @ ${new Date(ts).toISOString()}`);
    } catch (e) {
        console.warn('Failed to parse message from', topic, e.message);
        // Optionally publish parse error to a monitoring topic
    }
});

function extractSensorId(topic) {
    const parts = topic.split('/');
    return parts.length ? parts[parts.length - 1] : 'unknown';
}

function pruneOld() {
    const now = Date.now();
    const cutoff = now - WINDOW_MS;
    // Keep only readings with ts >= cutoff
    readings = readings.filter(r => r.ts >= cutoff);
}

function computeAndPublish() {
    pruneOld();
    const now = Date.now();
    const windowStart = new Date(now - WINDOW_MS).toISOString();
    const windowEnd = new Date(now).toISOString();

    if (readings.length === 0) {
        console.log('No readings in window -> skipping compute');
        lastAvg = null; // or keep previous? choose to set null
        return;
    }

    const sum = readings.reduce((acc, r) => acc + r.value, 0);
    const avg = sum / readings.length;

    console.log(`Computed average over last ${WINDOW_MS/1000}s: avg=${avg.toFixed(3)} from ${readings.length} readings`);

    // check sudden increase
    if (lastAvg !== null && Math.abs(avg - lastAvg) >= 5) {
        const event = {
            eventId: `${Date.now()}-${Math.random().toString(16).slice(2,8)}`,
            type: 'sudden_increase',
            avg: Number(avg.toFixed(3)),
            prevAvg: Number(lastAvg.toFixed(3)),
            delta: Number((avg - lastAvg).toFixed(3)),
            windowStart,
            windowEnd,
            timestamp: new Date().toISOString()
        };
        publishEvent('sudden_increase', event);
    }

    // check temp high
    if (avg > 200) {
        const event = {
            eventId: `${Date.now()}-${Math.random().toString(16).slice(2,8)}`,
            type: 'temp_high',
            avg: Number(avg.toFixed(3)),
            windowStart,
            windowEnd,
            timestamp: new Date().toISOString()
        };
        publishEvent('temp_high', event);
    }

    lastAvg = avg;
}

function publishEvent(type, eventObj) {
    const topic = `${TOPIC_EVENT_BASE}/${type}`;
    const payload = JSON.stringify(eventObj);
    client.publish(topic, payload, { qos: 1 }, (err) => {
        if (err) console.error('Publish event error', err);
        else console.log(`[EVT] ${topic} => ${payload}`);
    });
}