const { Router } = require('express');
const adjudicationService = require('../services/adjudicationService');
const { eobToFhir, searchBundle } = require('../models/fhirMapper');

const router = Router();
const FHIR_JSON = 'application/fhir+json';

router.get('/:id', async (req, res) => {
  const eob = await adjudicationService.getEob(req.params.id);
  if (!eob) return res.status(404).json({ error: 'ExplanationOfBenefit not found' });
  res.type(FHIR_JSON).json(eobToFhir(eob));
});

router.get('/', async (req, res) => {
  let eobs = [];
  if (req.query.claim) {
    const eob = await adjudicationService.getEobByClaimId(req.query.claim);
    if (eob) eobs = [eob];
  } else if (req.query.patient) {
    eobs = await adjudicationService.getEobsByPatient(req.query.patient);
  }
  res.type(FHIR_JSON).json(searchBundle(eobs.map(eobToFhir)));
});

module.exports = router;
