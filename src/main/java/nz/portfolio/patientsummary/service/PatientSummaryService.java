package nz.portfolio.patientsummary.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ca.uhn.fhir.context.FhirContext;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest;
import nz.portfolio.patientsummary.mapper.ClinicalMapper;
import nz.portfolio.patientsummary.mapper.DocumentAssembler;
import nz.portfolio.patientsummary.mapper.DocumentAssembler.ClinicalContent;
import nz.portfolio.patientsummary.mapper.OrganizationMapper;
import nz.portfolio.patientsummary.mapper.PatientMapper;
import nz.portfolio.patientsummary.validation.ValidationService;

/** Orchestrates mapping, document assembly, serialization and validation. */
@Service
public class PatientSummaryService {

    private final PatientMapper patientMapper;
    private final ClinicalMapper clinicalMapper;
    private final OrganizationMapper organizationMapper;
    private final DocumentAssembler assembler;
    private final ValidationService validationService;
    private final FhirContext fhirContext;
    private final Clock clock;

    @Autowired
    public PatientSummaryService(PatientMapper patientMapper, ClinicalMapper clinicalMapper,
                                 OrganizationMapper organizationMapper, DocumentAssembler assembler,
                                 ValidationService validationService, FhirContext fhirContext) {
        this(patientMapper, clinicalMapper, organizationMapper, assembler, validationService, fhirContext, Clock.systemUTC());
    }

    public PatientSummaryService(PatientMapper patientMapper, ClinicalMapper clinicalMapper,
                                 OrganizationMapper organizationMapper, DocumentAssembler assembler,
                                 ValidationService validationService, FhirContext fhirContext, Clock clock) {
        this.patientMapper = patientMapper;
        this.clinicalMapper = clinicalMapper;
        this.organizationMapper = organizationMapper;
        this.assembler = assembler;
        this.validationService = validationService;
        this.fhirContext = fhirContext;
        this.clock = clock;
    }

    /** The generated document, its JSON form and the validation report for that JSON. */
    public record GeneratedSummary(Bundle bundle, String json, ValidationService.Report validation) {
    }

    public GeneratedSummary generate(PatientSummaryRequest request, boolean validate) {
        return generate(request, validate, () -> UUID.randomUUID().toString());
    }

    /** @param ids supplies logical ids / urn:uuid values; pass a seeded supplier for reproducible output */
    public GeneratedSummary generate(PatientSummaryRequest request, boolean validate, Supplier<String> ids) {
        Patient patient = patientMapper.toPatient(request.patient(), ids.get());
        Organization author = organizationMapper.toOrganization(request.author(), ids.get());
        Reference patientRef = new Reference("urn:uuid:" + patient.getIdElement().getIdPart());

        List<DomainResource> problems = request.conditions().stream()
                .<DomainResource>map(c -> clinicalMapper.toCondition(c, ids.get(), patientRef)).toList();
        List<DomainResource> allergies = request.allergies().stream()
                .<DomainResource>map(a -> clinicalMapper.toAllergyIntolerance(a, ids.get(), patientRef)).toList();
        List<DomainResource> medications = request.medications().stream()
                .<DomainResource>map(m -> clinicalMapper.toMedicationStatement(m, ids.get(), patientRef)).toList();
        List<DomainResource> immunizations = request.immunizations().stream()
                .<DomainResource>map(i -> clinicalMapper.toImmunization(i, ids.get(), patientRef)).toList();

        Bundle bundle = assembler.assemble(patient, author,
                new ClinicalContent(problems, allergies, medications, immunizations),
                ids.get(), ids.get(), clock.instant());

        String json = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
        ValidationService.Report report = validate ? validationService.validateAgainstDefaultProfile(json) : null;
        return new GeneratedSummary(bundle, json, report);
    }
}
