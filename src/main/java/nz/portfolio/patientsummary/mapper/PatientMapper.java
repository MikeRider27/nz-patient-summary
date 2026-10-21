package nz.portfolio.patientsummary.mapper;

import java.util.Locale;

import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

import nz.portfolio.patientsummary.dto.PatientSummaryRequest.AddressInput;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest.PatientInput;
import nz.portfolio.patientsummary.fhir.Narratives;
import nz.portfolio.patientsummary.fhir.NzFhir;
import nz.portfolio.patientsummary.synthetic.Nhi;

/**
 * Maps patient demographics to an NZPS Patient (nzps-patient, derived from Patient-uv-ips).
 *
 * <p>Profile constraints handled here:
 * <ul>
 *   <li>Patient-uv-ips: {@code name} 1..* and {@code birthDate} 1..1 are mandatory.</li>
 *   <li>nzps-patient: the NHI slice is discriminated by {@code system} with {@code use} fixed to
 *       "official" (dormant NHIs use "old"); invariant nz-pat-1 allows at most one official NHI.</li>
 *   <li>nzps-patient: ethnicity uses the NZ Base {@code nz-ethnicity} extension (0..6) coded with
 *       the Stats NZ level 4 ethnicity classification.</li>
 *   <li>Address is typed as NZ Base {@code NzAddress}; the suburb goes in its {@code suburb} extension.</li>
 * </ul>
 */
@Component
public class PatientMapper {

    static final int MAX_ETHNICITIES = 6;

    private final EthnicityTerminology ethnicity;

    public PatientMapper(EthnicityTerminology ethnicity) {
        this.ethnicity = ethnicity;
    }

    public Patient toPatient(PatientInput input, String id) {
        Patient patient = MapperSupport.prepare(new Patient(), id, NzFhir.PROFILE_PATIENT);

        if (input.nhi() != null) {
            String nhi = input.nhi().toUpperCase(Locale.ROOT);
            if (!Nhi.isValid(nhi)) {
                throw new InvalidInputException("patient.nhi", "'" + input.nhi() + "' fails the NHI check-digit validation");
            }
            patient.addIdentifier()
                    .setUse(Identifier.IdentifierUse.OFFICIAL)
                    .setSystem(NzFhir.NHI_SYSTEM)
                    .setValue(nhi);
        }

        addEthnicities(patient, input);

        HumanName name = patient.addName().setUse(HumanName.NameUse.OFFICIAL).setFamily(input.familyName());
        input.givenNames().forEach(name::addGiven);

        patient.setGender(AdministrativeGender.fromCode(input.gender()));
        patient.setBirthDateElement(new DateType(input.birthDate().toString()));

        if (input.mobilePhone() != null) {
            patient.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE)
                    .setUse(ContactPoint.ContactPointUse.MOBILE).setValue(input.mobilePhone());
        }
        if (input.email() != null) {
            patient.addTelecom().setSystem(ContactPoint.ContactPointSystem.EMAIL)
                    .setUse(ContactPoint.ContactPointUse.HOME).setValue(input.email());
        }
        if (input.address() != null) {
            patient.addAddress(toAddress(input.address()));
        }

        patient.setText(Narratives.paragraph(name.getNameAsSingleString()
                + ", born " + input.birthDate()
                + (input.nhi() != null ? ", NHI " + input.nhi().toUpperCase(Locale.ROOT) : "")
                + " (synthetic test patient)"));
        return patient;
    }

    private void addEthnicities(Patient patient, PatientInput input) {
        if (input.ethnicityCodes().size() > MAX_ETHNICITIES) {
            throw new InvalidInputException("patient.ethnicityCodes", "at most " + MAX_ETHNICITIES + " ethnicities may be recorded");
        }
        for (String code : input.ethnicityCodes()) {
            String display = ethnicity.display(code).orElseThrow(() -> new InvalidInputException(
                    "patient.ethnicityCodes", "'" + code + "' is not a Stats NZ level 4 ethnicity code"));
            patient.addExtension(NzFhir.ETHNICITY_EXTENSION,
                    MapperSupport.concept(NzFhir.ETHNICITY_LEVEL4_SYSTEM, code, display));
        }
    }

    static Address toAddress(AddressInput input) {
        Address address = new Address()
                .setUse(Address.AddressUse.HOME)
                .setType(Address.AddressType.PHYSICAL)
                .setCity(input.city())
                .setPostalCode(input.postalCode())
                .setCountry("NZ");
        input.lines().forEach(address::addLine);
        if (input.suburb() != null) {
            address.addExtension(NzFhir.SUBURB_EXTENSION, new StringType(input.suburb()));
        }
        return address;
    }
}
