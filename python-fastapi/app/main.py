"""FHIR R4 Claim Processing API — Python / FastAPI implementation."""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.events.publisher import start_producer, stop_producer
from app.services.coverage_service import close_redis
from app.routes import patient, coverage, claim, eob, metadata

logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(app: FastAPI):
    await start_producer()
    yield
    await stop_producer()
    await close_redis()


app = FastAPI(
    title="FHIR Claim Processing API",
    description=(
        "FHIR R4 compliant API for healthcare claim submission, "
        "adjudication, and ExplanationOfBenefit generation"
    ),
    version="1.0.0",
    lifespan=lifespan,
)

app.include_router(metadata.router)
app.include_router(patient.router)
app.include_router(coverage.router)
app.include_router(claim.router)
app.include_router(eob.router)


@app.get("/health")
async def health():
    return {"status": "ok"}
