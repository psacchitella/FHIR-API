package config

import (
	"fmt"
	"os"
	"time"
)

type Config struct {
	Port            string
	DatabaseURL     string
	RedisAddr       string
	KafkaBrokers    []string
	CoverageCacheTTL time.Duration
}

func Load() *Config {
	return &Config{
		Port: getEnv("PORT", "8082"),
		DatabaseURL: fmt.Sprintf("postgres://%s:%s@%s:%s/%s?sslmode=disable",
			getEnv("DB_USER", "fhir"),
			getEnv("DB_PASSWORD", "fhir"),
			getEnv("DB_HOST", "localhost"),
			getEnv("DB_PORT", "5432"),
			getEnv("DB_NAME", "fhir_claims"),
		),
		RedisAddr:        fmt.Sprintf("%s:%s", getEnv("REDIS_HOST", "localhost"), getEnv("REDIS_PORT", "6379")),
		KafkaBrokers:     []string{getEnv("KAFKA_BROKERS", "localhost:9092")},
		CoverageCacheTTL: 15 * time.Minute,
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
