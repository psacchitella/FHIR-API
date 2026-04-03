const { Router } = require('express');

const router = Router();
const FHIR_JSON = 'application/fhir+json';

router.get('/metadata', (req, res) => {
  res.type(FHIR_JSON).json({
    resourceType: 'CapabilityStatement',
    status: 'active',
    date: new Date().toISOString(),
    kind: 'instance',
    fhirVersion: '4.0.1',
    format: ['application/fhir+json'],
    software: { name: 'FHIR Claim Processing API (Node.js)', version: '1.0.0' },
    rest: [{
      mode: 'server',
      resource: [
        resource('Patient', ['read', 'create', 'update', 'search-type']),
        resource('Coverage', ['read', 'create', 'search-type']),
        resource('Claim', ['read', 'create', 'search-type']),
        resource('ExplanationOfBenefit', ['read', 'search-type']),
      ],
      operation: [{ name: 'adjudicate', definition: 'Claim/{id}/$adjudicate' }],
    }],
  });
});

function resource(type, interactions) {
  return { type, interaction: interactions.map(code => ({ code })) };
}

module.exports = router;
