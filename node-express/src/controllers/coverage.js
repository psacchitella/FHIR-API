const { Router } = require('express');
const pool = require('../config/database');
const coverageService = require('../services/coverageService');
const { coverageToFhir, searchBundle } = require('../models/fhirMapper');

const router = Router();
const FHIR_JSON = 'application/fhir+json';

router.get('/:id', async (req, res) => {
  const coverage = await coverageService.findById(req.params.id);
  if (!coverage) return res.status(404).json({ error: 'Coverage not found' });
  res.type(FHIR_JSON).json(coverageToFhir(coverage));
});

router.get('/', async (req, res) => {
  let coverages = [];
  if (req.query.beneficiary) {
    coverages = await coverageService.findByPatientId(req.query.beneficiary);
  }
  res.type(FHIR_JSON).json(searchBundle(coverages.map(coverageToFhir)));
});

router.post('/', async (req, res) => {
  const body = req.body;
  const patientId = body.beneficiary.reference.replace('Patient/', '');

  const patientCheck = await pool.query('SELECT 1 FROM patients WHERE id = $1', [patientId]);
  if (!patientCheck.rows[0]) return res.status(400).json({ error: `Patient not found: ${patientId}` });

  const payor = body.payor[0];
  const period = body.period || {};

  const saved = await coverageService.saveCoverage({
    patient_id: patientId,
    subscriber_id: body.subscriberId || '',
    status: body.status || 'active',
    payor_name: payor.display || '',
    payor_id: (payor.reference || '').replace('Organization/', ''),
    plan_name: payor.display || '',
    group_number: null,
    relationship: body.relationship?.coding?.[0]?.code || 'self',
    period_start: period.start || null,
    period_end: period.end || null,
  });

  res.status(201).type(FHIR_JSON)
    .set('Location', `/fhir/Coverage/${saved.id}`)
    .json(coverageToFhir(saved));
});

module.exports = router;
