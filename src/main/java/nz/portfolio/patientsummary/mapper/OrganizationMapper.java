package nz.portfolio.patientsummary.mapper;

import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Organization;
import org.springframework.stereotype.Component;

import nz.portfolio.patientsummary.dto.PatientSummaryRequest.AuthorInput;
import nz.portfolio.patientsummary.fhir.Narratives;
import nz.portfolio.patientsummary.fhir.NzFhir;

/** Maps the authoring organisation to NZPS-organization (Organization-uv-ips: {@code name} 1..1). */
@Component
public class OrganizationMapper {

    static final AuthorInput DEFAULT_AUTHOR = new AuthorInput("Synthetic Health Centre", null);

    public Organization toOrganization(AuthorInput input, String id) {
        AuthorInput author = input == null ? DEFAULT_AUTHOR : input;
        Organization organization = MapperSupport.prepare(new Organization(), id, NzFhir.PROFILE_ORGANIZATION);
        organization.setActive(true);
        organization.setName(author.organisationName());
        if (author.city() != null) {
            organization.addAddress(new Address().setCity(author.city()).setCountry("NZ"));
        }
        organization.setText(Narratives.paragraph(author.organisationName()));
        return organization;
    }
}
