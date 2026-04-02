import uuid
from datetime import date, datetime

from sqlalchemy import ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class CoverageModel(Base):
    __tablename__ = "coverages"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    patient_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("patients.id"), nullable=False)
    subscriber_id: Mapped[str] = mapped_column(String(100), nullable=False)
    payor_name: Mapped[str] = mapped_column(String(200), nullable=False)
    payor_id: Mapped[str] = mapped_column(String(100), nullable=False)
    plan_name: Mapped[str] = mapped_column(String(200), nullable=False)
    group_number: Mapped[str | None] = mapped_column(String(50))
    status: Mapped[str] = mapped_column(String(20), default="active")
    relationship: Mapped[str] = mapped_column(String(20), default="self")
    period_start: Mapped[date | None]
    period_end: Mapped[date | None]
    created_at: Mapped[datetime] = mapped_column(default=datetime.utcnow)
    updated_at: Mapped[datetime] = mapped_column(
        default=datetime.utcnow, onupdate=datetime.utcnow
    )
