package com.healthcare.ai_appointmentscheduler.service;

import com.healthcare.ai_appointmentscheduler.dto.ParseResponse;
import com.healthcare.ai_appointmentscheduler.entity.ExtractedEntities;
import com.healthcare.ai_appointmentscheduler.entity.NormalizedEntity;
import org.springframework.stereotype.Service;

@Service
public class PipelineServiceImpl implements PipelineService {

    private final TextPreprocessorImpl preprocessor;
    private final EntityExtractorImpl extractor;
    private final NattyNormalizer normalizer;
    private final SimpleConfidenceScorer scorer;
    private final DefaultGuardrailService guardrail;

    public PipelineServiceImpl(TextPreprocessorImpl preprocessor,
                               EntityExtractorImpl extractor,
                               NattyNormalizer normalizer,
                               SimpleConfidenceScorer scorer,
                               DefaultGuardrailService guardrail) {
        this.preprocessor = preprocessor;
        this.extractor = extractor;
        this.normalizer = normalizer;
        this.scorer = scorer;
        this.guardrail = guardrail;
    }

    @Override
    public ParseResponse parseText(String text) {
        String clean = preprocessor.preprocess(text);
        ExtractedEntities extracted = extractor.extract(clean);
        NormalizedEntity normalized = normalizer.normalize(clean, extracted);
        double entityConf = scorer.scoreEntities(extracted);
        double normConf = scorer.scoreNormalization(normalized);
        return guardrail.buildResponse(text, extracted, normalized, entityConf, normConf);
    }
}