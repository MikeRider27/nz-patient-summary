package nz.portfolio.patientsummary.validation;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.NpmPackageValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.RemoteTerminologyServiceValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.UnknownCodeSystemWarningValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;

/**
 * Wires a single, shared HAPI FHIR validator that knows the NZPS, NZ Base and IPS profiles.
 *
 * <p>Support chain (first match wins):
 * <ol>
 *   <li>NPM packages: NZPS 0.7.0, NZ Base 3.0.1, IPS 2.0.0 and their dependencies, loaded from the
 *       classpath (bundled in the jar at build time, so nothing is downloaded at runtime).</li>
 *   <li>Core FHIR R4 definitions.</li>
 *   <li>Snapshot generation (the IG packages only ship differentials for some profiles).</li>
 *   <li>In-memory terminology for code systems shipped in the packages (e.g. NZ ethnicity,
 *       HL7 terminology), plus common code systems (UCUM, ISO country/language, MIME types).</li>
 *   <li>Either a remote terminology server, or - offline - a fallback that reports code systems we
 *       cannot expand (SNOMED CT, LOINC) as warnings instead of errors.</li>
 * </ol>
 */
@Configuration
public class FhirValidationConfig {

    private static final Logger log = LoggerFactory.getLogger(FhirValidationConfig.class);

    @Bean
    FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    IValidationSupport validationSupport(FhirContext fhirContext, FhirValidationProperties properties) {
        NpmPackageValidationSupport npm = new NpmPackageValidationSupport(fhirContext);
        for (String fhirPackage : properties.packages()) {
            try {
                npm.loadPackageFromClasspath("classpath:fhir-packages/" + fhirPackage);
                log.info("Loaded FHIR package {}", fhirPackage);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot load FHIR package " + fhirPackage, e);
            }
        }
        ProfilePatches.apply(npm);

        VersionTolerantValidationSupport versionTolerant = new VersionTolerantValidationSupport(fhirContext);
        ValidationSupportChain chain = new ValidationSupportChain(
                npm,
                new DefaultProfileValidationSupport(fhirContext),
                new SnapshotGeneratingValidationSupport(fhirContext),
                new InMemoryTerminologyServerValidationSupport(fhirContext),
                new CommonCodeSystemsTerminologyService(fhirContext),
                versionTolerant);
        versionTolerant.setRoot(chain);

        if (properties.remoteTerminology()) {
            log.info("Using remote terminology server {}", properties.terminologyServerUrl());
            chain.addValidationSupport(new RemoteTerminologyServiceValidationSupport(fhirContext, properties.terminologyServerUrl()));
        } else {
            log.info("Offline terminology: SNOMED CT / LOINC codes are not checked against the full code systems");
            UnknownCodeSystemWarningValidationSupport unknown = new UnknownCodeSystemWarningValidationSupport(fhirContext);
            unknown.setNonExistentCodeSystemSeverity(IValidationSupport.IssueSeverity.WARNING);
            chain.addValidationSupport(unknown);
        }
        return chain;
    }

    @Bean
    FhirValidator fhirValidator(FhirContext fhirContext, IValidationSupport validationSupport) {
        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(validationSupport);
        FhirValidator validator = fhirContext.newValidator();
        validator.registerValidatorModule(instanceValidator);
        return validator;
    }
}
