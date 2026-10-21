package nz.portfolio.patientsummary.validation;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param packages               FHIR NPM package files (on the classpath under fhir-packages/) to load
 * @param defaultProfile         profile every Bundle sent to /summary/validate is checked against
 * @param terminologyServerUrl   optional FHIR terminology server (e.g. https://tx.fhir.org/r4) for full
 *                               SNOMED CT / LOINC validation; blank = offline validation only
 * @param warmUp                 pre-generate snapshots at start-up so the first request is fast
 */
@ConfigurationProperties(prefix = "fhir.validation")
public record FhirValidationProperties(
        List<String> packages,
        String defaultProfile,
        String terminologyServerUrl,
        boolean warmUp) {

    public FhirValidationProperties {
        packages = packages == null ? List.of() : List.copyOf(packages);
    }

    public boolean remoteTerminology() {
        return terminologyServerUrl != null && !terminologyServerUrl.isBlank();
    }
}
