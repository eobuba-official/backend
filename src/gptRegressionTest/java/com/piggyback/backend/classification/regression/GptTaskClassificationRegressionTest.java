package com.piggyback.backend.classification.regression;

import com.piggyback.backend.classification.application.ClassificationCommand;
import com.piggyback.backend.classification.application.ClassificationPolicy;
import com.piggyback.backend.classification.application.FraudDetectionPolicy;
import com.piggyback.backend.classification.config.ClassificationProperties;
import com.piggyback.backend.classification.domain.ClassificationResult;
import com.piggyback.backend.classification.domain.FraudPatternType;
import com.piggyback.backend.classification.domain.InputMethod;
import com.piggyback.backend.classification.infrastructure.llm.LlmClassificationException;
import com.piggyback.backend.classification.infrastructure.llm.LlmProperties;
import com.piggyback.backend.classification.infrastructure.llm.OpenAiCompatibleTaskClassificationClient;
import com.piggyback.backend.classification.port.LlmAnalysisOutput;
import com.piggyback.backend.domain.TaskTypeCode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("gpt-regression")
class GptTaskClassificationRegressionTest {

    private static final int MAX_EXTERNAL_REQUESTS = 20;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> ALLOWED_TASK_CODES = Arrays.stream(TaskTypeCode.values())
            .map(Enum::name)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Set<String> ALLOWED_FRAUD_PATTERNS = Arrays.stream(FraudPatternType.values())
            .map(Enum::name)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    @Test
    void evaluatesTheFixedDatasetAgainstTheRealGptProxy() throws Exception {
        RegressionProfile profile = RegressionProfile.load();
        Assumptions.assumeTrue(profile.configured(), profile.skipReason());

        RegressionDataset dataset = readDataset();
        assertFalse(dataset.cases().isEmpty(), "회귀 평가 데이터셋은 비어 있을 수 없습니다.");
        assertTrue(
                dataset.cases().size() <= MAX_EXTERNAL_REQUESTS,
                "비용 보호 한도를 초과했습니다. 외부 요청은 최대 " + MAX_EXTERNAL_REQUESTS + "개입니다."
        );

        var client = new OpenAiCompatibleTaskClassificationClient(
                OBJECT_MAPPER,
                profile.llmProperties()
        );
        var classificationPolicy = new ClassificationPolicy(profile.classificationProperties());
        var fraudPolicy = new FraudDetectionPolicy();
        var results = new ArrayList<CaseResult>();
        var observedModels = new LinkedHashSet<String>();
        var promptVersions = new LinkedHashSet<String>();
        Instant startedAt = Instant.now();

        for (RegressionCase testCase : dataset.cases()) {
            try {
                LlmAnalysisOutput output = client.analyze(testCase.utterance());
                observedModels.add(output.model());
                promptVersions.add(output.promptVersion());
                CaseResult result = evaluate(testCase, output, classificationPolicy, fraudPolicy);
                results.add(result);
                System.out.printf(
                        Locale.ROOT,
                        "GPT regression case=%s status=%s confidence=%.2f passed=%s%n",
                        testCase.id(),
                        result.actualStatus(),
                        result.confidence(),
                        result.passed()
                );
            } catch (LlmClassificationException exception) {
                results.add(CaseResult.transportFailure(testCase));
                break;
            }
        }

        RegressionReport report = RegressionReport.create(
                dataset.version(),
                profile,
                startedAt,
                dataset.cases().size(),
                observedModels,
                promptVersions,
                results
        );
        List<Path> reportPaths = writeReports(profile.reportDirectory(), report);
        System.out.printf(
                Locale.ROOT,
                "GPT regression model=%s promptVersions=%s report=%s%n",
                profile.llmProperties().getPrimaryModel(),
                promptVersions,
                reportPaths.get(0)
        );

        List<String> failures = results.stream()
                .filter(result -> !result.passed())
                .map(result -> result.caseId() + ": " + String.join(", ", result.issues()))
                .toList();
        assertTrue(
                results.size() == dataset.cases().size(),
                "외부 호출 오류로 평가가 조기 종료되었습니다. 리포트를 확인하세요."
        );
        assertTrue(failures.isEmpty(), "회귀 실패: " + String.join(" | ", failures));
    }

