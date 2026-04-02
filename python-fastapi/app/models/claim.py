import uuid
from datetime import date, datetime
from decimal import Decimal

from sqlalchemy import ForeignKey, Numeric, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class ClaimModel(Base):
    __tablename__ = "claims"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    patient_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("patients.id"), nullable=False)
    coverage_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("coverages.id"), nullable=False)
    status: Mapped[str] = mapped_column(String(20), default="draft")
    type: Mapped[str] = mapped_column(String(20), nullable=False)
    use: Mapped[str] = mapped_column(String(20), default="claim")
    billable_period_start: Mapped[date] = mapped_column(nullable=False)
    billable_period_end: Mapped[date] = mapped_column(nullable=False)
    total_amount: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False)
    currency: Mapped[str] = mapped_column(String(3), default="USD")
    provider_reference: Mapped[str | None] = mapped_column(String(200))
    facility_reference: Mapped[str | None] = mapped_column(String(200))
    created_at: Mapped[datetime] = mapped_column(default=datetime.utcnow)
    updated_at: Mapped[datetime] = mapped_column(
        default=datetime.utcnow, onupdate=datetime.utcnow
    )

    items: Mapped[list["ClaimItemModel"]] = relationship(
        back_populates="claim", cascade="all, delete-orphan"
    )


class ClaimItemModel(Base):
    __tablename__ = "claim_items"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    claim_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("claims.id", ondelete="CASCADE"), nullable=False)
    sequence: Mapped[int] = mapped_column(nullable=False)
    product_or_service_code: Mapped[str] = mapped_column(String(20), nullable=False)
    product_or_service_display: Mapped[str | None] = mapped_column(String(200))
    serviced_date: Mapped[date | None]
    quantity: Mapped[int] = mapped_column(default=1)
    unit_price: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False)
    net_amount: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False)

    claim: Mapped["ClaimModel"] = relationship(back_populates="items")
