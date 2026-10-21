package nz.portfolio.patientsummary.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ca.uhn.fhir.context.FhirContext;
import jakarta.validation.Valid;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest;
import nz.portfolio.patientsummary.service.PatientSummaryService;
import nz.portfolio.patientsummary.service.PatientSummaryService.GeneratedSummary;
import nz.portfolio.patientsummary.validation.ValidationService;

@RestController
@RequestMapping("/summary")
@Validated
public class SummaryController {

    private final PatientSummaryService summaryService;
    private final ValidationService validationService;
    private final FhirContext fhirContext;

    public SummaryController(PatientSummaryService summaryService, ValidationService validationService, FhirContext fhirContext) {
        this.summaryService = summaryService;
        this.validationService = validationService;
        this.fhirContext = fhirContext;
    }

    /**
     * Generates an NZPS document Bundle. The validation outcome is summarised in the
     * X-Validation-Errors / X-Validation-Warnings response headers.
     */
    @PostMapping(path = "/generate", consumes = "application/json", produces = FhirMediaTypes.FHIR_JSON)
    public ResponseEntity<String> generate(@Valid @RequestBody PatientSummaryRequest request,
                                           @RequestParam(defaultValue = "true") boolean validate) {
        GeneratedSummary summary = summaryService.generate(request, validate);
        return withValidationHeaders(summary).body(summary.json());
    }

    /**
     * Validates any FHIR resource (typically a document Bundle) against NZPS / NZ Base / IPS.
     * By default the NZPS Bundle profile is enforced; pass {@code profile=} (empty) to validate only
     * against the profiles declared in meta.profile, or any other canonical URL.
     */
    @PostMapping(path = "/validate", consumes = {FhirMediaTypes.FHIR_JSON, "application/json"}, produces = FhirMediaTypes.FHIR_JSON)
    public ResponseEntity<String> validate(@RequestBody String resource,
                                           @RequestParam(required = false) String profile) {
        ValidationService.Report report = profile == null
                ? validationService.validateAgainstDefaultProfile(resource)
                : validationService.validate(resource, profile);
        return ResponseEntity.ok()
                .header("X-Validation-Errors", String.valueOf(report.errors()))
                .header("X-Validation-Warnings", String.valueOf(report.warnings()))
                .body(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(report.outcome()));
    }

    static ResponseEntity.BodyBuilder withValidationHeaders(GeneratedSummary summary) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"nzps-" + summary.bundle().getIdElement().getIdPart() + ".json\"");
        if (summary.validation() != null) {
            response.header("X-Validation-Errors", String.valueOf(summary.validation().errors()))
                    .header("X-Validation-Warnings", String.valueOf(summary.validation().warnings()));
        }
        return response;
    }
}
