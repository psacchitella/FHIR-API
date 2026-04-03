const pool = require('../config/database');
const { getRedis } = require('../config/redis');
const config = require('../config');

const CACHE_PREFIX = 'coverage:';

async function isPatientEligible(patientId) {
  const cacheKey = `${CACHE_PREFIX}${patientId}`;
  const redis = getRedis();

  if (redis) {
    try {
      const cached = await redis.get(cacheKey);
      if (cached !== null) {
        console.log(`Coverage cache hit for patient ${patientId}`);
        return cached === '1';
      }
    } catch { /* cache miss, fall through */ }
  }

  console.log(`Coverage cache miss for patient ${patientId}, querying DB`);
  const result = await pool.query(
    'SELECT 1 FROM coverages WHERE patient_id = $1 AND status = $2 LIMIT 1',
    [patientId, 'active']
  );
  const eligible = result.rows.length > 0;

  if (redis) {
    try {
      await redis.set(cacheKey, eligible ? '1' : '0', 'EX', config.coverageCacheTtl);
    } catch { /* cache write failure is non-fatal */ }
  }

  return eligible;
}

async function saveCoverage(fields) {
  const result = await pool.query(
    `INSERT INTO coverages (patient_id, subscriber_id, payor_name, payor_id, plan_name,
     group_number, status, relationship, period_start, period_end)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) RETURNING *`,
    [fields.patient_id, fields.subscriber_id, fields.payor_name, fields.payor_id,
     fields.plan_name, fields.group_number, fields.status, fields.relationship,
     fields.period_start, fields.period_end]
  );

  const redis = getRedis();
  if (redis && fields.status === 'active') {
    try {
      await redis.set(`${CACHE_PREFIX}${fields.patient_id}`, '1', 'EX', config.coverageCacheTtl);
    } catch { /* non-fatal */ }
  }

  return result.rows[0];
}

async function findById(id) {
  const result = await pool.query('SELECT * FROM coverages WHERE id = $1', [id]);
  return result.rows[0] || null;
}

async function findByPatientId(patientId) {
  const result = await pool.query('SELECT * FROM coverages WHERE patient_id = $1', [patientId]);
  return result.rows;
}

module.exports = { isPatientEligible, saveCoverage, findById, findByPatientId };
