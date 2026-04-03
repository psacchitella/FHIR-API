const express = require('express');
const config = require('./config');
const { startProducer, stopProducer } = require('./events/publisher');

const app = express();
app.use(express.json({ type: ['application/json', 'application/fhir+json'] }));

// Routes
app.use('/fhir', require('./controllers/metadata'));
app.use('/fhir/Patient', require('./controllers/patient'));
app.use('/fhir/Coverage', require('./controllers/coverage'));
app.use('/fhir/Claim', require('./controllers/claim'));
app.use('/fhir/ExplanationOfBenefit', require('./controllers/eob'));

app.get('/health', (req, res) => res.json({ status: 'ok' }));

// Error handler
app.use((err, req, res, _next) => {
  console.error(err);
  res.status(500).json({ error: err.message });
});

async function start() {
  await startProducer();
  app.listen(config.port, () => {
    console.log(`FHIR Claim API (Node.js) listening on port ${config.port}`);
  });
}

process.on('SIGTERM', async () => {
  await stopProducer();
  process.exit(0);
});

start().catch(console.error);

module.exports = app;
