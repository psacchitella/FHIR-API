const { Router } = require('express');
const pool = require('../config/database');
const { patientToFhir, fhirToPatientFields, searchBundle } = require('../models/fhirMapper');

const router = Router();
const FHIR_JSON = 'application/fhir+json';

router.get('/:id', async (req, res) => {
  const result = await pool.query('SELECT * FROM patients WHERE id = $1', [req.params.id]);
  if (!result.rows[0]) return res.status(404).json({ error: 'Patient not found' });
  res.type(FHIR_JSON).json(patientToFhir(result.rows[0]));
});

router.get('/', async (req, res) => {
  let result;
  if (req.query.identifier) {
    result = await pool.query('SELECT * FROM patients WHERE mrn = $1', [req.query.identifier]);
  } else {
    result = await pool.query('SELECT * FROM patients');
  }
  res.type(FHIR_JSON).json(searchBundle(result.rows.map(patientToFhir)));
});

router.post('/', async (req, res) => {
  const fields = fhirToPatientFields(req.body);
  const result = await pool.query(
    `INSERT INTO patients (family_name, given_name, mrn, birth_date, gender)
     VALUES ($1,$2,$3,$4,$5) RETURNING *`,
    [fields.family_name, fields.given_name, fields.mrn, fields.birth_date, fields.gender]
  );
  const patient = result.rows[0];
  res.status(201).type(FHIR_JSON)
    .set('Location', `/fhir/Patient/${patient.id}`)
    .json(patientToFhir(patient));
});

router.put('/:id', async (req, res) => {
  const fields = fhirToPatientFields(req.body);
  const result = await pool.query(
    `UPDATE patients SET family_name=$1, given_name=$2, mrn=$3, birth_date=$4, gender=$5,
     updated_at=NOW() WHERE id=$6 RETURNING *`,
    [fields.family_name, fields.given_name, fields.mrn, fields.birth_date, fields.gender, req.params.id]
  );
  if (!result.rows[0]) return res.status(404).json({ error: 'Patient not found' });
  res.type(FHIR_JSON).json(patientToFhir(result.rows[0]));
});

module.exports = router;