    private CaseResult evaluate(
            RegressionCase testCase,
            LlmAnalysisOutput output,
            ClassificationPolicy classificationPolicy,
            FraudDetectionPolicy fraudPolicy
    ) {
        List<String> issues = new ArrayList<>();
        validateStructuredOutput(testCase, output, issues);

        var command = new ClassificationCommand(
                testCase.utterance(),
                testCase.inputMethod(),
                null
        );
        ClassificationResult classification = classificationPolicy.normalize(
                command,
                output.classificationSignal()
        );
        var fraudAssessment = fraudPolicy.evaluate(testCase.utterance(), output);
        String actualStatus = fraudAssessment.detected()
                ? "FRAUD_WARNING"
                : classification.status().name();

        if (!testCase.expectedStatus().equals(actualStatus)) {
            issues.add("expected status=" + testCase.expectedStatus() + " but was=" + actualStatus);
        }
        if (testCase.expectedTask() != null) {
            String actualTask = classification.task() == null
                    ? null
                    : classification.task().taskTypeCode().name();
            if (!testCase.expectedTask().equals(actualTask)) {
                issues.add("expected task=" + testCase.expectedTask() + " but was=" + actualTask);
            }
        }
        if ("CANDIDATES".equals(actualStatus)) {
            List<String> candidates = classification.candidates().stream()
                    .map(candidate -> candidate.taskTypeCode().name())
                    .toList();
            if (candidates.size() < 2 || candidates.size() > 3) {
                issues.add("candidate count must be between 2 and 3");
            }
            if (new LinkedHashSet<>(candidates).size() != candidates.size()) {
                issues.add("candidate response contains duplicates");
            }
            if (!ALLOWED_TASK_CODES.containsAll(candidates)) {
                issues.add("candidate response contains an unsupported task code");
            }
            if (testCase.expectedCandidateIncludes() != null
                    && !candidates.contains(testCase.expectedCandidateIncludes())) {
                issues.add("candidate response is missing=" + testCase.expectedCandidateIncludes());
            }
        }
        if (testCase.inputMethod() == InputMethod.VOICE && !classification.sttRecheckNeeded()) {
            issues.add("voice input must require STT recheck");
        }
        if (testCase.correctedMustContain() != null
                && !classification.correctedUtterance().contains(testCase.correctedMustContain())) {
            issues.add("corrected utterance does not contain the expected correction token");
        }

        List<String> effectiveFraudPatterns = fraudAssessment.patterns().stream()
                .map(pattern -> pattern.type().name())
                .toList();
        if (!effectiveFraudPatterns.containsAll(testCase.expectedFraudPatterns())) {
            issues.add("missing expected fraud patterns=" + testCase.expectedFraudPatterns());
        }
        if (testCase.expectedFraudPatterns().isEmpty() && !effectiveFraudPatterns.isEmpty()) {
            issues.add("normal control was classified as fraud");
        }

        return new CaseResult(
                testCase.id(),
                testCase.category(),
                testCase.expectedStatus(),
                actualStatus,
                testCase.expectedTask(),
                classification.task() == null ? null : classification.task().taskTypeCode().name(),
                classification.candidates().stream()
                        .map(candidate -> candidate.taskTypeCode().name())
                        .toList(),
                effectiveFraudPatterns,
                output.confidence(),
                output.model(),
                output.promptVersion(),
                issues.isEmpty(),
                List.copyOf(issues)
        );
    }

    private void validateStructuredOutput(
            RegressionCase testCase,
            LlmAnalysisOutput output,
            List<String> issues
    ) {
        if (output.model() == null || output.model().isBlank()) {
            issues.add("response model is missing");
        }
        if (output.promptVersion() == null || output.promptVersion().isBlank()) {
            issues.add("prompt version is missing");
        }
        if (output.correctedText() == null || output.correctedText().isBlank()) {
            issues.add("corrected_text is blank");
        }
        if (output.confidence() == null
                || output.confidence().isNaN()
                || output.confidence().isInfinite()
                || output.confidence() < 0.0
                || output.confidence() > 1.0) {
            issues.add("confidence is outside 0..1");
        }
        if (!output.intent().isBlank() && !ALLOWED_TASK_CODES.contains(output.intent())) {
            issues.add("intent contains an unsupported task code");
        }
        if (output.candidates().size() > 3) {
            issues.add("structured candidates exceed three items");
        }
        if (new LinkedHashSet<>(output.candidates()).size() != output.candidates().size()) {
            issues.add("structured candidates contain duplicates");
        }
        if (!ALLOWED_TASK_CODES.containsAll(output.candidates())) {
            issues.add("structured candidates contain an unsupported task code");
        }
        output.fraudPatterns().forEach(pattern -> {
            if (!ALLOWED_FRAUD_PATTERNS.contains(pattern.type())) {
                issues.add("structured fraud pattern contains an unsupported code");
            }
            if (pattern.evidence() == null
                    || pattern.evidence().isBlank()
                    || !testCase.utterance().contains(pattern.evidence().trim())) {
                issues.add("fraud evidence is not an exact phrase from the utterance");
            }
            if (pattern.explanation() == null || pattern.explanation().isBlank()) {
                issues.add("fraud explanation is blank");
            }
        });
    }

