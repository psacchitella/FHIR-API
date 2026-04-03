package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"

	"github.com/psacchitella/fhir-claim-api/internal/config"
	"github.com/psacchitella/fhir-claim-api/internal/events"
	"github.com/psacchitella/fhir-claim-api/internal/handlers"
	"github.com/psacchitella/fhir-claim-api/internal/services"
)

func main() {
	cfg := config.Load()
	ctx := context.Background()

	// PostgreSQL
	pool, err := pgxpool.New(ctx, cfg.DatabaseURL)
	if err != nil {
		log.Fatalf("Unable to connect to database: %v", err)
	}
	defer pool.Close()
	log.Println("PostgreSQL connected")

	// Redis
	rdb := redis.NewClient(&redis.Options{Addr: cfg.RedisAddr})
	if err := rdb.Ping(ctx).Err(); err != nil {
		log.Printf("Redis unavailable, caching disabled: %v", err)
		rdb = nil
	} else {
		log.Println("Redis connected")
	}

	// Kafka
	events.StartProducer(cfg.KafkaBrokers)
	defer events.StopProducer()

	// Services
	coverageSvc := services.NewCoverageService(pool, rdb, cfg)
	adjudicationSvc := services.NewAdjudicationService(pool, coverageSvc)

	// Router
	gin.SetMode(gin.ReleaseMode)
	r := gin.New()
	r.Use(gin.Logger(), gin.Recovery())

	h := handlers.New(pool, adjudicationSvc, coverageSvc)

	fhir := r.Group("/fhir")
	{
		fhir.GET("/metadata", h.Metadata)

		fhir.GET("/Patient/:id", h.ReadPatient)
		fhir.GET("/Patient", h.SearchPatients)
		fhir.POST("/Patient", h.CreatePatient)

		fhir.GET("/Coverage/:id", h.ReadCoverage)
		fhir.GET("/Coverage", h.SearchCoverages)
		fhir.POST("/Coverage", h.CreateCoverage)

		fhir.GET("/Claim/:id", h.ReadClaim)
		fhir.GET("/Claim", h.SearchClaims)
		fhir.POST("/Claim", h.CreateClaim)
		fhir.POST("/Claim/:id/$adjudicate", h.AdjudicateClaim)

		fhir.GET("/ExplanationOfBenefit/:id", h.ReadEOB)
		fhir.GET("/ExplanationOfBenefit", h.SearchEOBs)
	}

	r.GET("/health", func(c *gin.Context) { c.JSON(200, gin.H{"status": "ok"}) })

	// Server with graceful shutdown
	srv := &http.Server{Addr: ":" + cfg.Port, Handler: r}

	go func() {
		log.Printf("FHIR Claim API (Go) listening on port %s", cfg.Port)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Server error: %v", err)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("Shutting down...")
	shutdownCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	srv.Shutdown(shutdownCtx)
}
