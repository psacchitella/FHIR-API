const { Router } = require('express');
const pool = require('../config/database');
const adjudicationService = require('../services/adjudicationService');
const { claimToFhir, eobToFhir, searchBundle } = require('../models/fhirMapper');

const router = Router();
const FHIR_JSON = 'application/fhir+json';

router.get('/:id', async (req, res) => {
  if (req.params.id.includes('$')) return res.status(400).json({ error: 'Invalid ID' });
  const data = await adjudicationService.getClaim(req.params.id);
  if (!data) return res.status(404).json({ error: 'Claim not found' });
  res.type(FHIR_JSON).json(claimToFhir(data.claim, data.items));
});

router.get('/', async (req, res) => {
  if (!req.query.patient) return res.type(FHIR_JSON).json(searchBundle([]));
  const claims = await adjudicationService.getClaimsByPatient(req.query.patient);
  res.type(FHIR_JSON).json(searchBundle(claims.map(d => claimToFhir(d.claim, d.items))));
});

router.post('/', async (req, res) => {
  const body = req.body;
  const patientId = body.patient.reference.replace('Patient/', '');
  const coverageId = body.insurance[0].coverage.reference.replace('Coverage/', '');

  const patientCheck = await pool.query('SELECT 1 FROM patients WHERE id = $1', [patientId]);
  if (!patientCheck.rows[0]) return res.status(400).json({ error: `Patient not found: ${patientId}` });

  const coverageCheck = await pool.query('SELECT 1 FROM coverages WHERE id = $1', [coverageId]);
  if (!coverageCheck.rows[0]) return res.status(400).json({ error: `Coverage not found: ${coverageId}` });

  const fields = {
    patient_id: patientId,
    coverage_id: coverageId,
    type: body.type.coding[0].code,
    use: body.use || 'claim',
    billable_period_start: body.billablePeriod.start,
    billable_period_end: body.billablePeriod.end,
    total_amount: body.total.value,
    currency: body.total.currency || 'USD',
    provider_reference: body.provider?.reference || null,
  };

  const items = (body.item || []).map((item, i) => ({
    sequence: i + 1,
    product_or_service_code: item.productOrService.coding[0].code,
    product_or_service_display: item.productOrService.coding[0].display || null,
    serviced_date: null,
    quantity: item.quantity.value,
    unit_price: item.unitPrice.value,
    net_amount: item.net.value,
  }));

  const data = await adjudicationService.submitClaim(fields, items);
  res.status(201).type(FHIR_JSON)
    .set('Location', `/fhir/Claim/${data.claim.id}`)
    .json(claimToFhir(data.claim, data.items));
});

// $adjudicate operation
router.post('/:id/\\$adjudicate', async (req, res) => {
  try {
    const eob = await adjudicationService.adjudicate(req.params.id);
    res.type(FHIR_JSON).json(eobToFhir(eob));
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

module.exports = router;
