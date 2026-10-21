package nz.portfolio.patientsummary.mapper;

import java.util.Optional;

/** Looks up displays for Stats NZ level 4 ethnicity codes. */
@FunctionalInterface
public interface EthnicityTerminology {

    Optional<String> display(String level4Code);
}
