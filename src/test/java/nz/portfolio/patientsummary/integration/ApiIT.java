package nz.portfolio.patientsummary.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import ca.uhn.fhir.context.FhirContext;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIT {

    private static final String REQUEST = """
            {
              "patient": {
                "nhi": "ZAA0067",
                "familyName": "Ngata",
                "givenNames": ["Aroha"],
                "gender": "female",
                "birthDate": "1984-03-14",
                "ethnicityCodes": ["21111", "11111"],
                "address": {"lines": ["12 Kowhai Street"], "suburb": "Ponsonby", "city": "Auckland", "postalCode": "1011"}
              },
              "author": {"organisationName": "Kowhai Synthetic Medical Centre", "city": "Auckland"},
              "conditions": [{"snomedCode": "44054006", "display": "Diabetes mellitus type 2", "onsetDate": "2019-06-01"}],
              "medications": [{"snomedCode": "372567009", "display": "Metformin", "dosageText": "500 mg twice daily", "startDate": "2019-06-01"}],
              "allergies": [{"snomedCode": "764146007", "display": "Penicillin", "category": "medication", "criticality": "high",
                             "reactionSnomedCode": "39579001", "reactionDisplay": "Anaphylaxis"}],
              "immunizations": [{"snomedCode": "1181000221105", "display": "Influenza virus antigen only vaccine product", "occurrenceDate": "2025-04-14"}]
            }
            """;

    @Autowired MockMvc mvc;
    @Autowired FhirContext fhirContext;

    @Test
    void generateReturnsValidDocumentWithCompositionFirst() throws Exception {
        MvcResult result = mvc.perform(post("/summary/generate").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Validation-Errors", "0"))
                .andReturn();

        Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, result.getResponse().getContentAsString());
        assertThat(bundle.getType()).isEqualTo(Bundle.BundleType.DOCUMENT);
        assertThat(bundle.getEntryFirstRep().getResource()).isInstanceOf(Composition.class);
        assertThat(bundle.getEntry()).hasSize(7); // composition, patient, organization + 4 clinical
    }

    @Test
    void generateRejectsNhiWithWrongCheckDigit() throws Exception {
        mvc.perform(post("/summary/generate").contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST.replace("ZAA0067", "ZAA0068")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"))
                .andExpect(jsonPath("$.issue[0].expression[0]").value("patient.nhi"));
    }

    @Test
    void generateRejectsUnknownEthnicityCode() throws Exception {
        mvc.perform(post("/summary/generate").contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST.replace("\"21111\"", "\"99998\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.issue[0].expression[0]").value("patient.ethnicityCodes"));
    }

    @Test
    void generateRejectsMissingMandatoryFields() throws Exception {
        mvc.perform(post("/summary/generate").contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST.replace("\"birthDate\": \"1984-03-14\",", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.issue[0].expression[0]").value("patient.birthDate"));
    }

    @Test
    void validateAcceptsCommittedExample() throws Exception {
        mvc.perform(post("/summary/validate").contentType("application/fhir+json")
                        .content(Files.readString(Path.of("examples/nzps-example-1.json"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Validation-Errors", "0"))
                .andExpect(jsonPath("$.resourceType").value("OperationOutcome"));
    }

    /** Negative control: proves the validator actually enforces the NZPS/IPS rules. */
    @Test
    void validateReportsErrorsForNonConformingBundle() throws Exception {
        String collection = """
                {"resourceType": "Bundle", "type": "collection",
                 "entry": [{"fullUrl": "urn:uuid:3f1c1f7e-8e4b-4c55-9d43-2d0f9a7c1b11",
                            "resource": {"resourceType": "Patient", "gender": "female"}}]}
                """;
        MvcResult result = mvc.perform(post("/summary/validate").contentType("application/fhir+json").content(collection))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(Integer.parseInt(result.getResponse().getHeader("X-Validation-Errors"))).isPositive();
        assertThat(result.getResponse().getContentAsString())
                .contains("Composition")  // missing mandatory Composition entry
                .contains("document");    // wrong Bundle.type
    }

    @Test
    void bundleViewerIsServed() throws Exception {
        String page = mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Bundle Viewer").contains("summary/validate");
    }

    @Test
    void syntheticPatientIsReproducible() throws Exception {
        String first = mvc.perform(get("/synthetic/patient").param("seed", "42")).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(get("/synthetic/patient").param("seed", "42")).andReturn().getResponse().getContentAsString();
        assertThat(first).isEqualTo(second).contains("\"nhi\":\"Z");
    }

    @Test
    void syntheticSummaryIsValid() throws Exception {
        mvc.perform(get("/synthetic/summary").param("seed", "7").param("nhiFormat", "EXPANDED"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Validation-Errors", "0"))
                .andExpect(header().string("X-Synthetic-Seed", "7"));
    }
}
