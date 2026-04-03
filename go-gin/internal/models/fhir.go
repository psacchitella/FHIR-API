package models

// FHIR R4 resource mappers: entity structs -> FHIR JSON maps

const (
	SystemMRN          = "http://hospital.example.org/mrn"
	SystemCPT          = "http://www.ama-assn.org/go/cpt"
	SystemClaimType    = "http://terminology.hl7.org/CodeSystem/claim-type"
	SystemAdjudication = "http://terminology.hl7.org/CodeSystem/adjudication"
)

func PatientToFHIR(p *Patient) map[string]any {
	r := map[string]any{
		"resourceType": "Patient",
		"id":           p.ID,
		"identifier":   []map[string]any{{"system": SystemMRN, "value": p.MRN}},
		"name":         []map[string]any{{"family": p.FamilyName, "given": []string{p.GivenName}}},
		"meta":         map[string]any{"lastUpdated": p.UpdatedAt},
	}
	if p.BirthDate != nil { r["birthDate"] = *p.BirthDate }
	if p.Gender != nil { r["gender"] = *p.Gender }
	var telecom []map[string]any
	if p.Phone != nil { telecom = append(telecom, map[string]any{"system": "phone", "value": *p.Phone}) }
	if p.Email != nil { telecom = append(telecom, map[string]any{"system": "email", "value": *p.Email}) }
	if len(telecom) > 0 { r["telecom"] = telecom }
	if p.AddressLine != nil {
		r["address"] = []map[string]any{{
			"line": []string{*p.AddressLine}, "city": p.City, "state": p.State, "postalCode": p.PostalCode,
		}}
	}
	return r
}

func CoverageToFHIR(c *Coverage) map[string]any {
	r := map[string]any{
		"resourceType": "Coverage",
		"id":           c.ID,
		"status":       c.Status,
		"beneficiary":  map[string]any{"reference": "Patient/" + c.PatientID},
		"subscriberId": c.SubscriberID,
		"payor":        []map[string]any{{"reference": "Organization/" + c.PayorID, "display": c.PayorName}},
		"relationship": map[string]any{"coding": []map[string]any{{"code": c.Relationship}}},
	}
	if c.PeriodStart != nil {
		period := map[string]any{"start": *c.PeriodStart}
		if c.PeriodEnd != nil { period["end"] = *c.PeriodEnd }
		r["period"] = period
	}
	return r
}

func ClaimToFHIR(c *Claim) map[string]any {
	items := make([]map[string]any, 0, len(c.Items))
	for _, i := range c.Items {
		item := map[string]any{
			"sequence": i.Sequence,
			"productOrService": map[string]any{
				"coding": []map[string]any{{
					"system": SystemCPT, "code": i.ProductOrServiceCode, "display": i.ProductOrServiceDisplay,
				}},
			},
			"quantity":  map[string]any{"value": i.Quantity},
			"unitPrice": map[string]any{"value": i.UnitPrice, "currency": c.Currency},
			"net":       map[string]any{"value": i.NetAmount, "currency": c.Currency},
		}
		items = append(items, item)
	}
	r := map[string]any{
		"resourceType":  "Claim",
		"id":            c.ID,
		"status":        c.Status,
		"type":          map[string]any{"coding": []map[string]any{{"system": SystemClaimType, "code": c.Type}}},
		"use":           c.Use,
		"patient":       map[string]any{"reference": "Patient/" + c.PatientID},
		"insurance":     []map[string]any{{"sequence": 1, "focal": true, "coverage": map[string]any{"reference": "Coverage/" + c.CoverageID}}},
		"billablePeriod": map[string]any{"start": c.BillablePeriodStart, "end": c.BillablePeriodEnd},
		"total":         map[string]any{"value": c.TotalAmount, "currency": c.Currency},
		"item":          items,
		"meta":          map[string]any{"lastUpdated": c.UpdatedAt},
	}
	if c.ProviderReference != nil { r["provider"] = map[string]any{"reference": *c.ProviderReference} }
	return r
}

func EOBToFHIR(e *EOB) map[string]any {
	r := map[string]any{
		"resourceType": "ExplanationOfBenefit",
		"id":           e.ID,
		"status":       e.Status,
		"outcome":      e.Outcome,
		"type":         map[string]any{"coding": []map[string]any{{"system": SystemClaimType, "code": e.Type}}},
		"patient":      map[string]any{"reference": "Patient/" + e.PatientID},
		"claim":        map[string]any{"reference": "Claim/" + e.ClaimID},
		"meta":         map[string]any{"lastUpdated": e.UpdatedAt},
	}
	if e.Disposition != nil { r["disposition"] = *e.Disposition }
	var total []map[string]any
	if e.TotalSubmitted != nil {
		total = append(total, map[string]any{
			"category": map[string]any{"coding": []map[string]any{{"system": SystemAdjudication, "code": "submitted"}}},
			"amount":   map[string]any{"value": *e.TotalSubmitted, "currency": "USD"},
		})
	}
	if e.TotalBenefit != nil {
		total = append(total, map[string]any{
			"category": map[string]any{"coding": []map[string]any{{"system": SystemAdjudication, "code": "benefit"}}},
			"amount":   map[string]any{"value": *e.TotalBenefit, "currency": "USD"},
		})
		r["payment"] = map[string]any{"amount": map[string]any{"value": *e.TotalBenefit, "currency": "USD"}}
	}
	if len(total) > 0 { r["total"] = total }
	return r
}

func SearchBundle(resources []map[string]any) map[string]any {
	entries := make([]map[string]any, 0, len(resources))
	for _, r := range resources {
		rt, _ := r["resourceType"].(string)
		id, _ := r["id"].(string)
		entries = append(entries, map[string]any{"fullUrl": rt + "/" + id, "resource": r})
	}
	return map[string]any{"resourceType": "Bundle", "type": "searchset", "total": len(resources), "entry": entries}
}
