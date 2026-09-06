package com.piggyback.backend.classification.regression;

import com.piggyback.backend.domain.TaskTypeCode;
import com.piggyback.backend.classification.domain.FraudPatternType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GptRegressionDatasetContractTest {

    private static final Path DATASET_PATH = Path.of(
            "src/gptRegressionTest/resources/gpt-regression-dataset.json"
    );

    @Test
    void fixedDatasetCoversTasksConfidenceSttAndFraudWithoutExceedingTheCostCap() throws Exception {
        RegressionDataset dataset;
        try (InputStream input = Files.newInputStream(DATASET_PATH)) {
            dataset = new ObjectMapper().readValue(input, RegressionDataset.class);
        }

        assertEquals("2026-09-06-v1", dataset.version());
        assertTrue(dataset.cases().size() <= 20);
        assertEquals(
                dataset.cases().size(),
                dataset.cases().stream().map(RegressionCase::id).distinct().count()
        );

        Set<String> coveredTaskCodes = dataset.cases().stream()
                .filter(testCase -> "TASK_CODE".equals(testCase.category()))
                .map(RegressionCase::expectedTask)
                .collect(Collectors.toSet());
        assertEquals(
                Arrays.stream(TaskTypeCode.values()).map(Enum::name).collect(Collectors.toSet()),
                coveredTaskCodes
        );

        Set<String> confidenceStatuses = dataset.cases().stream()
                .filter(testCase -> Set.of("TASK_CODE", "CONFIDENCE_FLOW")
                        .contains(testCase.category()))
                .map(RegressionCase::expectedStatus)
                .collect(Collectors.toSet());
        assertTrue(confidenceStatuses.containsAll(Set.of("CONFIRMED", "CANDIDATES", "UNCLASSIFIED")));
        assertTrue(dataset.cases().stream().anyMatch(testCase -> "STT_CORRECTION".equals(testCase.category())));

        Set<String> singleFraudPatterns = dataset.cases().stream()
                .filter(testCase -> "FRAUD_SINGLE".equals(testCase.category()))
                .flatMap(testCase -> testCase.expectedFraudPatterns().stream())
                .collect(Collectors.toSet());
        assertEquals(
                Arrays.stream(FraudPatternType.values()).map(Enum::name).collect(Collectors.toSet()),
                singleFraudPatterns
        );
        assertTrue(dataset.cases().stream().anyMatch(testCase -> "FRAUD_COMPOUND".equals(testCase.category())));
        assertEquals(
                2,
                dataset.cases().stream().filter(testCase -> "FRAUD_CONTROL".equals(testCase.category())).count()
        );
    }

    record RegressionDataset(String version, List<RegressionCase> cases) {
    }

    record RegressionCase(
            String id,
            String category,
            String expectedStatus,
            String expectedTask,
            List<String> expectedFraudPatterns
    ) {
        RegressionCase {
            expectedFraudPatterns = expectedFraudPatterns == null
                    ? List.of()
                    : List.copyOf(expectedFraudPatterns);
        }
    }
}
