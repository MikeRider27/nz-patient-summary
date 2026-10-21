package nz.portfolio.patientsummary.validation;

import org.hl7.fhir.instance.model.api.IBaseResource;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.IValidationSupport;

/**
 * Resolves versioned canonical references ({@code url|version}) that no loaded package satisfies
 * exactly, by falling back to the unversioned canonical.
 *
 * <p>Why this is needed: IPS 2.0.0 (and NZPS 0.7.0, which inherits from it) reference core types as
 * {@code http://hl7.org/fhir/StructureDefinition/Practitioner|4.0.1} and extensions such as
 * {@code individual-genderIdentity|5.3.0-ballot-tc1}, while the IPS package itself declares a
 * dependency on {@code hl7.fhir.uv.extensions.r4#5.2.0}. Without this fallback the validator reports
 * "Unable to resolve the profile reference" errors that are artefacts of package versioning, not
 * defects in the instance being validated.
 */
class VersionTolerantValidationSupport implements IValidationSupport {

    private final FhirContext fhirContext;
    private IValidationSupport root;

    VersionTolerantValidationSupport(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    /** The chain this support is part of; lookups of the unversioned URL go back through it. */
    void setRoot(IValidationSupport root) {
        this.root = root;
    }

    @Override
    public FhirContext getFhirContext() {
        return fhirContext;
    }

    @Override
    public IBaseResource fetchStructureDefinition(String url) {
        String unversioned = stripVersion(url);
        return unversioned == null ? null : root.fetchStructureDefinition(unversioned);
    }

    @Override
    public IBaseResource fetchValueSet(String url) {
        String unversioned = stripVersion(url);
        return unversioned == null ? null : root.fetchValueSet(unversioned);
    }

    @Override
    public IBaseResource fetchCodeSystem(String url) {
        String unversioned = stripVersion(url);
        return unversioned == null ? null : root.fetchCodeSystem(unversioned);
    }

    @Override
    public String getName() {
        return "VersionTolerantValidationSupport";
    }

    private String stripVersion(String url) {
        if (root == null || url == null) {
            return null;
        }
        int bar = url.indexOf('|');
        return bar < 0 ? null : url.substring(0, bar);
    }
}
