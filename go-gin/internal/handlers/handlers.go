package handlers

import (
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/psacchitella/fhir-claim-api/internal/models"
	"github.com/psacchitella/fhir-claim-api/internal/services"
)

const fhirJSON = "application/fhir+json"

type Handlers struct {
	db          *pgxpool.Pool
	adjudication *services.AdjudicationService
	coverage     *services.CoverageService
}

func New(db *pgxpool.Pool, adj *services.AdjudicationService, cov *services.CoverageService) *Handlers {
	return &Handlers{db: db, adjudication: adj, coverage: cov}
}

// --- Metadata ---

func (h *Handlers) Metadata(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{
		"resourceType": "CapabilityStatement",
		"status": "active", "date": time.Now().UTC().Format(time.RFC3339),
		"kind": "instance", "fhirVersion": "4.0.1",
		"format": []string{"application/fhir+json"},
		"software": gin.H{"name": "FHIR Claim Processing API (Go)", "version": "1.0.0"},
		"rest": []gin.H{{
			"mode": "server",
			"resource": []gin.H{
				{"type": "Patient", "interaction": []gin.H{{"code": "read"}, {"code": "create"}, {"code": "update"}, {"code": "search-type"}}},
				{"type": "Coverage", "interaction": []gin.H{{"code": "read"}, {"code": "create"}, {"code": "search-type"}}},
				{"type": "Claim", "interaction": []gin.H{{"code": "read"}, {"code": "create"}, {"code": "search-type"}}},
				{"type": "ExplanationOfBenefit", "interaction": []gin.H{{"code": "read"}, {"code": "search-type"}}},
			},
			"operation": []gin.H{{"name": "adjudicate", "definition": "Claim/{id}/$adjudicate"}},
		}},
	})
}

// --- Patient ---

func (h *Handlers) ReadPatient(c *gin.Context) {
	p := &models.Patient{}
	err := h.db.QueryRow(c, `SELECT id, family_name, given_name, mrn, birth_date, gender,
		phone, email, address_line, city, state, postal_code, created_at, updated_at
		FROM patients WHERE id=$1`, c.Param("id")).Scan(
		&p.ID, &p.FamilyName, &p.GivenName, &p.MRN, &p.BirthDate, &p.Gender,
		&p.Phone, &p.Email, &p.AddressLine, &p.City, &p.State, &p.PostalCode,
		&p.CreatedAt, &p.UpdatedAt)
	if err != nil {
		c.JSON(404, gin.H{"error": "Patient not found"})
		return
	}
	c.JSON(200, models.PatientToFHIR(p))
}

func (h *Handlers) SearchPatients(c *gin.Context) {
	var rows_data []*models.Patient
	identifier := c.Query("identifier")
	var query string
	var args []any
	if identifier != "" {
		query = `SELECT id, family_name, given_name, mrn, birth_date, gender, phone, email,
			address_line, city, state, postal_code, created_at, updated_at FROM patients WHERE mrn=$1`
		args = []any{identifier}
	} else {
		query = `SELECT id, family_name, given_name, mrn, birth_date, gender, phone, email,
			address_line, city, state, postal_code, created_at, updated_at FROM patients`
	}
	rows, err := h.db.Query(c, query, args...)
	if err != nil {
		c.JSON(500, gin.H{"error": err.Error()})
		return
	}
	defer rows.Close()
	for rows.Next() {
		p := &models.Patient{}
		_ = rows.Scan(&p.ID, &p.FamilyName, &p.GivenName, &p.MRN, &p.BirthDate, &p.Gender,
			&p.Phone, &p.Email, &p.AddressLine, &p.City, &p.State, &p.PostalCode,
			&p.CreatedAt, &p.UpdatedAt)
		rows_data = append(rows_data, p)
	}
	resources := make([]map[string]any, 0, len(rows_data))
	for _, p := range rows_data {
		resources = append(resources, models.PatientToFHIR(p))
	}
	c.JSON(200, models.SearchBundle(resources))
}

