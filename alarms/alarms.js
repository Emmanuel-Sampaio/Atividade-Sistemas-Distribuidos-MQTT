

const mqtt = require('mqtt');

const BROKER = process.env.BROKER || 'mqtt://localhost:1883';
const CLIENT_ID = 'alarms-service';
const TOPIC = 'cat/events/#';

const client = mqtt.connect(BROKER, { clientId: CLIENT_ID });

client.on('connect', () => {
    console.log('Alarms service connected to', BROKER);
    client.subscribe(TOPIC, { qos: 1 }, (err) => {
        if (err) console.error('Subscribe error', err);
        else console.log('Subscribed to', TOPIC);
    });
});

client.on('message', (topic, message) => {
    try {
        const ev = JSON.parse(message.toString());
        handleEvent(topic, ev);
    } catch (e) {
        console.warn('Invalid event payload on', topic, e.message);
    }
});

function handleEvent(topic, ev) {
    const t = ev.type || topic.split('/').pop();

    const now = new Date().toISOString();
    if (t === 'sudden_increase') {
        console.log(`[ALARM][${now}] Sudden temperature increase detected! id=${ev.eventId} delta=${ev.delta} avg=${ev.avg} prev=${ev.prevAvg}`);
    } else if (t === 'temp_high') {
        console.log(`[ALARM][${now}] TEMPERATURE HIGH! id=${ev.eventId} avg=${ev.avg}`);

    } else {
        console.log(`[ALARM][${now}] Event ${t}:`, ev);
    }
}