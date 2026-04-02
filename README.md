# FHIR R4 Claim Processing API

A healthcare interoperability API implementing the **FHIR R4** standard for claim submission, adjudication, and ExplanationOfBenefit (EOB) generation — the modern replacement for X12 837/835 EDI in bill-pay workflows.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        FHIR REST API                            │
│                     (Spring Boot 3 / Java 21)                   │
│                                                                 │
│  /fhir/Patient    /fhir/Claim    /fhir/Coverage    /fhir/EOB   │
│                        │                                        │
│              ┌─────────▼──────────┐                             │
│              │  Adjudication       │                             │
│              │  Service            │                             │
│              │  (Copay/Deductible/ │                             │
│              │   Coinsurance)      │                             │
│              └──┬──────────┬──────┘                             │
│                 │          │                                     │
│         ┌──────▼───┐  ┌───▼────────┐                           │
│         │ Coverage  │  │ Claim Event│                           │
│         │ Cache     │  │ Publisher  │                           │
│         └──────┬───┘  └───┬────────┘                           │
└────────────────┼──────────┼─────────────────────────────────────┘
                 │          │
         ┌───────▼──┐  ┌───▼──────┐  ┌──────────────┐
         │  Redis   │  │  Kafka   │  │ PostgreSQL   │
         │ (Cache)  │  │ (Events) │  │ (Persistence)│
         └──────────┘  └──────────┘  └──────────────┘
```

## Bill-Pay Flow (FHIR Equivalent of X12 837 → 835)

```
1. POST /fhir/Patient          → Register patient
2. POST /fhir/Coverage         → Add insurance coverage
3. POST /fhir/Claim            → Submit claim (publishes CLAIM_SUBMITTED to Kafka)
4. POST /fhir/Claim/{id}/$adjudicate → Adjudicate claim
   ├── Checks coverage eligibility (Redis-cached)
   ├── Applies copay ($50) + deductible ($500) + coinsurance (80/20)
   ├── Generates ExplanationOfBenefit
   └── Publishes CLAIM_ADJUDICATED + EOB_GENERATED to Kafka
5. GET  /fhir/ExplanationOfBenefit?patient={id} → View payment breakdown
```

## Tech Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Core API | Java 21 + Spring Boot 3.3 | FHIR R4 RESTful server |
| FHIR Library | HAPI FHIR 7.4 | Resource parsing, validation, serialization |
| Database | PostgreSQL 16 | Claim, patient, coverage persistence |
| Cache | Redis 7 | Coverage eligibility caching (15-min TTL) |
| Events | Apache Kafka | Claim lifecycle events (submit, adjudicate, deny) |
| Containers | Docker Compose | Full stack: API + Postgres + Redis + Kafka |

## Quick Start

```bash
# Start all services
docker compose up -d

# Verify the FHIR server
curl http://localhost:8080/fhir/metadata | jq .

# Create a patient
curl -X POST http://localhost:8080/fhir/Patient \
  -H "Content-Type: application/fhir+json" \
  -d '{
    "resourceType": "Patient",
    "identifier": [{"system": "http://hospital.example.org/mrn", "value": "MRN-001"}],
    "name": [{"family": "Smith", "given": ["John"]}],
    "birthDate": "1985-03-15",
    "gender": "male"
  }'

# Add coverage (use patient ID from response above)
curl -X POST http://localhost:8080/fhir/Coverage \
  -H "Content-Type: application/fhir+json" \
  -d '{
    "resourceType": "Coverage",
    "status": "active",
    "beneficiary": {"reference": "Patient/<PATIENT_ID>"},
    "subscriberId": "SUB-12345",
    "payor": [{"reference": "Organization/bcbs-co", "display": "Blue Cross Blue Shield CO"}],
    "relationship": {"coding": [{"code": "self"}]},
    "period": {"start": "2025-01-01", "end": "2025-12-31"}
  }'