func (h *Handlers) CreatePatient(c *gin.Context) {
	var body map[string]any
	if err := c.BindJSON(&body); err != nil {
		c.JSON(400, gin.H{"error": err.Error()})
		return
	}
	names, _ := body["name"].([]any)
	name, _ := names[0].(map[string]any)
	ids, _ := body["identifier"].([]any)
	id, _ := ids[0].(map[string]any)
	givens, _ := name["given"].([]any)

	p := &models.Patient{}
	err := h.db.QueryRow(c,
		`INSERT INTO patients (family_name, given_name, mrn, birth_date, gender)
		 VALUES ($1,$2,$3,$4,$5) RETURNING id, family_name, given_name, mrn, birth_date,
		 gender, phone, email, address_line, city, state, postal_code, created_at, updated_at`,
		name["family"], givens[0], id["value"], body["birthDate"], body["gender"],
	).Scan(&p.ID, &p.FamilyName, &p.GivenName, &p.MRN, &p.BirthDate, &p.Gender,
		&p.Phone, &p.Email, &p.AddressLine, &p.City, &p.State, &p.PostalCode,
		&p.CreatedAt, &p.UpdatedAt)
	if err != nil {
		c.JSON(400, gin.H{"error": err.Error()})
		return
	}
	c.Header("Location", "/fhir/Patient/"+p.ID)
	c.JSON(201, models.PatientToFHIR(p))
}

// --- Coverage ---

func (h *Handlers) ReadCoverage(c *gin.Context) {
	cov, err := h.coverage.FindByID(c, c.Param("id"))
	if err != nil {
		c.JSON(404, gin.H{"error": "Coverage not found"})
		return
	}
	c.JSON(200, models.CoverageToFHIR(cov))
}

func (h *Handlers) SearchCoverages(c *gin.Context) {
	beneficiary := c.Query("beneficiary")
	if beneficiary == "" {
		c.JSON(200, models.SearchBundle(nil))
		return
	}
	coverages, _ := h.coverage.FindByPatientID(c, beneficiary)
	resources := make([]map[string]any, 0, len(coverages))
	for _, cov := range coverages {
		resources = append(resources, models.CoverageToFHIR(cov))
	}
	c.JSON(200, models.SearchBundle(resources))
}

func (h *Handlers) CreateCoverage(c *gin.Context) {
	var body map[string]any
	if err := c.BindJSON(&body); err != nil {
		c.JSON(400, gin.H{"error": err.Error()})
		return
	}
	benRef, _ := body["beneficiary"].(map[string]any)
	ref, _ := benRef["reference"].(string)
	patientID := ref[len("Patient/"):]

	payors, _ := body["payor"].([]any)
	payor, _ := payors[0].(map[string]any)
	payorRef, _ := payor["reference"].(string)

	rel := "self"
	if relMap, ok := body["relationship"].(map[string]any); ok {
		if codings, ok := relMap["coding"].([]any); ok && len(codings) > 0 {
			if coding, ok := codings[0].(map[string]any); ok {
				rel, _ = coding["code"].(string)
			}
		}
	}

	cov := &models.Coverage{
		PatientID:    patientID,
		SubscriberID: strOrEmpty(body, "subscriberId"),
		Status:       strOr(body, "status", "active"),
		PayorName:    strOrEmpty(payor, "display"),
		PayorID:      payorRef[len("Organization/"):],
		PlanName:     strOrEmpty(payor, "display"),
		Relationship: rel,
	}
	if period, ok := body["period"].(map[string]any); ok {
		if s, ok := period["start"].(string); ok { cov.PeriodStart = &s }
		if e, ok := period["end"].(string); ok { cov.PeriodEnd = &e }
	}

	saved, err := h.coverage.Save(c, cov)
	if err != nil {
		c.JSON(400, gin.H{"error": err.Error()})
		return
	}
	c.Header("Location", "/fhir/Coverage/"+saved.ID)
	c.JSON(201, models.CoverageToFHIR(saved))
}

// --- Claim ---

func (h *Handlers) ReadClaim(c *gin.Context) {
	claim, err := h.adjudication.GetClaim(c, c.Param("id"))
	if err != nil {
		c.JSON(404, gin.H{"error": "Claim not found"})
		return
	}
	c.JSON(200, models.ClaimToFHIR(claim))
}

func (h *Handlers) SearchClaims(c *gin.Context) {
	patient := c.Query("patient")
	if patient == "" {
		c.JSON(200, models.SearchBundle(nil))
		return
	}
	claims, _ := h.adjudication.GetClaimsByPatient(c, patient)
	resources := make([]map[string]any, 0, len(claims))
	for _, cl := range claims {
		resources = append(resources, models.ClaimToFHIR(cl))
	}
	c.JSON(200, models.SearchBundle(resources))
}

