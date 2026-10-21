package nz.portfolio.patientsummary.mapper;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.AllergyIntolerance.AllergyIntoleranceCategory;
import org.hl7.fhir.r4.model.AllergyIntolerance.AllergyIntoleranceCriticality;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.MedicationStatement.MedicationStatementStatus;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

import nz.portfolio.patientsummary.dto.PatientSummaryRequest.AllergyInput;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest.ConditionInput;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest.ImmunizationInput;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest.MedicationInput;
import nz.portfolio.patientsummary.fhir.Narratives;
import nz.portfolio.patientsummary.fhir.NzFhir;

/**
 * Maps problems, medications, allergies and immunizations to their NZPS profiles.
 * All clinical concepts are SNOMED CT, matching the IPS value set bindings
 * (Problems/Allergies/Medications/Vaccines - IPS).
 */
@Component
public class ClinicalMapper {

    private static final String DATA_ABSENT_REASON = "http://hl7.org/fhir/StructureDefinition/data-absent-reason";

    /**
     * nzps-problem (Condition-uv-ips): {@code code} 1..1 and {@code subject.reference} 1..1.
     * Base invariant con-3 requires clinicalStatus for problem-list items, so it defaults to "active".
     */
    public Condition toCondition(ConditionInput input, String id, Reference patient) {
        String status = input.clinicalStatus() == null ? "active" : input.clinicalStatus();
        Condition condition = MapperSupport.prepare(new Condition(), id, NzFhir.PROFILE_PROBLEM);
        condition.setClinicalStatus(MapperSupport.concept(NzFhir.CONDITION_CLINICAL, status, MapperSupport.capitalise(status)));
        condition.setVerificationStatus(MapperSupport.concept(NzFhir.CONDITION_VER_STATUS, "confirmed", "Confirmed"));
        condition.addCategory(MapperSupport.concept(NzFhir.CONDITION_CATEGORY, "problem-list-item", "Problem List Item"));
        condition.setCode(MapperSupport.snomed(input.snomedCode(), input.display()));
        condition.setSubject(patient);
        if (input.onsetDate() != null) {
            condition.setOnset(MapperSupport.day(input.onsetDate()));
        }
        condition.setText(Narratives.paragraph(input.display() + " (" + status + ")"));
        return condition;
    }

    /**
     * nzps-medicationstatement (MedicationStatement-uv-ips): {@code effective[x]} is 1..1. When the
     * start date is unknown we send an empty dateTime carrying the data-absent-reason extension, which
     * is the IPS-sanctioned way to say "required but unknown". NZPS adds an optional NZMT coding slice.
     */
    public MedicationStatement toMedicationStatement(MedicationInput input, String id, Reference patient) {
        MedicationStatement statement = MapperSupport.prepare(new MedicationStatement(), id, NzFhir.PROFILE_MEDICATION_STATEMENT);
        statement.setStatus(MedicationStatementStatus.fromCode(input.status() == null ? "active" : input.status()));

        CodeableConcept medication = MapperSupport.snomed(input.snomedCode(), input.display());
        if (input.nzmtCode() != null) {
            medication.addCoding(new Coding(NzFhir.NZMT, input.nzmtCode(), input.display()));
        }
        statement.setMedication(medication);
        statement.setSubject(patient);

        if (input.startDate() != null) {
            statement.setEffective(new Period().setStartElement(MapperSupport.day(input.startDate())));
        } else {
            DateTimeType unknown = new DateTimeType();
            unknown.addExtension(DATA_ABSENT_REASON, new org.hl7.fhir.r4.model.CodeType("unknown"));
            statement.setEffective(unknown);
        }
        if (input.dosageText() != null) {
            statement.addDosage().setText(input.dosageText());
        }
        statement.setText(Narratives.paragraph(input.display()
                + (input.dosageText() != null ? " - " + input.dosageText() : "")));
        return statement;
    }

    /** nzps-allergyintolerance (AllergyIntolerance-uv-ips): {@code code} 1..1, {@code patient.reference} 1..1. */
    public AllergyIntolerance toAllergyIntolerance(AllergyInput input, String id, Reference patient) {
        AllergyIntolerance allergy = MapperSupport.prepare(new AllergyIntolerance(), id, NzFhir.PROFILE_ALLERGY);
        allergy.setClinicalStatus(MapperSupport.concept(NzFhir.ALLERGY_CLINICAL, "active", "Active"));
        allergy.setVerificationStatus(MapperSupport.concept(NzFhir.ALLERGY_VERIFICATION, "confirmed", "Confirmed"));
        allergy.setType(AllergyIntolerance.AllergyIntoleranceType.ALLERGY);
        if (input.category() != null) {
            allergy.addCategory(AllergyIntoleranceCategory.fromCode(input.category()));
        }
        if (input.criticality() != null) {
            allergy.setCriticality(AllergyIntoleranceCriticality.fromCode(input.criticality()));
        }
        allergy.setCode(MapperSupport.snomed(input.snomedCode(), input.display()));
        allergy.setPatient(patient);
        if (input.reactionSnomedCode() != null) {
            allergy.addReaction().addManifestation(MapperSupport.snomed(input.reactionSnomedCode(), input.reactionDisplay()));
        }
        allergy.setText(Narratives.paragraph(input.display()
                + (input.reactionDisplay() != null ? " - reaction: " + input.reactionDisplay() : "")));
        return allergy;
    }

    /** nzps-immunization (Immunization-uv-ips): {@code patient.reference} 1..1; base R4 requires occurrence[x]. */
    public Immunization toImmunization(ImmunizationInput input, String id, Reference patient) {
        Immunization immunization = MapperSupport.prepare(new Immunization(), id, NzFhir.PROFILE_IMMUNIZATION);
        immunization.setStatus(Immunization.ImmunizationStatus.COMPLETED);
        immunization.setVaccineCode(MapperSupport.snomed(input.snomedCode(), input.display()));
        immunization.setPatient(patient);
        immunization.setOccurrence(MapperSupport.day(input.occurrenceDate()));
        immunization.setPrimarySource(true);
        if (input.lotNumber() != null) {
            immunization.setLotNumber(input.lotNumber());
        }
        immunization.setText(Narratives.paragraph(input.display() + " given " + input.occurrenceDate()));
        return immunization;
    }
}
