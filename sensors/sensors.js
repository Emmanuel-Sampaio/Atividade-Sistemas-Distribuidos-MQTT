

const mqtt = require('mqtt');

function parseArgs() {
    const args = {};
    process.argv.slice(2).forEach(a => {
        const [k, v] = a.replace(/^--/, '').split('=');
        args[k] = (v === undefined) ? true : v;
    });
    return args;
}

const args = parseArgs();
const BROKER = args.broker || 'mqtt://localhost:1883';
const COUNT = parseInt(args.count || '3', 10);
const INTERVAL_SEC = parseFloat(args.interval || '60'); // default 60s
const BASELINE = parseFloat(args.baseline || '100'); // base temp
const NOISE = parseFloat(args.noise || '1.5');
const FAST = args.fast || false; // shorthand for dev: faster interval
const SPIKE_AFTER = args['spike-after'] ? parseInt(args['spike-after'], 10) : null; // seconds until spike starts
const SPIKE_DURATION = args['spike-duration'] ? parseInt(args['spike-duration'], 10) : 0; // count of publishes to spike
const SPIKE_MAG = args['spike-magnitude'] ? parseFloat(args['spike-magnitude']) : 50;

const intervalMs = FAST ? (INTERVAL_SEC * 1000 / 6) : INTERVAL_SEC * 1000; // fast mode shrinks for demo

const client = mqtt.connect(BROKER, { clientId: 'sensors-sim' });

client.on('connect', () => {
    console.log('Sensors simulator connected to', BROKER);
    startSimulation();
});

client.on('error', (err) => {
    console.error('MQTT error:', err);
});

function randNormal(mean = 0, std = 1) {
    // Box-Muller
    let u = 0, v = 0;
    while (u === 0) u = Math.random();
    while (v === 0) v = Math.random();
    return mean + std * Math.sqrt(-2.0 * Math.log(u)) * Math.cos(2.0 * Math.PI * v);
}

function startSimulation() {
    const sensors = [];
    for (let i = 0; i < COUNT; i++) {
        sensors.push({
            id: `s${i + 1}`,
            baseline: BASELINE + (i * 2), // small offset per sensor
            spikeRemaining: 0
        });
    }

    let elapsed = 0; // seconds

    // On-demand spike trigger if asked
    if (SPIKE_AFTER !== null) {
        setTimeout(() => {
            console.log('Triggering spike on all sensors for demo');
            sensors.forEach(s => s.spikeRemaining = SPIKE_DURATION || 3);
        }, SPIKE_AFTER * 1000);
    }

    // Publish immediately then on interval
    publishAll();
    setInterval(() => {
        elapsed += INTERVAL_SEC;
        // Optionally start spikes at some time
        publishAll();
    }, intervalMs);

    function publishAll() {
        const now = new Date().toISOString();
        sensors.forEach(s => {
            let value = s.baseline + randNormal(0, NOISE);
            if (s.spikeRemaining > 0) {
                value += SPIKE_MAG;
                s.spikeRemaining--;
            }
            const payload = JSON.stringify({
                sensorId: s.id,
                value: Number(value.toFixed(3)),
                ts: now
            });
            const topic = `sensors/temperature/${s.id}`;
            client.publish(topic, payload, { qos: 1 }, (err) => {
                if (err) console.error('Publish error', err);
            });
            console.log(`[PUB] ${topic} => ${payload}`);
        });
    }
}