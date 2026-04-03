/**
 * Core bill-pay adjudication engine (Node.js).
 *
 * Simulates X12 837->835 in FHIR terms:
 *   Claim (837) -> adjudicate -> ExplanationOfBenefit (835/EOB)
 *
 * Adjudication model:
 *   - $50 copay per claim
 *   - $500 deductible
 *   - 80/20 coinsurance after deductible
 */

const pool = require('../config/database');
const coverageService = require('./coverageService');
const { publishClaimEvent } = require('../events/publisher');

const COPAY = 50.00;
const DEDUCTIBLE = 500.00;
const COINSURANCE_RATE = 0.20;

async function submitClaim(fields, items) {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    const claimResult = await client.query(
      `INSERT INTO claims (patient_id, coverage_id, status, type, use,
       billable_period_start, billable_period_end, total_amount, currency, provider_reference)
       VALUES ($1,$2,'active',$3,$4,$5,$6,$7,$8,$9) RETURNING *`,
      [fields.patient_id, fields.coverage_id, fields.type, fields.use,
       fields.billable_period_start, fields.billable_period_end,
       fields.total_amount, fields.currency || 'USD', fields.provider_reference]
    );
    const claim = claimResult.rows[0];

    const savedItems = [];
    for (const item of items) {
      const itemResult = await client.query(
        `INSERT INTO claim_items (claim_id, sequence, product_or_service_code,
         product_or_service_display, serviced_date, quantity, unit_price, net_amount)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8) RETURNING *`,
        [claim.id, item.sequence, item.product_or_service_code,
         item.product_or_service_display, item.serviced_date,
         item.quantity, item.unit_price, item.net_amount]
      );
      savedItems.push(itemResult.rows[0]);
    }

    await client.query('COMMIT');

    await publishClaimEvent(
      claim.id, claim.patient_id, 'CLAIM_SUBMITTED',
      `Claim submitted: $${claim.total_amount}`
    );

    return { claim, items: savedItems };
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

async function getClaim(claimId) {
  const claimResult = await pool.query('SELECT * FROM claims WHERE id = $1', [claimId]);
  if (!claimResult.rows[0]) return null;
  const itemsResult = await pool.query(
    'SELECT * FROM claim_items WHERE claim_id = $1 ORDER BY sequence', [claimId]
  );
  return { claim: claimResult.rows[0], items: itemsResult.rows };
}

async function getClaimsByPatient(patientId) {
  const claimsResult = await pool.query('SELECT * FROM claims WHERE patient_id = $1', [patientId]);
  const claims = [];
  for (const claim of claimsResult.rows) {
    const itemsResult = await pool.query(
      'SELECT * FROM claim_items WHERE claim_id = $1 ORDER BY sequence', [claim.id]
    );
    claims.push({ claim, items: itemsResult.rows });
  }
  return claims;
}

async function adjudicate(claimId) {
  const data = await getClaim(claimId);
  if (!data) throw new Error(`Claim not found: ${claimId}`);
  const { claim } = data;

  if (claim.status !== 'active') {
    throw new Error(`Claim ${claimId} is not in active status`);
  }

  // Step 1: Check coverage eligibility (Redis-cached)
  const eligible = await coverageService.isPatientEligible(claim.patient_id);
  if (!eligible) {
    await pool.query("UPDATE claims SET status = 'cancelled' WHERE id = $1", [claimId]);

    await publishClaimEvent(claimId, claim.patient_id, 'CLAIM_DENIED', 'No active coverage');

    const eobResult = await pool.query(
      `INSERT INTO explanation_of_benefits
       (claim_id, patient_id, status, outcome, type, total_submitted, total_benefit,
        patient_responsibility, disposition)
       VALUES ($1,$2,'active','error',$3,$4,0,$4,'Denied: no active coverage found') RETURNING *`,
      [claimId, claim.patient_id, claim.type, claim.total_amount]
    );
    return eobResult.rows[0];
  }

  // Step 2: Calculate adjudication amounts
  const submitted = parseFloat(claim.total_amount);
  const afterCopay = Math.max(submitted - COPAY, 0);
  const deductibleApplied = Math.min(afterCopay, DEDUCTIBLE);
  const afterDeductible = afterCopay - deductibleApplied;
  const coinsurance = Math.round(afterDeductible * COINSURANCE_RATE * 100) / 100;
  const patientTotal = Math.round((COPAY + deductibleApplied + coinsurance) * 100) / 100;
  const insurerPays = Math.round(Math.max(submitted - patientTotal, 0) * 100) / 100;

  // Step 3: Generate EOB
  const eobResult = await pool.query(
    `INSERT INTO explanation_of_benefits
     (claim_id, patient_id, status, outcome, type, total_submitted, total_benefit,
      patient_responsibility, deductible_applied, coinsurance_amount, copay_amount, disposition)
     VALUES ($1,$2,'active','complete',$3,$4,$5,$6,$7,$8,$9,'Claim adjudicated successfully')
     RETURNING *`,
    [claimId, claim.patient_id, claim.type, submitted, insurerPays,
     patientTotal, deductibleApplied, coinsurance, COPAY]
  );
  const eob = eobResult.rows[0];

  console.log(`Adjudicated claim ${claimId}: submitted=$${submitted}, insurer=$${insurerPays}, patient=$${patientTotal}`);

  // Step 4: Publish Kafka events
  await publishClaimEvent(claimId, claim.patient_id, 'CLAIM_ADJUDICATED',
    `Adjudicated: insurer pays $${insurerPays}, patient owes $${patientTotal}`);
  await publishClaimEvent(claimId, claim.patient_id, 'EOB_GENERATED',
    `EOB ${eob.id} generated`);

  return eob;
}

async function getEob(eobId) {
  const result = await pool.query('SELECT * FROM explanation_of_benefits WHERE id = $1', [eobId]);
  return result.rows[0] || null;
}

async function getEobByClaimId(claimId) {
  const result = await pool.query('SELECT * FROM explanation_of_benefits WHERE claim_id = $1', [claimId]);
  return result.rows[0] || null;
}

async function getEobsByPatient(patientId) {
  const result = await pool.query('SELECT * FROM explanation_of_benefits WHERE patient_id = $1', [patientId]);
  return result.rows;
}

module.exports = {
  submitClaim, getClaim, getClaimsByPatient, adjudicate,
  getEob, getEobByClaimId, getEobsByPatient,
};
