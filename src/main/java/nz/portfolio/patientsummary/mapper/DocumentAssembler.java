package nz.portfolio.patientsummary.mapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.SectionComponent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import nz.portfolio.patientsummary.fhir.Narratives;
import nz.portfolio.patientsummary.fhir.NzFhir;

/**
 * Assembles the NZPS document Bundle: Composition first, then every resource it references.
 *
 * <p>Profile constraints handled here:
 * <ul>
 *   <li>Bundle-uv-ips: {@code type} = document, {@code identifier} and {@code timestamp} are 1..1,
 *       every entry needs a {@code fullUrl}, and invariant bdl-ips-1 forbids any Composition other
 *       than the first entry.</li>
 *   <li>Composition-uv-ips: {@code type} is LOINC 60591-5; Problems (11450-4), Allergies (48765-2)
 *       and Medications (10160-0) sections are mandatory. Invariant ips-comp-1 requires each of them
 *       to have entries or an {@code emptyReason}; every section needs a title, code and narrative.</li>
 *   <li>Base cmp-2: a section with entries must not carry an emptyReason.</li>
 * </ul>
 */
@Component
public class DocumentAssembler {

    private static final TimeZone NZ_TIME = TimeZone.getTimeZone("Pacific/Auckland");

    public record ClinicalContent(
            List<DomainResource> problems,
            List<DomainResource> allergies,
            List<DomainResource> medications,
            List<DomainResource> immunizations) {
    }

    public Bundle assemble(Patient patient, Organization author, ClinicalContent content,
                           String compositionId, String bundleId, Instant now) {
        Date timestamp = Date.from(now);
        Reference patientRef = MapperSupport.referenceTo(patient, patient.getNameFirstRep().getNameAsSingleString());
        Reference authorRef = MapperSupport.referenceTo(author, author.getName());

        Composition composition = MapperSupport.prepare(new Composition(), compositionId, NzFhir.PROFILE_COMPOSITION);
        composition.setStatus(Composition.CompositionStatus.FINAL);
        composition.setType(MapperSupport.concept(NzFhir.LOINC, NzFhir.LOINC_PATIENT_SUMMARY, "Patient summary Document"));
        composition.setSubject(patientRef);
        composition.setDateElement(new DateTimeType(timestamp, TemporalPrecisionEnum.SECOND, NZ_TIME));
        composition.addAuthor(authorRef);
        composition.setTitle("New Zealand Patient Summary - " + patient.getNameFirstRep().getNameAsSingleString());
        composition.setText(Narratives.paragraph("New Zealand Patient Summary (synthetic test data)"));

        composition.addSection(section("Problem List", NzFhir.LOINC_SECTION_PROBLEMS,
                "Problem list - Reported", content.problems(), "No known problems"));
        composition.addSection(section("Allergies and Intolerances", NzFhir.LOINC_SECTION_ALLERGIES,
                "Allergies and adverse reactions Document", content.allergies(), "No known allergies"));
        composition.addSection(section("Medication Summary", NzFhir.LOINC_SECTION_MEDICATIONS,
                "History of Medication use Narrative", content.medications(), "No known medications"));
        if (!content.immunizations().isEmpty()) {
            // Immunizations is a recommended (0..1) section: only emitted when there is content.
            composition.addSection(section("Immunizations", NzFhir.LOINC_SECTION_IMMUNIZATIONS,
                    "History of Immunization note", content.immunizations(), null));
        }

        Bundle bundle = new Bundle();
        bundle.setId(bundleId);
        bundle.getMeta().addProfile(NzFhir.PROFILE_BUNDLE);
        bundle.getMeta().addTag(NzFhir.ACT_REASON, NzFhir.TEST_DATA_CODE, NzFhir.TEST_DATA_DISPLAY);
        bundle.getIdentifier().setSystem("urn:ietf:rfc:3986").setValue("urn:uuid:" + bundleId);
        bundle.setType(Bundle.BundleType.DOCUMENT);
        bundle.setTimestampElement(new InstantType(timestamp, TemporalPrecisionEnum.SECOND, NZ_TIME));

        List<DomainResource> entries = new ArrayList<>();
        entries.add(composition);
        entries.add(patient);
        entries.add(author);
        entries.addAll(content.problems());
        entries.addAll(content.allergies());
        entries.addAll(content.medications());
        entries.addAll(content.immunizations());
        entries.forEach(resource -> bundle.addEntry()
                .setFullUrl("urn:uuid:" + resource.getIdElement().getIdPart())
                .setResource(resource));
        return bundle;
    }

    private static SectionComponent section(String title, String loinc, String loincDisplay,
                                            List<DomainResource> entries, String emptyText) {
        SectionComponent section = new SectionComponent();
        section.setTitle(title);
        section.setCode(MapperSupport.concept(NzFhir.LOINC, loinc, loincDisplay));
        if (entries.isEmpty()) {
            section.setText(Narratives.paragraph(emptyText));
            section.setEmptyReason(MapperSupport.concept(NzFhir.LIST_EMPTY_REASON, "nilknown", "Nil Known"));
        } else {
            section.setText(Narratives.list(entries.stream()
                    .map(resource -> resource.getText().getDiv().allText())
                    .toList()));
            entries.forEach(resource -> section.addEntry(MapperSupport.referenceTo(resource, null)));
        }
        return section;
    }
}
