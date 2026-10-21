package nz.portfolio.patientsummary.validation;

import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.ElementDefinition.DiscriminatorType;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nz.portfolio.patientsummary.fhir.NzFhir;

/**
 * Minimal, documented corrections to published profiles that are otherwise impossible to conform to.
 * Each patch is applied in memory at start-up; the original package files are never modified.
 */
final class ProfilePatches {

    private static final Logger log = LoggerFactory.getLogger(ProfilePatches.class);

    private ProfilePatches() {
    }

    static void apply(PrePopulatedValidationSupport packages) {
        patchNhiIdentifierSlicing(packages);
    }

    /**
     * NZPS 0.7.0 {@code nzps-patient} slices {@code Patient.identifier} into {@code NHI}
     * ({@code use} = official) and {@code dormant} ({@code use} = old), but discriminates on
     * {@code system} only. Both slices fix the same NHI system, so every NHI identifier matches
     * both slices and the validator reports "Element matches more than one slice". Adding
     * {@code use} to the discriminator expresses what the profile evidently intends.
     */
    private static void patchNhiIdentifierSlicing(PrePopulatedValidationSupport packages) {
        StructureDefinition profile = (StructureDefinition) packages.fetchStructureDefinition(NzFhir.PROFILE_PATIENT);
        if (profile == null) {
            log.warn("{} not loaded - NHI slicing patch skipped", NzFhir.PROFILE_PATIENT);
            return;
        }
        int patched = 0;
        for (ElementDefinition element : profile.getSnapshot().getElement()) {
            patched += addUseDiscriminator(element);
        }
        for (ElementDefinition element : profile.getDifferential().getElement()) {
            patched += addUseDiscriminator(element);
        }
        if (patched > 0) {
            packages.addStructureDefinition(profile);
            log.info("Patched {} : Patient.identifier slicing now discriminates on system + use", NzFhir.PROFILE_PATIENT);
        }
    }

    private static int addUseDiscriminator(ElementDefinition element) {
        if (!"Patient.identifier".equals(element.getId()) || !element.hasSlicing()) {
            return 0;
        }
        boolean alreadyPresent = element.getSlicing().getDiscriminator().stream()
                .anyMatch(d -> "use".equals(d.getPath()));
        if (alreadyPresent) {
            return 0;
        }
        element.getSlicing().addDiscriminator().setType(DiscriminatorType.VALUE).setPath("use");
        return 1;
    }
}