func (h *Handlers) CreateClaim(c *gin.Context) {
	var body map[string]any
	if err := c.BindJSON(&body); err != nil {
		c.JSON(400, gin.H{"error": err.Error()})
		return
	}

	patRef, _ := body["patient"].(map[string]any)
	patientID := patRef["reference"].(string)[len("Patient/"):]

	ins, _ := body["insurance"].([]any)
	ins0, _ := ins[0].(map[string]any)
	covRef, _ := ins0["coverage"].(map[string]any)
	coverageID := covRef["reference"].(string)[len("Coverage/"):]

	typeObj, _ := body["type"].(map[string]any)
	codings, _ := typeObj["coding"].([]any)
	coding0, _ := codings[0].(map[string]any)

	billable, _ := body["billablePeriod"].(map[string]any)
	total, _ := body["total"].(map[string]any)

	claim := &models.Claim{
		PatientID:           patientID,
		CoverageID:          coverageID,
		Type:                coding0["code"].(string),
		Use:                 strOr(body, "use", "claim"),
		BillablePeriodStart: billable["start"].(string),
		BillablePeriodEnd:   billable["end"].(string),
		TotalAmount:         total["value"].(float64),
		Currency:            strOr(total, "currency", "USD"),
	}
	if prov, ok := body["provider"].(map[string]any); ok {
		ref := prov["reference"].(string)
		claim.ProviderReference = &ref
	}

	var items []models.ClaimItem
	if fhirItems, ok := body["item"].([]any); ok {
		for i, fi := range fhirItems {
			item, _ := fi.(map[string]any)
			ps, _ := item["productOrService"].(map[string]any)
			psCoding, _ := ps["coding"].([]any)
			psCoding0, _ := psCoding[0].(map[string]any)
			qty, _ := item["quantity"].(map[string]any)
			up, _ := item["unitPrice"].(map[string]any)
			net, _ := item["net"].(map[string]any)

			display, _ := psCoding0["display"].(string)
			ci := models.ClaimItem{
				Sequence:                i + 1,
				ProductOrServiceCode:    psCoding0["code"].(string),
				ProductOrServiceDisplay: &display,
				Quantity:                int(qty["value"].(float64)),
				UnitPrice:              up["value"].(float64),
				NetAmount:              net["value"].(float64),
			}
			items = append(items, ci)
		}
	}

	saved, err := h.adjudication.SubmitClaim(c, claim, items)
	if err != nil {
		c.JSON(400, gin.H{"error": err.Error()})
		return
	}
	c.Header("Location", "/fhir/Claim/"+saved.ID)
	c.JSON(201, models.ClaimToFHIR(saved))
}

func (h *Handlers) AdjudicateClaim(c *gin.Context) {
	eob, err := h.adjudication.Adjudicate(c, c.Param("id"))
	if err != nil {
		c.JSON(400, gin.H{"error": err.Error()})
		return
	}
	c.JSON(200, models.EOBToFHIR(eob))
}

// --- EOB ---

func (h *Handlers) ReadEOB(c *gin.Context) {
	eob, err := h.adjudication.GetEOB(c, c.Param("id"))
	if err != nil {
		c.JSON(404, gin.H{"error": "ExplanationOfBenefit not found"})
		return
	}
	c.JSON(200, models.EOBToFHIR(eob))
}

func (h *Handlers) SearchEOBs(c *gin.Context) {
	claimID := c.Query("claim")
	patientID := c.Query("patient")

	var eobs []*models.EOB
	if claimID != "" {
		eob, err := h.adjudication.GetEOBByClaimID(c, claimID)
		if err == nil {
			eobs = []*models.EOB{eob}
		}
	} else if patientID != "" {
		eobs, _ = h.adjudication.GetEOBsByPatient(c, patientID)
	}

	resources := make([]map[string]any, 0, len(eobs))
	for _, e := range eobs {
		resources = append(resources, models.EOBToFHIR(e))
	}
	c.JSON(200, models.SearchBundle(resources))
}

// --- helpers ---

func strOrEmpty(m map[string]any, key string) string {
	v, _ := m[key].(string)
	return v
}

func strOr(m map[string]any, key, fallback string) string {
	if v, ok := m[key].(string); ok && v != "" {
		return v
	}
	return fallback
}
