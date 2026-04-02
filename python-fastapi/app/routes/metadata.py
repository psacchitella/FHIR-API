import json
from datetime import datetime, timezone

from fastapi import APIRouter, Response

router = APIRouter(prefix="/fhir", tags=["Metadata"])
FHIR_JSON = "application/fhir+json"


@router.get("/metadata")
async def capability_statement():
    """FHIR CapabilityStatement — every FHIR server must publish this."""
    cs = {
        "resourceType": "CapabilityStatement",
        "status": "active",
        "date": datetime.now(timezone.utc).isoformat(),
        "kind": "instance",
        "fhirVersion": "4.0.1",
        "format": ["application/fhir+json"],
        "software": {
            "name": "FHIR Claim Processing API (Python)",
            "version": "1.0.0",
        },
        "rest": [{
            "mode": "server",
            "resource": [
                _resource("Patient", ["read", "create", "update", "search-type"]),
                _resource("Coverage", ["read", "create", "search-type"]),
                _resource("Claim", ["read", "create", "search-type"]),
                _resource("ExplanationOfBenefit", ["read", "search-type"]),
            ],
            "operation": [{
                "name": "adjudicate",
                "definition": "Claim/{id}/$adjudicate",
            }],
        }],
    }
    return Response(content=json.dumps(cs, default=str), media_type=FHIR_JSON)


def _resource(type_name: str, interactions: list[str]) -> dict:
    return {
        "type": type_name,
        "interaction": [{"code": i} for i in interactions],
    }
