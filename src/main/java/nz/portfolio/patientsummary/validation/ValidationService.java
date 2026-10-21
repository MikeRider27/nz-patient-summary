package nz.portfolio.patientsummary.validation;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationOptions;
import ca.uhn.fhir.validation.ValidationResult;

/** Validates FHIR JSON against the declared profiles plus (optionally) an explicit profile. */
@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private final FhirValidator validator;
    private final FhirValidationProperties properties;

    public ValidationService(FhirValidator validator, FhirValidationProperties properties) {
        this.validator = validator;
        this.properties = properties;
    }

    public record Report(OperationOutcome outcome, long errors, long warnings) {

        public boolean isValid() {
            return errors == 0;
        }
    }

    /**
     * Validates raw JSON. Validating the serialized form (rather than a parsed object) means the
     * result reflects exactly what a receiving system would see on the wire.
     *
     * @param profile explicit profile to validate against in addition to meta.profile, or null
     */
    public Report validate(String json, String profile) {
        ValidationOptions options = new ValidationOptions();
        if (profile != null && !profile.isBlank()) {
            options.addProfile(profile);
        }
        ValidationResult result = validator.validateWithResult(json, options);
        OperationOutcome outcome = (OperationOutcome) result.toOperationOutcome();
        long errors = outcome.getIssue().stream()
                .filter(issue -> issue.getSeverity() == IssueSeverity.ERROR || issue.getSeverity() == IssueSeverity.FATAL)
                .count();
        long warnings = outcome.getIssue().stream()
                .filter(issue -> issue.getSeverity() == IssueSeverity.WARNING)
                .count();
        return new Report(outcome, errors, warnings);
    }

    public Report validateAgainstDefaultProfile(String json) {
        return validate(json, properties.defaultProfile());
    }

    /** Generating snapshots for the NZPS/IPS profile tree takes a few seconds; do it before traffic arrives. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!properties.warmUp()) {
            return;
        }
        long start = System.currentTimeMillis();
        validateAgainstDefaultProfile("{\"resourceType\":\"Bundle\",\"type\":\"document\"}");
        log.info("FHIR validator warmed up in {} ms", System.currentTimeMillis() - start);
    }
}
