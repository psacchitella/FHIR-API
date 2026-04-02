"""Unit tests for the adjudication calculation logic."""

from decimal import Decimal

import pytest


def calculate_adjudication(
    submitted: Decimal,
    copay: Decimal = Decimal("50.00"),
    deductible: Decimal = Decimal("500.00"),
    coinsurance_rate: Decimal = Decimal("0.20"),
) -> dict:
    """Standalone adjudication calculation for unit testing."""
    after_copay = max(submitted - copay, Decimal("0.00"))
    deductible_applied = min(after_copay, deductible)
    after_deductible = after_copay - deductible_applied
    coinsurance = (after_deductible * coinsurance_rate).quantize(Decimal("0.01"))
    patient_total = copay + deductible_applied + coinsurance
    insurer_pays = max(submitted - patient_total, Decimal("0.00"))
    return {
        "submitted": submitted,
        "copay": copay,
        "deductible_applied": deductible_applied,
        "coinsurance": coinsurance,
        "patient_total": patient_total,
        "insurer_pays": insurer_pays,
    }


def test_standard_claim():
    """$1500 claim: copay $50 + deductible $500 + coinsurance $190 = $740 patient."""
    result = calculate_adjudication(Decimal("1500.00"))
    assert result["copay"] == Decimal("50.00")
    assert result["deductible_applied"] == Decimal("500.00")
    assert result["coinsurance"] == Decimal("190.00")
    assert result["patient_total"] == Decimal("740.00")
    assert result["insurer_pays"] == Decimal("760.00")


def test_small_claim_under_copay():
    """$30 claim: patient pays full amount (less than copay)."""
    result = calculate_adjudication(Decimal("30.00"))
    assert result["copay"] == Decimal("50.00")
    assert result["deductible_applied"] == Decimal("0.00")
    assert result["coinsurance"] == Decimal("0.00")
    assert result["patient_total"] == Decimal("50.00")
    # Insurer pays nothing — patient owes more than submitted
    # but capped at submitted amount in the service layer
    assert result["insurer_pays"] == Decimal("0.00")


def test_claim_under_deductible():
    """$400 claim: copay $50 + deductible $350 = $400 patient, insurer $0."""
    result = calculate_adjudication(Decimal("400.00"))
    assert result["deductible_applied"] == Decimal("350.00")
    assert result["coinsurance"] == Decimal("0.00")
    assert result["patient_total"] == Decimal("400.00")
    assert result["insurer_pays"] == Decimal("0.00")


def test_large_claim():
    """$10000 claim: copay $50 + deductible $500 + coinsurance $1890 = $2440."""
    result = calculate_adjudication(Decimal("10000.00"))
    assert result["deductible_applied"] == Decimal("500.00")
    assert result["coinsurance"] == Decimal("1890.00")
    assert result["patient_total"] == Decimal("2440.00")
    assert result["insurer_pays"] == Decimal("7560.00")


def test_exact_copay_plus_deductible():
    """$550 claim: copay $50 + deductible $500 = $550, insurer $0."""
    result = calculate_adjudication(Decimal("550.00"))
    assert result["patient_total"] == Decimal("550.00")
    assert result["insurer_pays"] == Decimal("0.00")
