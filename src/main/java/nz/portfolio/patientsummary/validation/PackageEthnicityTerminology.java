package nz.portfolio.patientsummary.validation;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeSystem.ConceptDefinitionComponent;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.support.IValidationSupport;
import nz.portfolio.patientsummary.fhir.NzFhir;
import nz.portfolio.patientsummary.mapper.EthnicityTerminology;

/**
 * Resolves ethnicity displays from the Stats NZ level 4 CodeSystem shipped inside the NZ Base
 * package, so generated codes and displays always match what the validator checks against.
 */
@Component
public class PackageEthnicityTerminology implements EthnicityTerminology {

    private final Map<String, String> displays;

    public PackageEthnicityTerminology(IValidationSupport validationSupport) {
        CodeSystem codeSystem = (CodeSystem) validationSupport.fetchCodeSystem(NzFhir.ETHNICITY_LEVEL4_SYSTEM);
        if (codeSystem == null) {
            throw new IllegalStateException("NZ ethnicity CodeSystem not found - is the NZ Base package loaded?");
        }
        this.displays = codeSystem.getConcept().stream()
                .collect(Collectors.toUnmodifiableMap(ConceptDefinitionComponent::getCode, ConceptDefinitionComponent::getDisplay));
    }

    @Override
    public Optional<String> display(String level4Code) {
        return Optional.ofNullable(displays.get(level4Code));
    }
}
