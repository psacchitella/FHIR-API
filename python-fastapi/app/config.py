from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = "postgresql+asyncpg://fhir:fhir@localhost:5432/fhir_claims"
    redis_url: str = "redis://localhost:6379"
    kafka_bootstrap_servers: str = "localhost:9092"
    fhir_base_url: str = "http://localhost:8000/fhir"
    coverage_cache_ttl_seconds: int = 900  # 15 minutes

    class Config:
        env_prefix = "FHIR_"


settings = Settings()
