package nz.portfolio.patientsummary.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

import nz.portfolio.patientsummary.dto.PatientSummaryRequest.AllergyInput;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest.ConditionInput;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest.ImmunizationInput;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest.MedicationInput;
import nz.portfolio.patientsummary.fhir.NzFhir;

class ClinicalMapperTest {

    private final ClinicalMapper mapper = new ClinicalMapper();
    private final Reference patient = new Reference("urn:uuid:9a3f2c1e-0000-4000-8000-000000000001");

    @Test
    void conditionIsAConfirmedProblemListItemCodedInSnomed() {
        Condition condition = mapper.toCondition(
                new ConditionInput("44054006", "Diabetes mellitus type 2", null, LocalDate.of(2019, 6, 1)), "c1", patient);

        assertThat(condition.getMeta().getProfile()).extracting("value").containsExactly(NzFhir.PROFILE_PROBLEM);
        assertThat(condition.getCode().getCodingFirstRep().getSystem()).isEqualTo(NzFhir.SNOMED);
        assertThat(condition.getCode().getCodingFirstRep().getCode()).isEqualTo("44054006");
        assertThat(condition.getClinicalStatus().getCodingFirstRep().getCode()).isEqualTo("active");
        assertThat(condition.getVerificationStatus().getCodingFirstRep().getCode()).isEqualTo("confirmed");
        assertThat(condition.getCategoryFirstRep().getCodingFirstRep().getCode()).isEqualTo("problem-list-item");
        assertThat(condition.getSubject().getReference()).isEqualTo(patient.getReference());
        assertThat(condition.getOnsetDateTimeType().getValueAsString()).isEqualTo("2019-06-01");
    }

    @Test
    void medicationStatementUsesPeriodWhenStartDateKnown() {
        MedicationStatement statement = mapper.toMedicationStatement(
                new MedicationInput("372567009", "Metformin", null, "500 mg twice daily", LocalDate.of(2019, 6, 1), null),
                "m1", patient);

        assertThat(statement.getStatus()).isEqualTo(MedicationStatement.MedicationStatementStatus.ACTIVE);
        assertThat(statement.getEffective()).isInstanceOf(Period.class);
        assertThat(statement.getDosageFirstRep().getText()).isEqualTo("500 mg twice daily");
        assertThat(statement.getMedicationCodeableConcept().getCoding()).hasSize(1);
    }

    @Test
    void medicationStatementUsesDataAbsentReasonWhenStartDateUnknown() {
        MedicationStatement statement = mapper.toMedicationStatement(
                new MedicationInput("372567009", "Metformin", null, null, null, "active"), "m1", patient);

        DateTimeType effective = (DateTimeType) statement.getEffective();
        assertThat(effective.hasValue()).isFalse();
        assertThat(effective.getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/data-absent-reason")).isNotNull();
    }

    @Test
    void medicationStatementAddsNzmtCodingWhenProvided() {
        MedicationStatement statement = mapper.toMedicationStatement(
                new MedicationInput("372567009", "Metformin", "10006421000116100", null, null, null), "m1", patient);

        assertThat(statement.getMedicationCodeableConcept().getCoding())
                .extracting("system").containsExactly(NzFhir.SNOMED, NzFhir.NZMT);
    }

    @Test
    void allergyCarriesCriticalityAndReaction() {
        AllergyIntolerance allergy = mapper.toAllergyIntolerance(
                new AllergyInput("764146007", "Penicillin", "medication", "high", "39579001", "Anaphylaxis"), "a1", patient);

        assertThat(allergy.getCriticality()).isEqualTo(AllergyIntolerance.AllergyIntoleranceCriticality.HIGH);
        assertThat(allergy.getCategory()).extracting("value").containsExactly(AllergyIntolerance.AllergyIntoleranceCategory.MEDICATION);
        assertThat(allergy.getReactionFirstRep().getManifestationFirstRep().getCodingFirstRep().getCode()).isEqualTo("39579001");
        assertThat(allergy.getPatient().getReference()).isEqualTo(patient.getReference());
    }

    @Test
    void immunizationIsCompletedWithOccurrenceDate() {
        Immunization immunization = mapper.toImmunization(
                new ImmunizationInput("1181000221105", "Influenza vaccine", LocalDate.of(2025, 4, 14), "FLU-123"), "i1", patient);

        assertThat(immunization.getStatus()).isEqualTo(Immunization.ImmunizationStatus.COMPLETED);
        assertThat(immunization.getOccurrenceDateTimeType().getValueAsString()).isEqualTo("2025-04-14");
        assertThat(immunization.getLotNumber()).isEqualTo("FLU-123");
    }
}
