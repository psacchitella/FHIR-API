package com.xanterra.fhir.service;

import com.xanterra.fhir.config.RedisConfig;
import com.xanterra.fhir.model.entity.CoverageEntity;
import com.xanterra.fhir.repository.CoverageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CoverageService {

    private static final Logger log = LoggerFactory.getLogger(CoverageService.class);
    private static final String CACHE_PREFIX = "coverage:";

    private final CoverageRepository coverageRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public CoverageService(CoverageRepository coverageRepository,
                           RedisTemplate<String, Object> redisTemplate) {
        this.coverageRepository = coverageRepository;
        this.redisTemplate = redisTemplate;
    }

    public CoverageEntity save(CoverageEntity entity) {
        CoverageEntity saved = coverageRepository.save(entity);
        cacheEligibility(saved);
        return saved;
    }

    public Optional<CoverageEntity> findById(UUID id) {
        return coverageRepository.findById(id);
    }

    public List<CoverageEntity> findByPatientId(UUID patientId) {
        return coverageRepository.findByPatientId(patientId);
    }

    /**
     * Check if a patient has active coverage. Uses Redis cache to avoid
     * repeated DB lookups during claim processing — coverage eligibility
     * is checked on every claim submission.
     */
    public boolean isPatientEligible(UUID patientId) {
        String cacheKey = CACHE_PREFIX + patientId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Coverage cache hit for patient {}", patientId);
            return Boolean.TRUE.equals(cached);
        }

        log.debug("Coverage cache miss for patient {}, querying DB", patientId);
        List<CoverageEntity> activeCoverages =
                coverageRepository.findByPatientIdAndStatus(patientId, "active");
        boolean eligible = !activeCoverages.isEmpty();

        redisTemplate.opsForValue().set(cacheKey, eligible, RedisConfig.COVERAGE_CACHE_TTL);
        return eligible;
    }

    private void cacheEligibility(CoverageEntity entity) {
        String cacheKey = CACHE_PREFIX + entity.getPatient().getId();
        boolean eligible = "active".equals(entity.getStatus());
        redisTemplate.opsForValue().set(cacheKey, eligible, RedisConfig.COVERAGE_CACHE_TTL);
    }
}
