package nz.portfolio.patientsummary.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import nz.portfolio.patientsummary.service.PatientSummaryService;
import nz.portfolio.patientsummary.service.PatientSummaryService.GeneratedSummary;
import nz.portfolio.patientsummary.synthetic.Nhi;
import nz.portfolio.patientsummary.synthetic.SyntheticPatientFactory;

/** Generates many synthetic patients and requires every resulting Bundle to validate with zero errors. */
@SpringBootTest
class GeneratedSummaryValidationIT {

    @Autowired
    PatientSummaryService summaryService;

    @Autowired
    SyntheticPatientFactory factory;

    static Stream<Arguments> seeds() {
        return LongStream.rangeClosed(1, 20).boxed()
                .map(seed -> Arguments.of(seed, seed % 2 == 0 ? Nhi.Format.EXPANDED : Nhi.Format.LEGACY));
    }

    @ParameterizedTest(name = "seed {0}, {1} NHI")
    @MethodSource("seeds")
    void syntheticSummaryHasNoValidationErrors(long seed, Nhi.Format format) {
        GeneratedSummary summary = summaryService.generate(factory.create(seed, format), true, SyntheticPatientFactory.idsFor(seed));

        assertThat(summary.validation().errors())
                .as("validation errors:%n%s", describe(summary.validation().outcome(), true))
                .isZero();
    }

    static String describe(OperationOutcome outcome, boolean errorsOnly) {
        return outcome.getIssue().stream()
                .filter(i -> !errorsOnly || i.getSeverity() == IssueSeverity.ERROR || i.getSeverity() == IssueSeverity.FATAL)
                .map(i -> i.getSeverity() + " " + i.getExpression() + " " + i.getDiagnostics())
                .collect(Collectors.joining("\n"));
    }
}
