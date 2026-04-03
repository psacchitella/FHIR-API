const SYSTEM_MRN = 'http://hospital.example.org/mrn';
const SYSTEM_CPT = 'http://www.ama-assn.org/go/cpt';
const SYSTEM_CLAIM_TYPE = 'http://terminology.hl7.org/CodeSystem/claim-type';
const SYSTEM_ADJUDICATION = 'http://terminology.hl7.org/CodeSystem/adjudication';

// --- Patient ---

function patientToFhir(row) {
  const resource = {
    resourceType: 'Patient',
    id: row.id,
    identifier: [{ system: SYSTEM_MRN, value: row.mrn }],
    name: [{ family: row.family_name, given: [row.given_name] }],
    meta: { lastUpdated: row.updated_at },
  };
  if (row.birth_date) resource.birthDate = row.birth_date;
  if (row.gender) resource.gender = row.gender;
  const telecom = [];
  if (row.phone) telecom.push({ system: 'phone', value: row.phone });
  if (row.email) telecom.push({ system: 'email', value: row.email });
  if (telecom.length) resource.telecom = telecom;
  if (row.address_line) {
    resource.address = [{
      line: [row.address_line], city: row.city,
      state: row.state, postalCode: row.postal_code,
    }];
  }
  return resource;
}

function fhirToPatientFields(body) {
  const fields = {};
  if (body.name?.[0]) {
    fields.family_name = body.name[0].family || '';
    fields.given_name = (body.name[0].given || []).join(' ');
  }
  if (body.identifier?.[0]) fields.mrn = body.identifier[0].value;
  if (body.birthDate) fields.birth_date = body.birthDate;
  if (body.gender) fields.gender = body.gender;
  return fields;
}

// --- Coverage ---

function coverageToFhir(row) {
  const resource = {
    resourceType: 'Coverage',
    id: row.id,
    status: row.status,
    beneficiary: { reference: `Patient/${row.patient_id}` },
    subscriberId: row.subscriber_id,
    payor: [{ reference: `Organization/${row.payor_id}`, display: row.payor_name }],
    relationship: { coding: [{ code: row.relationship }] },
  };
  if (row.period_start) {
    resource.period = { start: row.period_start };
    if (row.period_end) resource.period.end = row.period_end;
  }
  return resource;
}

// --- Claim ---

function claimToFhir(row, items = []) {
  const resource = {
    resourceType: 'Claim',
    id: row.id,
    status: row.status,
    type: { coding: [{ system: SYSTEM_CLAIM_TYPE, code: row.type }] },
    use: row.use,
    patient: { reference: `Patient/${row.patient_id}` },
    insurance: [{ sequence: 1, focal: true, coverage: { reference: `Coverage/${row.coverage_id}` } }],
    billablePeriod: { start: row.billable_period_start, end: row.billable_period_end },
    total: { value: parseFloat(row.total_amount), currency: row.currency },
    item: items.map(i => ({
      sequence: i.sequence,
      productOrService: {
        coding: [{ system: SYSTEM_CPT, code: i.product_or_service_code, display: i.product_or_service_display }],
      },
      quantity: { value: i.quantity },
      unitPrice: { value: parseFloat(i.unit_price), currency: row.currency },
      net: { value: parseFloat(i.net_amount), currency: row.currency },
    })),
    meta: { lastUpdated: row.updated_at },
  };
  if (row.provider_reference) resource.provider = { reference: row.provider_reference };
  return resource;
}

// --- ExplanationOfBenefit ---

function eobToFhir(row) {
  const resource = {
    resourceType: 'ExplanationOfBenefit',
    id: row.id,
    status: row.status,
    outcome: row.outcome,
    type: { coding: [{ system: SYSTEM_CLAIM_TYPE, code: row.type }] },
    patient: { reference: `Patient/${row.patient_id}` },
    claim: { reference: `Claim/${row.claim_id}` },
    meta: { lastUpdated: row.updated_at },
  };
  if (row.disposition) resource.disposition = row.disposition;
  const total = [];
  if (row.total_submitted != null) {
    total.push({
      category: { coding: [{ system: SYSTEM_ADJUDICATION, code: 'submitted' }] },
      amount: { value: parseFloat(row.total_submitted), currency: 'USD' },
    });
  }
  if (row.total_benefit != null) {
    total.push({
      category: { coding: [{ system: SYSTEM_ADJUDICATION, code: 'benefit' }] },
      amount: { value: parseFloat(row.total_benefit), currency: 'USD' },
    });
  }
  if (total.length) resource.total = total;
  if (row.total_benefit != null) {
    resource.payment = { amount: { value: parseFloat(row.total_benefit), currency: 'USD' } };
  }
  return resource;
}

// --- Bundle ---

function searchBundle(resources) {
  return {
    resourceType: 'Bundle',
    type: 'searchset',
    total: resources.length,
    entry: resources.map(r => ({
      fullUrl: `${r.resourceType}/${r.id}`,
      resource: r,
    })),
  };
}

module.exports = {
  patientToFhir, fhirToPatientFields,
  coverageToFhir, claimToFhir, eobToFhir, searchBundle,
};
