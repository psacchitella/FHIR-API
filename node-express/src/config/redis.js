const Redis = require('ioredis');
const config = require('./index');

let redis = null;

function getRedis() {
  if (!redis) {
    try {
      redis = new Redis({
        host: config.redis.host,
        port: config.redis.port,
        retryStrategy: (times) => (times > 3 ? null : Math.min(times * 200, 2000)),
        lazyConnect: true,
      });
      redis.on('error', (err) => {
        console.warn('Redis unavailable, caching disabled:', err.message);
        redis = null;
      });
    } catch {
      console.warn('Redis unavailable, caching disabled');
      redis = null;
    }
  }
  return redis;
}

module.exports = { getRedis };
