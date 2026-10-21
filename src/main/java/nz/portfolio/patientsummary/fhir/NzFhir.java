package nz.portfolio.patientsummary.fhir;

/**
 * Canonical URLs, code systems and profiles used by the New Zealand Patient Summary.
 * Every value here was taken from the published package definitions
 * (tewhatuora.fhir.nzps 0.7.0, fhir.org.nz.ig.base 3.0.1, hl7.fhir.uv.ips 2.0.0).
 */
public final class NzFhir {

    private NzFhir() {
    }

    // --- NZ identifiers and extensions (NZ Base) -------------------------------------------------
    public static final String NHI_SYSTEM = "https://standards.digital.health.nz/ns/nhi-id";
    public static final String ETHNICITY_EXTENSION = "http://hl7.org.nz/fhir/StructureDefinition/nz-ethnicity";
    public static final String ETHNICITY_LEVEL4_SYSTEM = "https://standards.digital.health.nz/ns/ethnic-group-level-4-code";
    public static final String SUBURB_EXTENSION = "http://hl7.org.nz/fhir/StructureDefinition/suburb";

    // --- Terminologies ---------------------------------------------------------------------------
    public static final String SNOMED = "http://snomed.info/sct";
    public static final String LOINC = "http://loinc.org";
    public static final String NZMT = "http://nzmt.org.nz";
    public static final String CONDITION_CLINICAL = "http://terminology.hl7.org/CodeSystem/condition-clinical";
    public static final String CONDITION_VER_STATUS = "http://terminology.hl7.org/CodeSystem/condition-ver-status";
    public static final String CONDITION_CATEGORY = "http://terminology.hl7.org/CodeSystem/condition-category";
    public static final String ALLERGY_CLINICAL = "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical";
    public static final String ALLERGY_VERIFICATION = "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification";
    public static final String LIST_EMPTY_REASON = "http://terminology.hl7.org/CodeSystem/list-empty-reason";
    public static final String ACT_REASON = "http://terminology.hl7.org/CodeSystem/v3-ActReason";

    /** HL7 v3 ActReason "HTEST" (test health data): marks every synthetic resource as test data. */
    public static final String TEST_DATA_CODE = "HTEST";
    public static final String TEST_DATA_DISPLAY = "test health data";

    // --- NZPS profiles ---------------------------------------------------------------------------
    private static final String NZPS = "https://fhir-ig.digital.health.nz/nzps/StructureDefinition/";
    public static final String PROFILE_BUNDLE = NZPS + "nzps-bundle";
    public static final String PROFILE_COMPOSITION = NZPS + "nzps-composition";
    public static final String PROFILE_PATIENT = NZPS + "nzps-patient";
    public static final String PROFILE_PROBLEM = NZPS + "nzps-problem";
    public static final String PROFILE_MEDICATION_STATEMENT = NZPS + "nzps-medicationstatement";
    public static final String PROFILE_ALLERGY = NZPS + "nzps-allergyintolerance";
    public static final String PROFILE_IMMUNIZATION = NZPS + "nzps-immunization";
    public static final String PROFILE_ORGANIZATION = NZPS + "NZPS-organization";

    // --- IPS Composition document type and section codes (LOINC) ---------------------------------
    public static final String LOINC_PATIENT_SUMMARY = "60591-5";
    public static final String LOINC_SECTION_PROBLEMS = "11450-4";
    public static final String LOINC_SECTION_ALLERGIES = "48765-2";
    public static final String LOINC_SECTION_MEDICATIONS = "10160-0";
    public static final String LOINC_SECTION_IMMUNIZATIONS = "11369-6";
}
