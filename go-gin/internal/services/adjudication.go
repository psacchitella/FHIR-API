// Package services contains the core bill-pay adjudication engine.
//
// Simulates X12 837->835 in FHIR terms:
//
//	Claim (837) -> adjudicate -> ExplanationOfBenefit (835/EOB)
//
// Adjudication model:
//   - $50 copay per claim
//   - $500 deductible
//   - 80/20 coinsurance after deductible
package services

import (
	"context"
	"fmt"
	"log"
	"math"

	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/psacchitella/fhir-claim-api/internal/events"
	"github.com/psacchitella/fhir-claim-api/internal/models"
)

const (
	copay           = 50.00
	deductible      = 500.00
	coinsuranceRate = 0.20
)

type AdjudicationService struct {
	db       *pgxpool.Pool
	coverage *CoverageService
}

func NewAdjudicationService(db *pgxpool.Pool, coverage *CoverageService) *AdjudicationService {
	return &AdjudicationService{db: db, coverage: coverage}
}

func (s *AdjudicationService) SubmitClaim(ctx context.Context, c *models.Claim, items []models.ClaimItem) (*models.Claim, error) {
	tx, err := s.db.Begin(ctx)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback(ctx)

	err = tx.QueryRow(ctx,
		`INSERT INTO claims (patient_id, coverage_id, status, type, use,
		 billable_period_start, billable_period_end, total_amount, currency, provider_reference)
		 VALUES ($1,$2,'active',$3,$4,$5,$6,$7,$8,$9) RETURNING id, status, created_at, updated_at`,
		c.PatientID, c.CoverageID, c.Type, c.Use, c.BillablePeriodStart,
		c.BillablePeriodEnd, c.TotalAmount, c.Currency, c.ProviderReference,
	).Scan(&c.ID, &c.Status, &c.CreatedAt, &c.UpdatedAt)
	if err != nil {
		return nil, fmt.Errorf("insert claim: %w", err)
	}

	savedItems := make([]models.ClaimItem, 0, len(items))
	for _, item := range items {
		var saved models.ClaimItem
		err = tx.QueryRow(ctx,
			`INSERT INTO claim_items (claim_id, sequence, product_or_service_code,
			 product_or_service_display, serviced_date, quantity, unit_price, net_amount)
			 VALUES ($1,$2,$3,$4,$5,$6,$7,$8) RETURNING id, claim_id, sequence,
			 product_or_service_code, product_or_service_display, serviced_date, quantity, unit_price, net_amount`,
			c.ID, item.Sequence, item.ProductOrServiceCode, item.ProductOrServiceDisplay,
			item.ServicedDate, item.Quantity, item.UnitPrice, item.NetAmount,
		).Scan(&saved.ID, &saved.ClaimID, &saved.Sequence, &saved.ProductOrServiceCode,
			&saved.ProductOrServiceDisplay, &saved.ServicedDate, &saved.Quantity,
			&saved.UnitPrice, &saved.NetAmount)
		if err != nil {
			return nil, fmt.Errorf("insert claim item: %w", err)
		}
		savedItems = append(savedItems, saved)
	}

	if err := tx.Commit(ctx); err != nil {
		return nil, err
	}

	c.Items = savedItems
	events.PublishClaimEvent(c.ID, c.PatientID, "CLAIM_SUBMITTED",
		fmt.Sprintf("Claim submitted: $%.2f", c.TotalAmount))

	return c, nil
}

