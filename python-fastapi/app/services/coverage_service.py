"""Coverage service with Redis-cached eligibility checks.

Coverage eligibility is checked on every claim submission. Redis caching
avoids repeated DB lookups — the cache TTL (15 min) balances freshness
with performance.
"""

from __future__ import annotations

import logging
from uuid import UUID

import redis.asyncio as aioredis
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import settings
from app.models.coverage import CoverageModel

logger = logging.getLogger(__name__)

CACHE_PREFIX = "coverage:"

_redis: aioredis.Redis | None = None


async def get_redis() -> aioredis.Redis | None:
    global _redis
    if _redis is None:
        try:
            _redis = aioredis.from_url(settings.redis_url, decode_responses=True)
            await _redis.ping()
            logger.info("Redis connected")
        except Exception:
            logger.warning("Redis unavailable — caching disabled")
            _redis = None
    return _redis


async def close_redis() -> None:
    global _redis
    if _redis:
        await _redis.close()
        _redis = None


async def is_patient_eligible(db: AsyncSession, patient_id: UUID) -> bool:
    cache_key = f"{CACHE_PREFIX}{patient_id}"

    r = await get_redis()
    if r:
        cached = await r.get(cache_key)
        if cached is not None:
            logger.debug("Coverage cache hit for patient %s", patient_id)
            return cached == "1"

    logger.debug("Coverage cache miss for patient %s, querying DB", patient_id)
    result = await db.execute(
        select(CoverageModel).where(
            CoverageModel.patient_id == patient_id,
            CoverageModel.status == "active",
        )
    )
    eligible = result.scalars().first() is not None

    if r:
        await r.set(
            cache_key, "1" if eligible else "0", ex=settings.coverage_cache_ttl_seconds
        )

    return eligible


async def save_coverage(db: AsyncSession, entity: CoverageModel) -> CoverageModel:
    db.add(entity)
    await db.commit()
    await db.refresh(entity)

    # Update cache
    r = await get_redis()
    if r and entity.status == "active":
        await r.set(
            f"{CACHE_PREFIX}{entity.patient_id}",
            "1",
            ex=settings.coverage_cache_ttl_seconds,
        )

    return entity
