package nz.portfolio.patientsummary.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.SectionComponent;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

import nz.portfolio.patientsummary.dto.PatientSummaryRequest.ConditionInput;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest.PatientInput;
import nz.portfolio.patientsummary.fhir.NzFhir;
import nz.portfolio.patientsummary.mapper.DocumentAssembler.ClinicalContent;

class DocumentAssemblerTest {

    private final DocumentAssembler assembler = new DocumentAssembler();

    private Bundle assemble(List<DomainResource> problems) {
        Patient patient = new PatientMapper(code -> Optional.empty()).toPatient(new PatientInput(
                "ZAA0067", "Smith", List.of("Olivia"), "female", LocalDate.of(1990, 1, 1), List.of(), null, null, null), "patient-1");
        Organization author = new OrganizationMapper().toOrganization(null, "org-1");
        return assembler.assemble(patient, author, new ClinicalContent(problems, List.of(), List.of(), List.of()),
                "composition-1", "bundle-1", Instant.parse("2026-10-21T09:00:00Z"));
    }

    @Test
    void bundleIsADocumentWithCompositionFirst() {
        Bundle bundle = assemble(List.of());

        assertThat(bundle.getType()).isEqualTo(Bundle.BundleType.DOCUMENT);
        assertThat(bundle.getIdentifier().getValue()).isEqualTo("urn:uuid:bundle-1");
        assertThat(bundle.getTimestamp()).isNotNull();
        assertThat(bundle.getMeta().getProfile()).extracting("value").containsExactly(NzFhir.PROFILE_BUNDLE);
        assertThat(bundle.getEntryFirstRep().getResource()).isInstanceOf(Composition.class);
        assertThat(bundle.getEntry()).extracting(BundleEntryComponent::getFullUrl).allMatch(url -> url.startsWith("urn:uuid:"));
    }

    @Test
    void compositionHasPatientSummaryTypeAndMandatoryIpsSections() {
        Composition composition = (Composition) assemble(List.of()).getEntryFirstRep().getResource();

        assertThat(composition.getType().getCodingFirstRep().getCode()).isEqualTo("60591-5");
        assertThat(composition.getSubject().getReference()).isEqualTo("urn:uuid:patient-1");
        assertThat(composition.getAuthorFirstRep().getReference()).isEqualTo("urn:uuid:org-1");
        assertThat(composition.getSection()).extracting(s -> s.getCode().getCodingFirstRep().getCode())
                .containsExactly("11450-4", "48765-2", "10160-0"); // immunizations omitted when empty
    }

    @Test
    void emptySectionsCarryEmptyReasonAndNoEntries() {
        Composition composition = (Composition) assemble(List.of()).getEntryFirstRep().getResource();

        for (SectionComponent section : composition.getSection()) {
            assertThat(section.getEntry()).isEmpty();
            assertThat(section.getEmptyReason().getCodingFirstRep().getCode()).isEqualTo("nilknown");
            assertThat(section.getText().getDivAsString()).isNotBlank();
        }
    }

    @Test
    void populatedSectionReferencesEveryResourceInTheBundle() {
        DomainResource problem = new ClinicalMapper().toCondition(
                new ConditionInput("90560007", "Gout", "active", null), "condition-1", new Reference("urn:uuid:patient-1"));
        Bundle bundle = assemble(List.of(problem));
        Composition composition = (Composition) bundle.getEntryFirstRep().getResource();

        SectionComponent problems = composition.getSection().get(0);
        assertThat(problems.hasEmptyReason()).isFalse();
        assertThat(problems.getEntryFirstRep().getReference()).isEqualTo("urn:uuid:condition-1");
        assertThat(bundle.getEntry()).extracting(BundleEntryComponent::getFullUrl).contains("urn:uuid:condition-1");
        assertThat(problems.getText().getDivAsString()).contains("Gout");
    }
}
