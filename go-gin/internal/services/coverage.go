package services

import (
	"context"
	"fmt"
	"log"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"

	"github.com/psacchitella/fhir-claim-api/internal/config"
	"github.com/psacchitella/fhir-claim-api/internal/models"
)

const cachePrefix = "coverage:"

type CoverageService struct {
	db    *pgxpool.Pool
	rdb   *redis.Client
	cfg   *config.Config
}

func NewCoverageService(db *pgxpool.Pool, rdb *redis.Client, cfg *config.Config) *CoverageService {
	return &CoverageService{db: db, rdb: rdb, cfg: cfg}
}

func (s *CoverageService) IsPatientEligible(ctx context.Context, patientID string) bool {
	cacheKey := cachePrefix + patientID

	if s.rdb != nil {
		val, err := s.rdb.Get(ctx, cacheKey).Result()
		if err == nil {
			log.Printf("Coverage cache hit for patient %s", patientID)
			return val == "1"
		}
	}

	log.Printf("Coverage cache miss for patient %s, querying DB", patientID)
	var exists bool
	err := s.db.QueryRow(ctx,
		"SELECT EXISTS(SELECT 1 FROM coverages WHERE patient_id=$1 AND status='active')",
		patientID).Scan(&exists)
	if err != nil {
		log.Printf("Coverage query error: %v", err)
		return false
	}

	if s.rdb != nil {
		val := "0"
		if exists { val = "1" }
		s.rdb.Set(ctx, cacheKey, val, s.cfg.CoverageCacheTTL)
	}

	return exists
}

func (s *CoverageService) Save(ctx context.Context, c *models.Coverage) (*models.Coverage, error) {
	err := s.db.QueryRow(ctx,
		`INSERT INTO coverages (patient_id, subscriber_id, payor_name, payor_id, plan_name,
		 group_number, status, relationship, period_start, period_end)
		 VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) RETURNING id, created_at, updated_at`,
		c.PatientID, c.SubscriberID, c.PayorName, c.PayorID, c.PlanName,
		c.GroupNumber, c.Status, c.Relationship, c.PeriodStart, c.PeriodEnd,
	).Scan(&c.ID, &c.CreatedAt, &c.UpdatedAt)
	if err != nil {
		return nil, fmt.Errorf("insert coverage: %w", err)
	}

	if s.rdb != nil && c.Status == "active" {
		s.rdb.Set(ctx, cachePrefix+c.PatientID, "1", s.cfg.CoverageCacheTTL)
	}
	return c, nil
}

func (s *CoverageService) FindByID(ctx context.Context, id string) (*models.Coverage, error) {
	c := &models.Coverage{}
	err := s.db.QueryRow(ctx, "SELECT * FROM coverages WHERE id=$1", id).Scan(
		&c.ID, &c.PatientID, &c.SubscriberID, &c.PayorName, &c.PayorID, &c.PlanName,
		&c.GroupNumber, &c.Status, &c.Relationship, &c.PeriodStart, &c.PeriodEnd,
		&c.CreatedAt, &c.UpdatedAt)
	if err != nil {
		return nil, err
	}
	return c, nil
}

func (s *CoverageService) FindByPatientID(ctx context.Context, patientID string) ([]*models.Coverage, error) {
	rows, err := s.db.Query(ctx, "SELECT * FROM coverages WHERE patient_id=$1", patientID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var coverages []*models.Coverage
	for rows.Next() {
		c := &models.Coverage{}
		_ = rows.Scan(&c.ID, &c.PatientID, &c.SubscriberID, &c.PayorName, &c.PayorID, &c.PlanName,
			&c.GroupNumber, &c.Status, &c.Relationship, &c.PeriodStart, &c.PeriodEnd,
			&c.CreatedAt, &c.UpdatedAt)
		coverages = append(coverages, c)
	}
	return coverages, nil
}

// Ensure time import is used
var _ = time.Now
