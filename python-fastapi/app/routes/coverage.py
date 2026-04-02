import json
from datetime import date
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.fhir_mapper import coverage_to_fhir, search_bundle
from app.models.coverage import CoverageModel
from app.models.patient import PatientModel
from app.services.coverage_service import save_coverage

router = APIRouter(prefix="/fhir/Coverage", tags=["Coverage"])
FHIR_JSON = "application/fhir+json"


@router.get("/{coverage_id}")
async def read_coverage(coverage_id: UUID, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(CoverageModel).where(CoverageModel.id == coverage_id))
    coverage = result.scalars().first()
    if not coverage:
        raise HTTPException(status_code=404, detail="Coverage not found")
    return Response(content=json.dumps(coverage_to_fhir(coverage), default=str), media_type=FHIR_JSON)


@router.get("")
async def search_coverages(
    beneficiary: UUID | None = None, db: AsyncSession = Depends(get_db)
):
    if beneficiary:
        result = await db.execute(
            select(CoverageModel).where(CoverageModel.patient_id == beneficiary)
        )
    else:
        result = await db.execute(select(CoverageModel))
    coverages = result.scalars().all()
    fhir_resources = [coverage_to_fhir(c) for c in coverages]
    return Response(
        content=json.dumps(search_bundle(fhir_resources), default=str),
        media_type=FHIR_JSON,
    )


@router.post("", status_code=201)
async def create_coverage(request: Request, db: AsyncSession = Depends(get_db)):
    body = await request.json()

    patient_ref = body["beneficiary"]["reference"]
    patient_id = UUID(patient_ref.replace("Patient/", ""))
    result = await db.execute(select(PatientModel).where(PatientModel.id == patient_id))
    if not result.scalars().first():
        raise HTTPException(status_code=400, detail=f"Patient not found: {patient_id}")

    payor = body["payor"][0]
    period = body.get("period", {})

    entity = CoverageModel(
        patient_id=patient_id,
        subscriber_id=body.get("subscriberId", ""),
        status=body.get("status", "active"),
        payor_name=payor.get("display", ""),
        payor_id=payor.get("reference", "").replace("Organization/", ""),
        plan_name=payor.get("display", ""),
        relationship=body.get("relationship", {}).get("coding", [{}])[0].get("code", "self"),
        period_start=date.fromisoformat(period["start"]) if "start" in period else None,
        period_end=date.fromisoformat(period["end"]) if "end" in period else None,
    )

    saved = await save_coverage(db, entity)
    return Response(
        content=json.dumps(coverage_to_fhir(saved), default=str),
        media_type=FHIR_JSON,
        headers={"Location": f"/fhir/Coverage/{saved.id}"},
    )