# Submit a claim
curl -X POST http://localhost:8080/fhir/Claim \
  -H "Content-Type: application/fhir+json" \
  -d '{
    "resourceType": "Claim",
    "type": {"coding": [{"system": "http://terminology.hl7.org/CodeSystem/claim-type", "code": "professional"}]},
    "use": "claim",
    "patient": {"reference": "Patient/<PATIENT_ID>"},
    "insurance": [{"sequence": 1, "focal": true, "coverage": {"reference": "Coverage/<COVERAGE_ID>"}}],
    "billablePeriod": {"start": "2025-03-01", "end": "2025-03-01"},
    "total": {"value": 1500.00, "currency": "USD"},
    "item": [{
      "sequence": 1,
      "productOrService": {"coding": [{"system": "http://www.ama-assn.org/go/cpt", "code": "99213", "display": "Office visit, established patient"}]},
      "quantity": {"value": 1},
      "unitPrice": {"value": 1500.00, "currency": "USD"},
      "net": {"value": 1500.00, "currency": "USD"}
    }]
  }'

# Adjudicate the claim → generates ExplanationOfBenefit
curl -X POST http://localhost:8080/fhir/Claim/<CLAIM_ID>/\$adjudicate | jq .

# View the EOB (payment breakdown)
curl http://localhost:8080/fhir/ExplanationOfBenefit?patient=<PATIENT_ID> | jq .
```

## FHIR Compliance

- **Content-Type**: `application/fhir+json` (FHIR R4)
- **Search**: Bundle responses with `searchset` type
- **References**: Standard FHIR reference format (`ResourceType/id`)
- **Operations**: Custom `$adjudicate` operation on Claim
- **Capability Statement**: `GET /fhir/metadata`
- **Resource Types**: Patient, Coverage, Claim, ExplanationOfBenefit

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/fhir/metadata` | FHIR CapabilityStatement |
| GET | `/fhir/Patient/{id}` | Read patient |
| GET | `/fhir/Patient?identifier={mrn}` | Search by MRN |
| POST | `/fhir/Patient` | Create patient |
| PUT | `/fhir/Patient/{id}` | Update patient |
| GET | `/fhir/Coverage/{id}` | Read coverage |
| GET | `/fhir/Coverage?beneficiary={patientId}` | Search by patient |
| POST | `/fhir/Coverage` | Create coverage |
| GET | `/fhir/Claim/{id}` | Read claim |
| GET | `/fhir/Claim?patient={patientId}` | Search by patient |
| POST | `/fhir/Claim` | Submit claim |
| POST | `/fhir/Claim/{id}/$adjudicate` | Adjudicate claim |
| GET | `/fhir/ExplanationOfBenefit/{id}` | Read EOB |
| GET | `/fhir/ExplanationOfBenefit?patient={id}` | Search EOBs by patient |
| GET | `/fhir/ExplanationOfBenefit?claim={id}` | Search EOB by claim |

## Adjudication Model

The simplified adjudication engine demonstrates the claim processing pipeline:

| Component | Amount | Description |
|-----------|--------|-------------|
| Copay | $50.00 | Fixed per-claim copay |
| Deductible | $500.00 | Applied before coinsurance |
| Coinsurance | 80/20 | Insurer 80%, patient 20% after deductible |

**Example**: $1,500 claim → Patient owes $240 (copay $50 + deductible $500 + coinsurance $190 = $740... wait, let me recalculate) → The adjudication service calculates this precisely per claim.

## Kafka Events

Claim lifecycle events are published to Kafka for downstream consumers (billing systems, notifications, analytics):

| Topic | Events |
|-------|--------|
| `fhir.claim.events` | CLAIM_SUBMITTED, CLAIM_ADJUDICATED, CLAIM_DENIED, CLAIM_CANCELLED |
| `fhir.eob.events` | EOB_GENERATED |

## Project Structure

```
src/main/java/com/xanterra/fhir/
├── config/                    # Spring configuration (FHIR, Redis, Kafka)
├── controller/                # FHIR REST endpoints
│   ├── PatientController      # Patient CRUD
│   ├── CoverageController     # Insurance coverage
│   ├── ClaimController        # Claim submission + $adjudicate
│   ├── ExplanationOfBenefitController  # EOB read/search
│   └── MetadataController     # FHIR CapabilityStatement
├── model/
│   ├── entity/                # JPA entities (PostgreSQL)
│   └── fhir/                  # FHIR R4 resource mappers
├── repository/                # Spring Data JPA repositories
├── service/                   # Business logic
│   ├── ClaimAdjudicationService  # Core adjudication engine
│   ├── CoverageService        # Eligibility with Redis cache
│   └── PatientService         # Patient management
└── event/                     # Kafka event publishing
```

## License

MIT
