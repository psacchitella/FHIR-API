from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.fhir_mapper import fhir_to_patient_fields, patient_to_fhir, search_bundle
from app.models.patient import PatientModel

router = APIRouter(prefix="/fhir/Patient", tags=["Patient"])
FHIR_JSON = "application/fhir+json"


@router.get("/{patient_id}")
async def read_patient(patient_id: UUID, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(PatientModel).where(PatientModel.id == patient_id))
    patient = result.scalars().first()
    if not patient:
        raise HTTPException(status_code=404, detail="Patient not found")
    return Response(
        content=_json_dumps(patient_to_fhir(patient)),
        media_type=FHIR_JSON,
    )


@router.get("")
async def search_patients(
    identifier: str | None = None, db: AsyncSession = Depends(get_db)
):
    if identifier:
        result = await db.execute(
            select(PatientModel).where(PatientModel.mrn == identifier)
        )
    else:
        result = await db.execute(select(PatientModel))
    patients = result.scalars().all()
    fhir_resources = [patient_to_fhir(p) for p in patients]
    return Response(
        content=_json_dumps(search_bundle(fhir_resources)),
        media_type=FHIR_JSON,
    )


@router.post("", status_code=201)
async def create_patient(request: Request, db: AsyncSession = Depends(get_db)):
    body = await request.json()
    fields = fhir_to_patient_fields(body)
    patient = PatientModel(**fields)
    db.add(patient)
    await db.commit()
    await db.refresh(patient)
    return Response(
        content=_json_dumps(patient_to_fhir(patient)),
        media_type=FHIR_JSON,
        headers={"Location": f"/fhir/Patient/{patient.id}"},
    )


@router.put("/{patient_id}")
async def update_patient(
    patient_id: UUID, request: Request, db: AsyncSession = Depends(get_db)
):
    result = await db.execute(select(PatientModel).where(PatientModel.id == patient_id))
    patient = result.scalars().first()
    if not patient:
        raise HTTPException(status_code=404, detail="Patient not found")

    body = await request.json()
    fields = fhir_to_patient_fields(body)
    for key, value in fields.items():
        setattr(patient, key, value)
    await db.commit()
    await db.refresh(patient)
    return Response(
        content=_json_dumps(patient_to_fhir(patient)),
        media_type=FHIR_JSON,
    )


def _json_dumps(obj) -> str:
    import json
    return json.dumps(obj, default=str)
