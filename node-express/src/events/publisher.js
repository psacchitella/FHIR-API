const { Kafka } = require('kafkajs');
const config = require('../config');

const CLAIM_EVENTS_TOPIC = 'fhir.claim.events';
const EOB_EVENTS_TOPIC = 'fhir.eob.events';

let producer = null;
let connected = false;

async function startProducer() {
  try {
    const kafka = new Kafka({
      clientId: 'fhir-claim-api-node',
      brokers: config.kafka.brokers,
      retry: { retries: 3 },
    });
    producer = kafka.producer();
    await producer.connect();
    connected = true;
    console.log('Kafka producer started');
  } catch (err) {
    console.warn('Kafka unavailable, events will be logged only:', err.message);
    connected = false;
  }
}

async function stopProducer() {
  if (producer && connected) {
    await producer.disconnect();
  }
}

async function publishClaimEvent(claimId, patientId, eventType, detail) {
  const event = {
    claimId,
    patientId,
    eventType,
    detail,
    timestamp: new Date().toISOString(),
  };

  const topic = eventType === 'EOB_GENERATED' ? EOB_EVENTS_TOPIC : CLAIM_EVENTS_TOPIC;
  console.log(`Publishing ${eventType} to ${topic} for claim ${claimId}`);

  if (producer && connected) {
    await producer.send({
      topic,
      messages: [{ key: claimId, value: JSON.stringify(event) }],
    });
  } else {
    console.log('Event (no Kafka):', JSON.stringify(event));
  }
}

module.exports = { startProducer, stopProducer, publishClaimEvent };
