import uuid
from datetime import datetime
from decimal import Decimal

from sqlalchemy import ForeignKey, Numeric, String
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class ExplanationOfBenefitModel(Base):
    __tablename__ = "explanation_of_benefits"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    claim_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("claims.id"), unique=True, nullable=False)
    patient_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("patients.id"), nullable=False)
    status: Mapped[str] = mapped_column(String(20), default="active")
    outcome: Mapped[str] = mapped_column(String(30), nullable=False)
    type: Mapped[str] = mapped_column(String(20), nullable=False)
    total_submitted: Mapped[Decimal | None] = mapped_column(Numeric(10, 2))
    total_benefit: Mapped[Decimal | None] = mapped_column(Numeric(10, 2))
    patient_responsibility: Mapped[Decimal | None] = mapped_column(Numeric(10, 2))
    deductible_applied: Mapped[Decimal | None] = mapped_column(Numeric(10, 2))
    coinsurance_amount: Mapped[Decimal | None] = mapped_column(Numeric(10, 2))
    copay_amount: Mapped[Decimal | None] = mapped_column(Numeric(10, 2))
    disposition: Mapped[str | None] = mapped_column(String(500))
    created_at: Mapped[datetime] = mapped_column(default=datetime.utcnow)
    updated_at: Mapped[datetime] = mapped_column(
        default=datetime.utcnow, onupdate=datetime.utcnow
    )
