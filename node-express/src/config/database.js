const { Pool } = require('pg');
const config = require('./index');

const pool = new Pool(config.database);

pool.on('error', (err) => {
  console.error('Unexpected PostgreSQL error:', err);
});

module.exports = pool;