func (s *AdjudicationService) GetClaim(ctx context.Context, id string) (*models.Claim, error) {
	c := &models.Claim{}
	err := s.db.QueryRow(ctx, `SELECT id, patient_id, coverage_id, status, type, use,
		billable_period_start, billable_period_end, total_amount, currency,
		provider_reference, facility_reference, created_at, updated_at
		FROM claims WHERE id=$1`, id).Scan(
		&c.ID, &c.PatientID, &c.CoverageID, &c.Status, &c.Type, &c.Use,
		&c.BillablePeriodStart, &c.BillablePeriodEnd, &c.TotalAmount, &c.Currency,
		&c.ProviderReference, &c.FacilityReference, &c.CreatedAt, &c.UpdatedAt)
	if err != nil {
		return nil, err
	}

	rows, err := s.db.Query(ctx,
		`SELECT id, claim_id, sequence, product_or_service_code, product_or_service_display,
		 serviced_date, quantity, unit_price, net_amount FROM claim_items WHERE claim_id=$1 ORDER BY sequence`, id)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	for rows.Next() {
		var item models.ClaimItem
		_ = rows.Scan(&item.ID, &item.ClaimID, &item.Sequence, &item.ProductOrServiceCode,
			&item.ProductOrServiceDisplay, &item.ServicedDate, &item.Quantity,
			&item.UnitPrice, &item.NetAmount)
		c.Items = append(c.Items, item)
	}
	return c, nil
}

