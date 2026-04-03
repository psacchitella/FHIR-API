package models

import (
	"time"
)

type Patient struct {
	ID          string     `json:"id" db:"id"`
	FamilyName  string     `json:"family_name" db:"family_name"`
	GivenName   string     `json:"given_name" db:"given_name"`
	MRN         string     `json:"mrn" db:"mrn"`
	BirthDate   *string    `json:"birth_date,omitempty" db:"birth_date"`
	Gender      *string    `json:"gender,omitempty" db:"gender"`
	Phone       *string    `json:"phone,omitempty" db:"phone"`
	Email       *string    `json:"email,omitempty" db:"email"`
	AddressLine *string    `json:"address_line,omitempty" db:"address_line"`
	City        *string    `json:"city,omitempty" db:"city"`
	State       *string    `json:"state,omitempty" db:"state"`
	PostalCode  *string    `json:"postal_code,omitempty" db:"postal_code"`
	CreatedAt   time.Time  `json:"created_at" db:"created_at"`
	UpdatedAt   time.Time  `json:"updated_at" db:"updated_at"`
}

type Coverage struct {
	ID           string    `json:"id" db:"id"`
	PatientID    string    `json:"patient_id" db:"patient_id"`
	SubscriberID string    `json:"subscriber_id" db:"subscriber_id"`
	PayorName    string    `json:"payor_name" db:"payor_name"`
	PayorID      string    `json:"payor_id" db:"payor_id"`
	PlanName     string    `json:"plan_name" db:"plan_name"`
	GroupNumber  *string   `json:"group_number,omitempty" db:"group_number"`
	Status       string    `json:"status" db:"status"`
	Relationship string   `json:"relationship" db:"relationship"`
	PeriodStart  *string   `json:"period_start,omitempty" db:"period_start"`
	PeriodEnd    *string   `json:"period_end,omitempty" db:"period_end"`
	CreatedAt    time.Time `json:"created_at" db:"created_at"`
	UpdatedAt    time.Time `json:"updated_at" db:"updated_at"`
}

type Claim struct {
	ID                  string    `json:"id" db:"id"`
	PatientID           string    `json:"patient_id" db:"patient_id"`
	CoverageID          string    `json:"coverage_id" db:"coverage_id"`
	Status              string    `json:"status" db:"status"`
	Type                string    `json:"type" db:"type"`
	Use                 string    `json:"use" db:"use"`
	BillablePeriodStart string    `json:"billable_period_start" db:"billable_period_start"`
	BillablePeriodEnd   string    `json:"billable_period_end" db:"billable_period_end"`
	TotalAmount         float64   `json:"total_amount" db:"total_amount"`
	Currency            string    `json:"currency" db:"currency"`
	ProviderReference   *string   `json:"provider_reference,omitempty" db:"provider_reference"`
	FacilityReference   *string   `json:"facility_reference,omitempty" db:"facility_reference"`
	CreatedAt           time.Time `json:"created_at" db:"created_at"`
	UpdatedAt           time.Time `json:"updated_at" db:"updated_at"`
	Items               []ClaimItem `json:"items,omitempty"`
}

type ClaimItem struct {
	ID                       string  `json:"id" db:"id"`
	ClaimID                  string  `json:"claim_id" db:"claim_id"`
	Sequence                 int     `json:"sequence" db:"sequence"`
	ProductOrServiceCode     string  `json:"product_or_service_code" db:"product_or_service_code"`
	ProductOrServiceDisplay  *string `json:"product_or_service_display,omitempty" db:"product_or_service_display"`
	ServicedDate             *string `json:"serviced_date,omitempty" db:"serviced_date"`
	Quantity                 int     `json:"quantity" db:"quantity"`
	UnitPrice                float64 `json:"unit_price" db:"unit_price"`
	NetAmount                float64 `json:"net_amount" db:"net_amount"`
}

type EOB struct {
	ID                    string    `json:"id" db:"id"`
	ClaimID               string    `json:"claim_id" db:"claim_id"`
	PatientID             string    `json:"patient_id" db:"patient_id"`
	Status                string    `json:"status" db:"status"`
	Outcome               string    `json:"outcome" db:"outcome"`
	Type                  string    `json:"type" db:"type"`
	TotalSubmitted        *float64  `json:"total_submitted,omitempty" db:"total_submitted"`
	TotalBenefit          *float64  `json:"total_benefit,omitempty" db:"total_benefit"`
	PatientResponsibility *float64  `json:"patient_responsibility,omitempty" db:"patient_responsibility"`
	DeductibleApplied     *float64  `json:"deductible_applied,omitempty" db:"deductible_applied"`
	CoinsuranceAmount     *float64  `json:"coinsurance_amount,omitempty" db:"coinsurance_amount"`
	CopayAmount           *float64  `json:"copay_amount,omitempty" db:"copay_amount"`
	Disposition           *string   `json:"disposition,omitempty" db:"disposition"`
	CreatedAt             time.Time `json:"created_at" db:"created_at"`
	UpdatedAt             time.Time `json:"updated_at" db:"updated_at"`
}
