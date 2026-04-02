"""Kafka event publisher for claim lifecycle events.

Publishes to two topics:
  - fhir.claim.events: CLAIM_SUBMITTED, CLAIM_ADJUDICATED, CLAIM_DENIED
  - fhir.eob.events:   EOB_GENERATED
"""

from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from enum import Enum
from typing import Any

from aiokafka import AIOKafkaProducer

from app.config import settings

logger = logging.getLogger(__name__)

CLAIM_EVENTS_TOPIC = "fhir.claim.events"
EOB_EVENTS_TOPIC = "fhir.eob.events"


class EventType(str, Enum):
    CLAIM_SUBMITTED = "CLAIM_SUBMITTED"
    CLAIM_ADJUDICATED = "CLAIM_ADJUDICATED"
    CLAIM_DENIED = "CLAIM_DENIED"
    CLAIM_CANCELLED = "CLAIM_CANCELLED"
    EOB_GENERATED = "EOB_GENERATED"


_producer: AIOKafkaProducer | None = None


async def start_producer() -> None:
    global _producer
    try:
        _producer = AIOKafkaProducer(
            bootstrap_servers=settings.kafka_bootstrap_servers,
            value_serializer=lambda v: json.dumps(v, default=str).encode("utf-8"),
            key_serializer=lambda k: k.encode("utf-8") if k else None,
        )
        await _producer.start()
        logger.info("Kafka producer started")
    except Exception:
        logger.warning("Kafka unavailable — events will be logged only")
        _producer = None


async def stop_producer() -> None:
    global _producer
    if _producer:
        await _producer.stop()
        _producer = None


async def publish_claim_event(
    claim_id: str,
    patient_id: str,
    event_type: EventType,
    detail: str,
) -> None:
    event: dict[str, Any] = {
        "claimId": claim_id,
        "patientId": patient_id,
        "eventType": event_type.value,
        "detail": detail,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }

    topic = EOB_EVENTS_TOPIC if event_type == EventType.EOB_GENERATED else CLAIM_EVENTS_TOPIC
    logger.info("Publishing %s to %s for claim %s", event_type.value, topic, claim_id)

    if _producer:
        await _producer.send(topic, value=event, key=claim_id)
    else:
        logger.info("Event (no Kafka): %s", event)