func (s *AdjudicationService) GetClaimsByPatient(ctx context.Context, patientID string) ([]*models.Claim, error) {
	rows, err := s.db.Query(ctx, "SELECT id FROM claims WHERE patient_id=$1", patientID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var claims []*models.Claim
	for rows.Next() {
		var id string
		_ = rows.Scan(&id)
		c, err := s.GetClaim(ctx, id)
		if err == nil {
			claims = append(claims, c)
		}
	}
	return claims, nil
}

func round2(f float64) float64 {
	return math.Round(f*100) / 100
}

func (s *AdjudicationService) Adjudicate(ctx context.Context, claimID string) (*models.EOB, error) {
	claim, err := s.GetClaim(ctx, claimID)
	if err != nil {
		return nil, fmt.Errorf("claim not found: %s", claimID)
	}
	if claim.Status != "active" {
		return nil, fmt.Errorf("claim %s is not in active status", claimID)
	}

	// Step 1: Check coverage eligibility (Redis-cached)
	eligible := s.coverage.IsPatientEligible(ctx, claim.PatientID)
	if !eligible {
		s.db.Exec(ctx, "UPDATE claims SET status='cancelled' WHERE id=$1", claimID)
		events.PublishClaimEvent(claimID, claim.PatientID, "CLAIM_DENIED", "No active coverage")

		eob := &models.EOB{}
		submitted := claim.TotalAmount
		zero := 0.0
		disp := "Denied: no active coverage found"
		err := s.db.QueryRow(ctx,
			`INSERT INTO explanation_of_benefits
			 (claim_id, patient_id, status, outcome, type, total_submitted, total_benefit,
			  patient_responsibility, disposition)
			 VALUES ($1,$2,'active','error',$3,$4,$5,$6,$7) RETURNING id, claim_id, patient_id,
			 status, outcome, type, total_submitted, total_benefit, patient_responsibility,
			 deductible_applied, coinsurance_amount, copay_amount, disposition, created_at, updated_at`,
			claimID, claim.PatientID, claim.Type, submitted, zero, submitted, disp,
		).Scan(&eob.ID, &eob.ClaimID, &eob.PatientID, &eob.Status, &eob.Outcome, &eob.Type,
			&eob.TotalSubmitted, &eob.TotalBenefit, &eob.PatientResponsibility,
			&eob.DeductibleApplied, &eob.CoinsuranceAmount, &eob.CopayAmount,
			&eob.Disposition, &eob.CreatedAt, &eob.UpdatedAt)
		if err != nil {
			return nil, err
		}
		return eob, nil
	}

	// Step 2: Calculate adjudication amounts
	submitted := claim.TotalAmount
	afterCopay := math.Max(submitted-copay, 0)
	deductibleApplied := math.Min(afterCopay, deductible)
	afterDeductible := afterCopay - deductibleApplied
	coinsurance := round2(afterDeductible * coinsuranceRate)
	patientTotal := round2(copay + deductibleApplied + coinsurance)
	insurerPays := round2(math.Max(submitted-patientTotal, 0))

	// Step 3: Generate EOB
	copayVal := copay
	eob := &models.EOB{}
	disp := "Claim adjudicated successfully"
	err = s.db.QueryRow(ctx,
		`INSERT INTO explanation_of_benefits
		 (claim_id, patient_id, status, outcome, type, total_submitted, total_benefit,
		  patient_responsibility, deductible_applied, coinsurance_amount, copay_amount, disposition)
		 VALUES ($1,$2,'active','complete',$3,$4,$5,$6,$7,$8,$9,$10) RETURNING id, claim_id,
		 patient_id, status, outcome, type, total_submitted, total_benefit, patient_responsibility,
		 deductible_applied, coinsurance_amount, copay_amount, disposition, created_at, updated_at`,
		claimID, claim.PatientID, claim.Type, submitted, insurerPays,
		patientTotal, deductibleApplied, coinsurance, copayVal, disp,
	).Scan(&eob.ID, &eob.ClaimID, &eob.PatientID, &eob.Status, &eob.Outcome, &eob.Type,
		&eob.TotalSubmitted, &eob.TotalBenefit, &eob.PatientResponsibility,
		&eob.DeductibleApplied, &eob.CoinsuranceAmount, &eob.CopayAmount,
		&eob.Disposition, &eob.CreatedAt, &eob.UpdatedAt)
	if err != nil {
		return nil, err
	}

	log.Printf("Adjudicated claim %s: submitted=$%.2f, insurer=$%.2f, patient=$%.2f",
		claimID, submitted, insurerPays, patientTotal)

	// Step 4: Publish Kafka events
	events.PublishClaimEvent(claimID, claim.PatientID, "CLAIM_ADJUDICATED",
		fmt.Sprintf("Adjudicated: insurer pays $%.2f, patient owes $%.2f", insurerPays, patientTotal))
	events.PublishClaimEvent(claimID, claim.PatientID, "EOB_GENERATED",
		fmt.Sprintf("EOB %s generated", eob.ID))

	return eob, nil
}

func (s *AdjudicationService) GetEOB(ctx context.Context, id string) (*models.EOB, error) {
	return s.scanEOB(ctx, "SELECT * FROM explanation_of_benefits WHERE id=$1", id)
}

func (s *AdjudicationService) GetEOBByClaimID(ctx context.Context, claimID string) (*models.EOB, error) {
	return s.scanEOB(ctx, "SELECT * FROM explanation_of_benefits WHERE claim_id=$1", claimID)
}

func (s *AdjudicationService) GetEOBsByPatient(ctx context.Context, patientID string) ([]*models.EOB, error) {
	rows, err := s.db.Query(ctx, "SELECT * FROM explanation_of_benefits WHERE patient_id=$1", patientID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var eobs []*models.EOB
	for rows.Next() {
		e := &models.EOB{}
		_ = rows.Scan(&e.ID, &e.ClaimID, &e.PatientID, &e.Status, &e.Outcome, &e.Type,
			&e.TotalSubmitted, &e.TotalBenefit, &e.PatientResponsibility,
			&e.DeductibleApplied, &e.CoinsuranceAmount, &e.CopayAmount,
			&e.Disposition, &e.CreatedAt, &e.UpdatedAt)
		eobs = append(eobs, e)
	}
	return eobs, nil
}

func (s *AdjudicationService) scanEOB(ctx context.Context, query, arg string) (*models.EOB, error) {
	e := &models.EOB{}
	err := s.db.QueryRow(ctx, query, arg).Scan(
		&e.ID, &e.ClaimID, &e.PatientID, &e.Status, &e.Outcome, &e.Type,
		&e.TotalSubmitted, &e.TotalBenefit, &e.PatientResponsibility,
		&e.DeductibleApplied, &e.CoinsuranceAmount, &e.CopayAmount,
		&e.Disposition, &e.CreatedAt, &e.UpdatedAt)
	if err != nil {
		return nil, err
	}
	return e, nil
}
