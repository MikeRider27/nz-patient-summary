package nz.portfolio.patientsummary.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

import nz.portfolio.patientsummary.dto.PatientSummaryRequest.AddressInput;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest.PatientInput;
import nz.portfolio.patientsummary.fhir.NzFhir;

class PatientMapperTest {

    private static final Map<String, String> ETHNICITIES = Map.of("21111", "Māori", "11111", "New Zealand European");
    private final PatientMapper mapper = new PatientMapper(code -> Optional.ofNullable(ETHNICITIES.get(code)));

    private static PatientInput input(String nhi, List<String> ethnicities) {
        return new PatientInput(nhi, "Ngata", List.of("Aroha", "Mere"), "female", LocalDate.of(1984, 3, 14),
                ethnicities, new AddressInput(List.of("12 Kowhai Street"), "Ponsonby", "Auckland", "1011"), "021 000 0000", null);
    }

    @Test
    void mapsNhiAsOfficialIdentifierWithNzSystem() {
        Patient patient = mapper.toPatient(input("zaa0067", List.of()), "p1");

        Identifier nhi = patient.getIdentifierFirstRep();
        assertThat(nhi.getSystem()).isEqualTo("https://standards.digital.health.nz/ns/nhi-id");
        assertThat(nhi.getUse()).isEqualTo(Identifier.IdentifierUse.OFFICIAL);
        assertThat(nhi.getValue()).isEqualTo("ZAA0067"); // normalised to upper case
    }

    @Test
    void declaresNzpsProfileAndTestDataTag() {
        Patient patient = mapper.toPatient(input("ZAA0067", List.of()), "p1");

        assertThat(patient.getMeta().getProfile()).extracting("value").containsExactly(NzFhir.PROFILE_PATIENT);
        assertThat(patient.getMeta().getTag()).anySatisfy(tag -> assertThat(tag.getCode()).isEqualTo("HTEST"));
        assertThat(patient.getIdElement().getIdPart()).isEqualTo("p1");
    }

    @Test
    void mapsEthnicitiesToNzBaseExtensionWithLevel4Codes() {
        Patient patient = mapper.toPatient(input("ZAA0067", List.of("21111", "11111")), "p1");

        assertThat(patient.getExtensionsByUrl(NzFhir.ETHNICITY_EXTENSION)).hasSize(2);
        CodeableConcept first = (CodeableConcept) patient.getExtensionsByUrl(NzFhir.ETHNICITY_EXTENSION).get(0).getValue();
        assertThat(first.getCodingFirstRep().getSystem()).isEqualTo(NzFhir.ETHNICITY_LEVEL4_SYSTEM);
        assertThat(first.getCodingFirstRep().getCode()).isEqualTo("21111");
        assertThat(first.getCodingFirstRep().getDisplay()).isEqualTo("Māori");
    }

    @Test
    void mapsDemographicsRequiredByIps() {
        Patient patient = mapper.toPatient(input("ZAA0067", List.of()), "p1");

        assertThat(patient.getNameFirstRep().getFamily()).isEqualTo("Ngata");
        assertThat(patient.getNameFirstRep().getGivenAsSingleString()).isEqualTo("Aroha Mere");
        assertThat(patient.getBirthDateElement().getValueAsString()).isEqualTo("1984-03-14");
        assertThat(patient.getAddressFirstRep().getCountry()).isEqualTo("NZ");
        assertThat(((StringType) patient.getAddressFirstRep().getExtensionByUrl(NzFhir.SUBURB_EXTENSION).getValue()).getValue())
                .isEqualTo("Ponsonby");
        assertThat(patient.getText().getDivAsString()).contains("synthetic");
    }

    @Test
    void omitsIdentifierWhenNoNhiGiven() {
        assertThat(mapper.toPatient(input(null, List.of()), "p1").getIdentifier()).isEmpty();
    }

    @Test
    void rejectsNhiWithInvalidCheckDigit() {
        assertThatThrownBy(() -> mapper.toPatient(input("ZAA0068", List.of()), "p1"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("check-digit");
    }

    @Test
    void rejectsUnknownEthnicityCode() {
        assertThatThrownBy(() -> mapper.toPatient(input("ZAA0067", List.of("99998")), "p1"))
                .isInstanceOf(InvalidInputException.class)
                .extracting("field").isEqualTo("patient.ethnicityCodes");
    }

    @Test
    void rejectsMoreThanSixEthnicities() {
        List<String> seven = Collections.nCopies(7, "21111");
        assertThatThrownBy(() -> mapper.toPatient(input("ZAA0067", seven), "p1"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("at most 6");
    }
}
