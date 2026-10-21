package nz.portfolio.patientsummary.mapper;

import java.time.LocalDate;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Reference;

import nz.portfolio.patientsummary.fhir.NzFhir;

/** Small helpers shared by all mappers. */
final class MapperSupport {

    private MapperSupport() {
    }

    /** Assigns a logical id, declares the NZPS profile and tags the resource as synthetic test data. */
    static <T extends DomainResource> T prepare(T resource, String id, String profile) {
        resource.setId(id);
        resource.getMeta().addProfile(profile);
        resource.getMeta().addTag(NzFhir.ACT_REASON, NzFhir.TEST_DATA_CODE, NzFhir.TEST_DATA_DISPLAY);
        return resource;
    }

    static CodeableConcept concept(String system, String code, String display) {
        return new CodeableConcept(new Coding(system, code, display));
    }

    static CodeableConcept snomed(String code, String display) {
        CodeableConcept concept = concept(NzFhir.SNOMED, code, display);
        concept.setText(display);
        return concept;
    }

    /** A day-precision FHIR dateTime, e.g. "2021-06-01". */
    static DateTimeType day(LocalDate date) {
        return new DateTimeType(date.toString());
    }

    /** Reference to a resource inside the same document Bundle (urn:uuid fullUrl). */
    static Reference referenceTo(DomainResource resource, String display) {
        return new Reference("urn:uuid:" + resource.getIdElement().getIdPart()).setDisplay(display);
    }

    static String capitalise(String code) {
        return Character.toUpperCase(code.charAt(0)) + code.substring(1);
    }
}
