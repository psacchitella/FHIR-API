# FHIR R4 Claim Processing API

A healthcare interoperability API implementing the **FHIR R4** standard for claim submission, adjudication, and ExplanationOfBenefit (EOB) generation — the modern replacement for X12 837/835 EDI in bill-pay workflows.

**Both implementations share the same database schema, API contract, and adjudication logic — demonstrating polyglot microservice architecture.**

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                         FHIR REST API                                │
│                                                                      │
│    ┌──────────────────────┐      ┌──────────────────────┐           │
│    │   Java / Spring Boot │      │  Python / FastAPI     │           │
│    │   (port 8080)        │      │  (port 8000)          │           │
│    │   HAPI FHIR R4       ���      │  fhir.resources       │           │
│    └──────────┬───────────┘      └──────────┬───────────┘           │
│               │          Shared Contract     │                       │
│               └──────────────┬───────────────┘                       │
│                              │                                       │
│                ┌─────────────▼──────────────┐                        │
│                │    Adjudication Engine      │                        │
│                │  Copay / Deductible / 80-20 │                        │
│                └──────┬─────────────┬───────┘                        │
│                       │             │                                 │
│               ┌───────▼───┐  ┌─────▼──────┐                         │
│               │ Coverage   │  │ Claim Event│                         │
│               │ Cache      │  │ Publisher  │                         │
│               └──────┬────┘  └─────┬──────┘                         │
└──────────────────────┼─────────────┼─────────────────────────────────┘
                       │             │
               ┌───────▼──┐  ┌──────▼─────┐  ┌──────��───────┐
               │  Redis   │  │   Kafka    │  │ PostgreSQL   │
               │ (Cache)  │  │  (Events)  │  │ (Persistence)│
               └──────────┘  └────────────┘  └──────────────┘
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

## Implementations

| | Java / Spring Boot | Python / FastAPI |
|---|---|---|
| **Directory** | [`java-spring/`](java-spring/) | [`python-fastapi/`](python-fastapi/) |
| **Runtime** | Java 21 | Python 3.12 |
| **Framework** | Spring Boot 3.3 | FastAPI + Uvicorn |
| **FHIR Library** | HAPI FHIR 7.4 | fhir.resources 7.1 |
| **ORM** | Spring Data JPA / Hibernate | SQLAlchemy 2.0 (async) |
| **Redis Client** | Spring Data Redis | redis-py (async) |
| **Kafka Client** | Spring Kafka | aiokafka |
| **Port** | 8080 | 8000 |
| **Build** | Maven | pip |

## Quick Start

### Java (Spring Boot)

```bash
cd java-spring
docker compose up -d
curl http://localhost:8080/fhir/metadata | jq .
```

### Python (FastAPI)

```bash
cd python-fastapi
docker compose up -d
curl http://localhost:8000/fhir/metadata | jq .
```

### End-to-End Flow (either implementation)

```bash
PORT=8080  # or 8000 for Python

# 1. Create a patient
curl -s -X POST http://localhost:$PORT/fhir/Patient \
  -H "Content-Type: application/fhir+json" \
  -d '{
    "resourceType": "Patient",
    "identifier": [{"system": "http://hospital.example.org/mrn", "value": "MRN-001"}],
    "name": [{"family": "Smith", "given": ["John"]}],
    "birthDate": "1985-03-15",
    "gender": "male"
  }' | jq .

# 2. Add coverage (replace <PATIENT_ID>)
curl -s -X POST http://localhost:$PORT/fhir/Coverage \
  -H "Content-Type: application/fhir+json" \
  -d '{
    "resourceType": "Coverage",
    "status": "active",
    "beneficiary": {"reference": "Patient/<PATIENT_ID>"},
    "subscriberId": "SUB-12345",
    "payor": [{"reference": "Organization/bcbs-co", "display": "Blue Cross Blue Shield CO"}],
    "relationship": {"coding": [{"code": "self"}]},
    "period": {"start": "2025-01-01", "end": "2025-12-31"}
  }' | jq .

# 3. Submit a claim (replace IDs)
curl -s -X POST http://localhost:$PORT/fhir/Claim \
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
  }' | jq .

# 4. Adjudicate → generates ExplanationOfBenefit
curl -s -X POST http://localhost:$PORT/fhir/Claim/<CLAIM_ID>/\$adjudicate | jq .

# 5. View the EOB (payment breakdown)
curl -s http://localhost:$PORT/fhir/ExplanationOfBenefit?patient=<PATIENT_ID> | jq .
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

**Example**: $1,500 claim
- After copay: $1,450
- After deductible: $950
- Coinsurance (20%): $190
- **Patient owes: $740** (copay $50 + deductible $500 + coinsurance $190)
- **Insurer pays: $760**

## Kafka Events

Claim lifecycle events published for downstream consumers (billing systems, notifications, analytics):

| Topic | Events |
|-------|--------|
| `fhir.claim.events` | CLAIM_SUBMITTED, CLAIM_ADJUDICATED, CLAIM_DENIED, CLAIM_CANCELLED |
| `fhir.eob.events` | EOB_GENERATED |

## Shared Infrastructure

Both implementations use the same backing services and database schema:

- **PostgreSQL 16** — Shared [schema.sql](java-spring/src/main/resources/schema.sql) with 5 tables, 7 indexes
- **Redis 7** — Coverage eligibility cache with 15-min TTL
- **Apache Kafka** — 2 topics, 3 partitions each for claim lifecycle events
- **Docker Compose** — Each implementation has its own compose file for the full stack

## Project Structure

```
FHIR-API/
├── java-spring/                   # Java 21 / Spring Boot 3.3
│   ├── src/main/java/.../fhir/
│   │   ├── config/                # FHIR, Redis, Kafka configuration
│   │   ├── controller/            # REST endpoints (5 controllers)
│   │   ├── model/entity/          # JPA entities
│   │   ├── model/fhir/            # FHIR R4 resource mapper
│   │   ├── repository/            # Spring Data JPA
│   │   ├── service/               # Adjudication + Coverage + Patient
│   │   └── event/                 # Kafka publisher
│   ├── src/main/resources/
│   │   ├── schema.sql             # PostgreSQL DDL (shared)
│   │   └── application.yml
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── pom.xml
│
├── python-fastapi/                # Python 3.12 / FastAPI
│   ├── app/
│   │   ├── models/                # SQLAlchemy async models
│   │   ├── routes/                # FastAPI routers (5 modules)
│   │   ├── services/              # Adjudication + Coverage
│   │   ├── events/                # Kafka publisher (aiokafka)
│   │   ├── fhir_mapper.py         # FHIR R4 resource mapper
│   │   ├── database.py            # Async SQLAlchemy engine
│   │   ├── config.py              # Pydantic settings
│   │   └── main.py                # FastAPI app
│   ├── tests/
│   ���   └── test_adjudication.py   # Unit tests
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── requirements.txt
│
└── README.md
```

## License

MIT
