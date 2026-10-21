package nz.portfolio.patientsummary.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Simple, clinician-friendly JSON input for generating a patient summary.
 * Clinical codes are SNOMED CT concept IDs, as required/preferred by the IPS and NZPS bindings.
 */
public record PatientSummaryRequest(
        @NotNull @Valid PatientInput patient,
        @Valid AuthorInput author,
        List<@Valid ConditionInput> conditions,
        List<@Valid MedicationInput> medications,
        List<@Valid AllergyInput> allergies,
        List<@Valid ImmunizationInput> immunizations) {

    public PatientSummaryRequest {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        medications = medications == null ? List.of() : List.copyOf(medications);
        allergies = allergies == null ? List.of() : List.copyOf(allergies);
        immunizations = immunizations == null ? List.of() : List.copyOf(immunizations);
    }

    public record PatientInput(
            @Pattern(regexp = "^[A-Za-z]{3}\\d{2}[A-Za-z0-9]{2}$", message = "must be 7 characters in NHI format AAANNNC or AAANNAX")
            String nhi,
            @NotBlank String familyName,
            @NotEmpty List<@NotBlank String> givenNames,
            @NotNull @Pattern(regexp = "male|female|other|unknown") String gender,
            @NotNull LocalDate birthDate,
            /* Ethnicity codes from the Stats NZ level 4 classification, e.g. 21111 (Maori), max 6. */
            List<@Pattern(regexp = "\\d{5}") String> ethnicityCodes,
            @Valid AddressInput address,
            String mobilePhone,
            String email) {

        public PatientInput {
            ethnicityCodes = ethnicityCodes == null ? List.of() : List.copyOf(ethnicityCodes);
        }
    }

    public record AddressInput(
            @NotEmpty List<@NotBlank String> lines,
            String suburb,
            @NotBlank String city,
            @Pattern(regexp = "\\d{4}", message = "must be a 4-digit NZ postcode") String postalCode) {
    }

    /** The organisation authoring the summary (e.g. the patient's general practice). */
    public record AuthorInput(@NotBlank String organisationName, String city) {
    }

    public record ConditionInput(
            @NotBlank @Pattern(regexp = "\\d{6,18}") String snomedCode,
            @NotBlank String display,
            @Pattern(regexp = "active|recurrence|relapse|inactive|remission|resolved") String clinicalStatus,
            LocalDate onsetDate) {
    }

    public record MedicationInput(
            @NotBlank @Pattern(regexp = "\\d{6,18}") String snomedCode,
            @NotBlank String display,
            /* Optional NZ Medicines Terminology (NZMT) code, added as an extra coding. */
            String nzmtCode,
            String dosageText,
            LocalDate startDate,
            @Pattern(regexp = "active|completed|stopped|on-hold|intended") String status) {
    }

    public record AllergyInput(
            @NotBlank @Pattern(regexp = "\\d{6,18}") String snomedCode,
            @NotBlank String display,
            @Pattern(regexp = "food|medication|environment|biologic") String category,
            @Pattern(regexp = "low|high|unable-to-assess") String criticality,
            @Pattern(regexp = "\\d{6,18}") String reactionSnomedCode,
            String reactionDisplay) {
    }

    public record ImmunizationInput(
            @NotBlank @Pattern(regexp = "\\d{6,18}") String snomedCode,
            @NotBlank String display,
            @NotNull LocalDate occurrenceDate,
            String lotNumber) {
    }
}
