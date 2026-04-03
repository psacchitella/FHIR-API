const config = {
  port: process.env.PORT || 8081,
  database: {
    host: process.env.DB_HOST || 'localhost',
    port: parseInt(process.env.DB_PORT || '5432'),
    database: process.env.DB_NAME || 'fhir_claims',
    user: process.env.DB_USER || 'fhir',
    password: process.env.DB_PASSWORD || 'fhir',
  },
  redis: {
    host: process.env.REDIS_HOST || 'localhost',
    port: parseInt(process.env.REDIS_PORT || '6379'),
  },
  kafka: {
    brokers: (process.env.KAFKA_BROKERS || 'localhost:9092').split(','),
  },
  fhir: {
    baseUrl: process.env.FHIR_BASE_URL || 'http://localhost:8081/fhir',
  },
  coverageCacheTtl: 900, // 15 minutes in seconds
};

module.exports = config;
