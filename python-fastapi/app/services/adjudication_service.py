"""Core bill-pay adjudication engine.

Simulates the X12 837->835 flow in FHIR terms:
  Claim (837) -> adjudicate -> ExplanationOfBenefit (835/EOB)

Applies a simplified adjudication model:
  - $50 copay per claim
  - $500 deductible
  - 80/20 coinsurance after deductible

In production this would integrate with a payer rules engine.
"""

from __future__ import annotations

import logging
from decimal import Decimal, ROUND_HALF_UP
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.models.claim import ClaimModel
from app.models.eob import ExplanationOfBenefitModel
from app.services.coverage_service import is_patient_eligible
from app.events.publisher import EventType, publish_claim_event

logger = logging.getLogger(__name__)

COPAY = Decimal("50.00")
DEDUCTIBLE = Decimal("500.00")
COINSURANCE_RATE = Decimal("0.20")  # patient pays 20%


async def submit_claim(db: AsyncSession, claim: ClaimModel) -> ClaimModel:
    claim.status = "active"
    db.add(claim)
    await db.commit()
    await db.refresh(claim, ["items"])

    await publish_claim_event(
        str(claim.id),
        str(claim.patient_id),
        EventType.CLAIM_SUBMITTED,
        f"Claim submitted: ${claim.total_amount}",
    )
    return claim


async def get_claim(db: AsyncSession, claim_id: UUID) -> ClaimModel | None:
    result = await db.execute(
        select(ClaimModel)
        .options(selectinload(ClaimModel.items))
        .where(ClaimModel.id == claim_id)
    )
    return result.scalars().first()


async def get_claims_by_patient(db: AsyncSession, patient_id: UUID) -> list[ClaimModel]:
    result = await db.execute(
        select(ClaimModel)
        .options(selectinload(ClaimModel.items))
        .where(ClaimModel.patient_id == patient_id)
    )
    return list(result.scalars().all())


async def adjudicate(db: AsyncSession, claim_id: UUID) -> ExplanationOfBenefitModel:
    claim = await get_claim(db, claim_id)
    if claim is None:
        raise ValueError(f"Claim not found: {claim_id}")
    if claim.status != "active":
        raise ValueError(f"Claim {claim_id} is not in active status")

    # Step 1: Check coverage eligibility (Redis-cached)
    eligible = await is_patient_eligible(db, claim.patient_id)
    if not eligible:
        claim.status = "cancelled"
        await db.commit()

        await publish_claim_event(
            str(claim_id), str(claim.patient_id),
            EventType.CLAIM_DENIED, "No active coverage",
        )

        eob = ExplanationOfBenefitModel(
            claim_id=claim.id,
            patient_id=claim.patient_id,
            status="active",
            outcome="error",
            type=claim.type,
            total_submitted=claim.total_amount,
            total_benefit=Decimal("0.00"),
            patient_responsibility=claim.total_amount,
            disposition="Denied: no active coverage found",
        )
        db.add(eob)
        await db.commit()
        await db.refresh(eob)
        return eob

    # Step 2: Calculate adjudication amounts
    submitted = claim.total_amount
    after_copay = max(submitted - COPAY, Decimal("0.00"))
    deductible_applied = min(after_copay, DEDUCTIBLE)
    after_deductible = after_copay - deductible_applied
    coinsurance = (after_deductible * COINSURANCE_RATE).quantize(
        Decimal("0.01"), rounding=ROUND_HALF_UP
    )
    patient_total = COPAY + deductible_applied + coinsurance
    insurer_pays = max(submitted - patient_total, Decimal("0.00"))

    # Step 3: Generate EOB
    eob = ExplanationOfBenefitModel(
        claim_id=claim.id,
        patient_id=claim.patient_id,
        status="active",
        outcome="complete",
        type=claim.type,
        total_submitted=submitted,
        total_benefit=insurer_pays,
        patient_responsibility=patient_total,
        deductible_applied=deductible_applied,
        coinsurance_amount=coinsurance,
        copay_amount=COPAY,
        disposition="Claim adjudicated successfully",
    )
    db.add(eob)
    await db.commit()
    await db.refresh(eob)

    logger.info(
        "Adjudicated claim %s: submitted=$%s, insurer=$%s, patient=$%s",
        claim_id, submitted, insurer_pays, patient_total,
    )

    # Step 4: Publish Kafka events
    await publish_claim_event(
        str(claim_id), str(claim.patient_id),
        EventType.CLAIM_ADJUDICATED,
        f"Adjudicated: insurer pays ${insurer_pays}, patient owes ${patient_total}",
    )
    await publish_claim_event(
        str(claim_id), str(claim.patient_id),
        EventType.EOB_GENERATED,
        f"EOB {eob.id} generated",
    )

    return eob


async def get_eob(db: AsyncSession, eob_id: UUID) -> ExplanationOfBenefitModel | None:
    result = await db.execute(
        select(ExplanationOfBenefitModel).where(ExplanationOfBenefitModel.id == eob_id)
    )
    return result.scalars().first()


async def get_eob_by_claim(db: AsyncSession, claim_id: UUID) -> ExplanationOfBenefitModel | None:
    result = await db.execute(
        select(ExplanationOfBenefitModel).where(ExplanationOfBenefitModel.claim_id == claim_id)
    )
    return result.scalars().first()


async def get_eobs_by_patient(db: AsyncSession, patient_id: UUID) -> list[ExplanationOfBenefitModel]:
    result = await db.execute(
        select(ExplanationOfBenefitModel).where(ExplanationOfBenefitModel.patient_id == patient_id)
    )
    return list(result.scalars().all())
