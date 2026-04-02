import json
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.fhir_mapper import eob_to_fhir, search_bundle
from app.services import adjudication_service

router = APIRouter(prefix="/fhir/ExplanationOfBenefit", tags=["ExplanationOfBenefit"])
FHIR_JSON = "application/fhir+json"


@router.get("/{eob_id}")
async def read_eob(eob_id: UUID, db: AsyncSession = Depends(get_db)):
    eob = await adjudication_service.get_eob(db, eob_id)
    if not eob:
        raise HTTPException(status_code=404, detail="ExplanationOfBenefit not found")
    return Response(content=json.dumps(eob_to_fhir(eob), default=str), media_type=FHIR_JSON)


@router.get("")
async def search_eobs(
    patient: UUID | None = None,
    claim: UUID | None = None,
    db: AsyncSession = Depends(get_db),
):
    if claim:
        eob = await adjudication_service.get_eob_by_claim(db, claim)
        eobs = [eob] if eob else []
    elif patient:
        eobs = await adjudication_service.get_eobs_by_patient(db, patient)
    else:
        eobs = []

    fhir_resources = [eob_to_fhir(e) for e in eobs]
    return Response(
        content=json.dumps(search_bundle(fhir_resources), default=str),
        media_type=FHIR_JSON,
    )