    private RegressionDataset readDataset() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/gpt-regression-dataset.json")) {
            if (input == null) {
                throw new IllegalStateException("gpt-regression-dataset.json을 찾을 수 없습니다.");
            }
            return OBJECT_MAPPER.readValue(input, RegressionDataset.class);
        }
    }

    private List<Path> writeReports(Path reportDirectory, RegressionReport report) throws IOException {
        Files.createDirectories(reportDirectory);
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneOffset.UTC)
                .format(report.completedAt());
        Path timestamped = reportDirectory.resolve("gpt-regression-" + timestamp + ".json");
        Path latest = reportDirectory.resolve("latest.json");
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(timestamped.toFile(), report);
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(latest.toFile(), report);
        return List.of(timestamped, latest);
    }

    record RegressionDataset(String version, List<RegressionCase> cases) {
        RegressionDataset {
            cases = cases == null ? List.of() : List.copyOf(cases);
        }
    }

    record RegressionCase(
            String id,
            String category,
            String utterance,
            InputMethod inputMethod,
            String expectedStatus,
            String expectedTask,
            String expectedCandidateIncludes,
            String correctedMustContain,
            List<String> expectedFraudPatterns
    ) {
        RegressionCase {
            expectedFraudPatterns = expectedFraudPatterns == null
                    ? List.of()
                    : List.copyOf(expectedFraudPatterns);
        }
    }

    record CaseResult(
            String caseId,
            String category,
            String expectedStatus,
            String actualStatus,
            String expectedTask,
            String actualTask,
            List<String> candidates,
            List<String> fraudPatterns,
            Double confidence,
            String responseModel,
            String promptVersion,
            boolean passed,
            List<String> issues
    ) {
        static CaseResult transportFailure(RegressionCase testCase) {
            return new CaseResult(
                    testCase.id(),
                    testCase.category(),
                    testCase.expectedStatus(),
                    "LLM_ERROR",
                    testCase.expectedTask(),
                    null,
                    List.of(),
                    List.of(),
                    null,
                    "unknown",
                    "unknown",
                    false,
                    List.of("external LLM request failed")
            );
        }
    }

    record RegressionReport(
            String datasetVersion,
            String configurationSource,
            String requestedModel,
            Set<String> responseModels,
            Set<String> promptVersions,
            Instant startedAt,
            Instant completedAt,
            int plannedCases,
            int executedCases,
            int passedCases,
            int failedCases,
            Map<String, Integer> confidenceDistribution,
            List<CaseResult> results
    ) {
        static RegressionReport create(
                String datasetVersion,
                RegressionProfile profile,
                Instant startedAt,
                int plannedCases,
                Set<String> observedModels,
                Set<String> promptVersions,
                List<CaseResult> results
        ) {
            int passed = (int) results.stream().filter(CaseResult::passed).count();
            Map<String, Integer> confidenceDistribution = new LinkedHashMap<>();
            confidenceDistribution.put("belowCandidateFloor", 0);
            confidenceDistribution.put("candidateRange", 0);
            confidenceDistribution.put("confirmedRange", 0);
            confidenceDistribution.put("notAvailable", 0);
            for (CaseResult result : results) {
                String bucket = result.confidence() == null
                        ? "notAvailable"
                        : result.confidence() < 0.35
                        ? "belowCandidateFloor"
                        : result.confidence() < 0.75
                        ? "candidateRange"
                        : "confirmedRange";
                confidenceDistribution.computeIfPresent(bucket, (key, value) -> value + 1);
            }
            return new RegressionReport(
                    datasetVersion,
                    profile.configurationSource(),
                    profile.llmProperties().getPrimaryModel(),
                    Collections.unmodifiableSet(new LinkedHashSet<>(observedModels)),
                    Collections.unmodifiableSet(new LinkedHashSet<>(promptVersions)),
                    startedAt,
                    Instant.now(),
                    plannedCases,
                    results.size(),
                    passed,
                    results.size() - passed,
                    Collections.unmodifiableMap(new LinkedHashMap<>(confidenceDistribution)),
                    List.copyOf(results)
            );
        }
    }

    record RegressionProfile(
            LlmProperties llmProperties,
            ClassificationProperties classificationProperties,
            Path reportDirectory,
            String configurationSource,
            String skipReason
    ) {
        private static final String LOCAL_PROPERTIES_PATH = "src/main/resources/application-local.properties";

        static RegressionProfile load() throws IOException {
            Properties defaults = loadClasspathProperties();
            Properties local = loadLocalProperties();
            String apiKey = firstNonBlank(
                    System.getenv("GPT_REGRESSION_API_KEY"),
                    local.getProperty("piggyback.integrations.llm.api-key")
            );
            String baseUrl = firstNonBlank(
                    System.getenv("GPT_REGRESSION_BASE_URL"),
                    local.getProperty("piggyback.integrations.llm.base-url")
            );
            String model = firstNonBlank(
                    System.getenv("GPT_REGRESSION_MODEL"),
                    local.getProperty("piggyback.integrations.llm.primary-model"),
                    defaults.getProperty("piggyback.integrations.llm.primary-model")
            );

            LlmProperties llm = new LlmProperties();
            llm.setApiKey(apiKey);
            llm.setBaseUrl(baseUrl);
            llm.setPrimaryModel(model);
            llm.setFallbackModel(model);
            llm.setChatCompletionsPath(firstNonBlank(
                    local.getProperty("piggyback.integrations.llm.chat-completions-path"),
                    defaults.getProperty("piggyback.integrations.llm.chat-completions-path")
            ));
            llm.setConnectTimeout(parseDuration(firstNonBlank(
                    defaults.getProperty("piggyback.integrations.llm.connect-timeout"),
                    "PT3S"
            )));
            llm.setReadTimeout(parseDuration(firstNonBlank(
                    defaults.getProperty("piggyback.integrations.llm.read-timeout"),
                    "PT20S"
            )));

            ClassificationProperties classification = new ClassificationProperties();
            classification.setConfidenceThreshold(Double.parseDouble(defaults.getProperty(
                    "piggyback.classification.confidence-threshold",
                    "0.75"
            )));
            classification.setCandidateFloor(Double.parseDouble(defaults.getProperty(
                    "piggyback.classification.candidate-floor",
                    "0.35"
            )));
            classification.setCandidateLimit(Integer.parseInt(defaults.getProperty(
                    "piggyback.classification.candidate-limit",
                    "3"
            )));

            String source = !blank(System.getenv("GPT_REGRESSION_API_KEY"))
                    ? "environment"
                    : !blank(local.getProperty("piggyback.integrations.llm.api-key"))
                    ? "ignored-local-profile"
                    : "unconfigured";
            String skipReason = blank(apiKey)
                    ? "GPT 회귀 테스트를 건너뜁니다: GPT_REGRESSION_API_KEY 또는 로컬 LLM API Key가 없습니다."
                    : blank(baseUrl)
                    ? "GPT 회귀 테스트를 건너뜁니다: GPT_REGRESSION_BASE_URL 또는 로컬 LLM base URL이 없습니다."
                    : null;
            return new RegressionProfile(
                    llm,
                    classification,
                    Path.of(defaults.getProperty(
                            "piggyback.gpt-regression.report-directory",
                            "build/reports/gpt-regression"
                    )),
                    source,
                    skipReason
            );
        }

        boolean configured() {
            return skipReason == null;
        }

        private static Properties loadClasspathProperties() throws IOException {
            Properties properties = new Properties();
            try (InputStream input = GptTaskClassificationRegressionTest.class
                    .getResourceAsStream("/application-gpt-regression.properties")) {
                if (input == null) {
                    throw new IllegalStateException("application-gpt-regression.properties를 찾을 수 없습니다.");
                }
                properties.load(input);
            }
            return properties;
        }

        private static Properties loadLocalProperties() throws IOException {
            Properties properties = new Properties();
            Path localPath = Path.of(LOCAL_PROPERTIES_PATH);
            if (Files.isRegularFile(localPath)) {
                try (InputStream input = Files.newInputStream(localPath)) {
                    properties.load(input);
                }
            }
            return properties;
        }

        private static Duration parseDuration(String value) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("p")) {
                return Duration.parse(value.trim().toUpperCase(Locale.ROOT));
            }
            if (normalized.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
            }
            if (normalized.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
            }
            throw new IllegalArgumentException("지원하지 않는 duration 형식입니다.");
        }

        private static String firstNonBlank(String... values) {
            return Arrays.stream(values)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse("");
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }
}
