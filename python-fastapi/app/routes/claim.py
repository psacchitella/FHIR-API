import json
from datetime import date
from decimal import Decimal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.fhir_mapper import claim_to_fhir, eob_to_fhir, search_bundle
from app.models.claim import ClaimModel, ClaimItemModel
from app.models.patient import PatientModel
from app.models.coverage import CoverageModel
from app.services import adjudication_service

router = APIRouter(prefix="/fhir/Claim", tags=["Claim"])
FHIR_JSON = "application/fhir+json"


@router.get("/{claim_id}")
async def read_claim(claim_id: UUID, db: AsyncSession = Depends(get_db)):
    claim = await adjudication_service.get_claim(db, claim_id)
    if not claim:
        raise HTTPException(status_code=404, detail="Claim not found")
    return Response(content=json.dumps(claim_to_fhir(claim), default=str), media_type=FHIR_JSON)


@router.get("")
async def search_claims(
    patient: UUID | None = None, db: AsyncSession = Depends(get_db)
):
    if not patient:
        return Response(
            content=json.dumps(search_bundle([]), default=str),
            media_type=FHIR_JSON,
        )
    claims = await adjudication_service.get_claims_by_patient(db, patient)
    fhir_resources = [claim_to_fhir(c) for c in claims]
    return Response(
        content=json.dumps(search_bundle(fhir_resources), default=str),
        media_type=FHIR_JSON,
    )


@router.post("", status_code=201)
async def create_claim(request: Request, db: AsyncSession = Depends(get_db)):
    body = await request.json()

    # Resolve patient
    patient_ref = body["patient"]["reference"]
    patient_id = UUID(patient_ref.replace("Patient/", ""))
    result = await db.execute(select(PatientModel).where(PatientModel.id == patient_id))
    if not result.scalars().first():
        raise HTTPException(status_code=400, detail=f"Patient not found: {patient_id}")

    # Resolve coverage
    coverage_ref = body["insurance"][0]["coverage"]["reference"]
    coverage_id = UUID(coverage_ref.replace("Coverage/", ""))
    result = await db.execute(select(CoverageModel).where(CoverageModel.id == coverage_id))
    if not result.scalars().first():
        raise HTTPException(status_code=400, detail=f"Coverage not found: {coverage_id}")

    billable = body["billablePeriod"]
    entity = ClaimModel(
        patient_id=patient_id,
        coverage_id=coverage_id,
        type=body["type"]["coding"][0]["code"],
        use=body.get("use", "claim"),
        billable_period_start=date.fromisoformat(billable["start"]),
        billable_period_end=date.fromisoformat(billable["end"]),
        total_amount=Decimal(str(body["total"]["value"])),
        provider_reference=body.get("provider", {}).get("reference"),
    )

    # Map line items
    for i, fhir_item in enumerate(body.get("item", []), start=1):
        coding = fhir_item["productOrService"]["coding"][0]
        item = ClaimItemModel(
            sequence=i,
            product_or_service_code=coding["code"],
            product_or_service_display=coding.get("display"),
            quantity=int(fhir_item["quantity"]["value"]),
            unit_price=Decimal(str(fhir_item["unitPrice"]["value"])),
            net_amount=Decimal(str(fhir_item["net"]["value"])),
        )
        entity.items.append(item)

    saved = await adjudication_service.submit_claim(db, entity)
    return Response(
        content=json.dumps(claim_to_fhir(saved), default=str),
        media_type=FHIR_JSON,
        headers={"Location": f"/fhir/Claim/{saved.id}"},
    )


@router.post("/{claim_id}/$adjudicate")
async def adjudicate_claim(claim_id: UUID, db: AsyncSession = Depends(get_db)):
    """FHIR $adjudicate operation — processes claim and returns EOB."""
    try:
        eob = await adjudication_service.adjudicate(db, claim_id)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    return Response(
        content=json.dumps(eob_to_fhir(eob), default=str),
        media_type=FHIR_JSON,
    )
