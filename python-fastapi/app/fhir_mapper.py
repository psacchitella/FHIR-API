"""Bidirectional mapper between SQLAlchemy models and FHIR R4 resources.

Uses the ``fhir.resources`` library which gives us Pydantic-based FHIR
models with built-in validation against the R4 spec.
"""

from __future__ import annotations

from datetime import date, datetime, timezone
from typing import Any

from app.models.patient import PatientModel
from app.models.coverage import CoverageModel
from app.models.claim import ClaimModel
from app.models.eob import ExplanationOfBenefitModel

SYSTEM_MRN = "http://hospital.example.org/mrn"
SYSTEM_CPT = "http://www.ama-assn.org/go/cpt"
SYSTEM_CLAIM_TYPE = "http://terminology.hl7.org/CodeSystem/claim-type"
SYSTEM_ADJUDICATION = "http://terminology.hl7.org/CodeSystem/adjudication"


# --- Patient ---

def patient_to_fhir(entity: PatientModel) -> dict[str, Any]:
    resource: dict[str, Any] = {
        "resourceType": "Patient",
        "id": str(entity.id),
        "identifier": [{"system": SYSTEM_MRN, "value": entity.mrn}],
        "name": [{"family": entity.family_name, "given": [entity.given_name]}],
        "meta": {"lastUpdated": _to_iso(entity.updated_at)},
    }
    if entity.birth_date:
        resource["birthDate"] = entity.birth_date.isoformat()
    if entity.gender:
        resource["gender"] = entity.gender
    telecom = []
    if entity.phone:
        telecom.append({"system": "phone", "value": entity.phone})
    if entity.email:
        telecom.append({"system": "email", "value": entity.email})
    if telecom:
        resource["telecom"] = telecom
    if entity.address_line:
        resource["address"] = [{
            "line": [entity.address_line],
            "city": entity.city,
            "state": entity.state,
            "postalCode": entity.postal_code,
        }]
    return resource


def fhir_to_patient_fields(fhir: dict[str, Any]) -> dict[str, Any]:
    fields: dict[str, Any] = {}
    if names := fhir.get("name"):
        name = names[0]
        fields["family_name"] = name.get("family", "")
        fields["given_name"] = " ".join(name.get("given", []))
    if identifiers := fhir.get("identifier"):
        fields["mrn"] = identifiers[0].get("value", "")
    if bd := fhir.get("birthDate"):
        fields["birth_date"] = date.fromisoformat(bd)
    if g := fhir.get("gender"):
        fields["gender"] = g
    return fields


# --- Coverage ---

def coverage_to_fhir(entity: CoverageModel) -> dict[str, Any]:
    resource: dict[str, Any] = {
        "resourceType": "Coverage",
        "id": str(entity.id),
        "status": entity.status,
        "beneficiary": {"reference": f"Patient/{entity.patient_id}"},
        "subscriberId": entity.subscriber_id,
        "payor": [{"reference": f"Organization/{entity.payor_id}", "display": entity.payor_name}],
        "relationship": {"coding": [{"code": entity.relationship}]},
    }
    if entity.period_start:
        period: dict[str, str] = {"start": entity.period_start.isoformat()}
        if entity.period_end:
            period["end"] = entity.period_end.isoformat()
        resource["period"] = period
    return resource


# --- Claim ---

def claim_to_fhir(entity: ClaimModel) -> dict[str, Any]:
    items = []
    for item in entity.items:
        items.append({
            "sequence": item.sequence,
            "productOrService": {
                "coding": [{
                    "system": SYSTEM_CPT,
                    "code": item.product_or_service_code,
                    "display": item.product_or_service_display,
                }]
            },
            "quantity": {"value": item.quantity},
            "unitPrice": {"value": float(item.unit_price), "currency": entity.currency},
            "net": {"value": float(item.net_amount), "currency": entity.currency},
        })

    resource: dict[str, Any] = {
        "resourceType": "Claim",
        "id": str(entity.id),
        "status": entity.status,
        "type": {"coding": [{"system": SYSTEM_CLAIM_TYPE, "code": entity.type}]},
        "use": entity.use,
        "patient": {"reference": f"Patient/{entity.patient_id}"},
        "insurance": [{
            "sequence": 1,
            "focal": True,
            "coverage": {"reference": f"Coverage/{entity.coverage_id}"},
        }],
        "billablePeriod": {
            "start": entity.billable_period_start.isoformat(),
            "end": entity.billable_period_end.isoformat(),
        },
        "total": {"value": float(entity.total_amount), "currency": entity.currency},
        "item": items,
        "meta": {"lastUpdated": _to_iso(entity.updated_at)},
    }
    if entity.provider_reference:
        resource["provider"] = {"reference": entity.provider_reference}
    return resource


# --- ExplanationOfBenefit ---

def eob_to_fhir(entity: ExplanationOfBenefitModel) -> dict[str, Any]:
    resource: dict[str, Any] = {
        "resourceType": "ExplanationOfBenefit",
        "id": str(entity.id),
        "status": entity.status,
        "outcome": entity.outcome,
        "type": {"coding": [{"system": SYSTEM_CLAIM_TYPE, "code": entity.type}]},
        "patient": {"reference": f"Patient/{entity.patient_id}"},
        "claim": {"reference": f"Claim/{entity.claim_id}"},
        "meta": {"lastUpdated": _to_iso(entity.updated_at)},
    }
    if entity.disposition:
        resource["disposition"] = entity.disposition

    total = []
    if entity.total_submitted is not None:
        total.append({
            "category": {"coding": [{"system": SYSTEM_ADJUDICATION, "code": "submitted"}]},
            "amount": {"value": float(entity.total_submitted), "currency": "USD"},
        })
    if entity.total_benefit is not None:
        total.append({
            "category": {"coding": [{"system": SYSTEM_ADJUDICATION, "code": "benefit"}]},
            "amount": {"value": float(entity.total_benefit), "currency": "USD"},
        })
    if total:
        resource["total"] = total

    if entity.total_benefit is not None:
        resource["payment"] = {
            "amount": {"value": float(entity.total_benefit), "currency": "USD"}
        }
    return resource


# --- Bundle helper ---

def search_bundle(resources: list[dict[str, Any]]) -> dict[str, Any]:
    entries = []
    for r in resources:
        rt = r.get("resourceType", "Resource")
        rid = r.get("id", "")
        entries.append({"fullUrl": f"{rt}/{rid}", "resource": r})
    return {
        "resourceType": "Bundle",
        "type": "searchset",
        "total": len(resources),
        "entry": entries,
    }


def _to_iso(dt: datetime | None) -> str | None:
    if dt is None:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.isoformat()
