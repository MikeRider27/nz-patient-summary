package nz.portfolio.patientsummary.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ca.uhn.fhir.context.FhirContext;
import nz.portfolio.patientsummary.mapper.ClinicalMapper;
import nz.portfolio.patientsummary.mapper.DocumentAssembler;
import nz.portfolio.patientsummary.mapper.OrganizationMapper;
import nz.portfolio.patientsummary.mapper.PatientMapper;
import nz.portfolio.patientsummary.service.PatientSummaryService;
import nz.portfolio.patientsummary.service.PatientSummaryService.GeneratedSummary;
import nz.portfolio.patientsummary.synthetic.Nhi;
import nz.portfolio.patientsummary.synthetic.SyntheticPatientFactory;
import nz.portfolio.patientsummary.validation.ValidationService;

/**
 * Validates the committed example Bundles in /examples. Regenerate them with
 * {@code ./mvnw verify -Dexamples.regenerate=true}.
 */
@SpringBootTest
class ExamplesIT {

    static final Path EXAMPLES = Path.of("examples");

    /** name, seed, NHI format. Seeds chosen to cover allergies, empty sections and both NHI formats. */
    record Example(String name, long seed, Nhi.Format format) {
    }

    static final Example[] EXAMPLE_SET = {
            new Example("nzps-example-1", 3, Nhi.Format.LEGACY),
            new Example("nzps-example-2", 8, Nhi.Format.EXPANDED),
            new Example("nzps-example-3", 15, Nhi.Format.LEGACY),
    };

    @Autowired PatientMapper patientMapper;
    @Autowired ClinicalMapper clinicalMapper;
    @Autowired OrganizationMapper organizationMapper;
    @Autowired DocumentAssembler assembler;
    @Autowired ValidationService validationService;
    @Autowired FhirContext fhirContext;
    @Autowired SyntheticPatientFactory factory;

    @Test
    @EnabledIfSystemProperty(named = "examples.regenerate", matches = "true")
    void regenerateExamples() throws IOException {
        Clock fixed = Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), ZoneId.of("Pacific/Auckland"));
        PatientSummaryService service = new PatientSummaryService(patientMapper, clinicalMapper, organizationMapper,
                assembler, validationService, fhirContext, fixed);
        Files.createDirectories(EXAMPLES);
        for (Example example : EXAMPLE_SET) {
            GeneratedSummary summary = service.generate(factory.create(example.seed(), example.format()), true,
                    SyntheticPatientFactory.idsFor(example.seed()));
            Files.writeString(EXAMPLES.resolve(example.name() + ".json"), summary.json() + "\n", StandardCharsets.UTF_8);
        }
    }

    static Stream<Path> exampleFiles() throws IOException {
        try (Stream<Path> files = Files.list(EXAMPLES)) {
            return files.filter(p -> p.getFileName().toString().matches("nzps-example-\\d+\\.json")).sorted().toList().stream();
        }
    }

    @Test
    void thereAreThreeExamples() throws IOException {
        assertThat(exampleFiles()).hasSize(3);
    }

    @ParameterizedTest
    @MethodSource("exampleFiles")
    void exampleValidatesWithZeroErrors(Path file) throws IOException {
        ValidationService.Report report = validationService.validateAgainstDefaultProfile(Files.readString(file));

        // Keep the validation report next to the example so reviewers can inspect it without running anything.
        // Only rewritten on regeneration: messages contain object hashes that differ between runs.
        if (Boolean.getBoolean("examples.regenerate")) {
            String outcomeName = file.getFileName().toString().replace(".json", ".validation.json");
            Files.writeString(file.resolveSibling(outcomeName),
                    fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(report.outcome()) + "\n",
                    StandardCharsets.UTF_8);
        }

        assertThat(report.errors())
                .as("validation errors in %s:%n%s", file, GeneratedSummaryValidationIT.describe(report.outcome(), true))
                .isZero();
    }
}
