package nz.portfolio.patientsummary.controller;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import nz.portfolio.patientsummary.dto.PatientSummaryRequest;
import nz.portfolio.patientsummary.service.PatientSummaryService;
import nz.portfolio.patientsummary.service.PatientSummaryService.GeneratedSummary;
import nz.portfolio.patientsummary.synthetic.Nhi;
import nz.portfolio.patientsummary.synthetic.SyntheticPatientFactory;

/** Synthetic, clearly fake NZ test patients - for demos, testing and onboarding. */
@RestController
@RequestMapping("/synthetic")
public class SyntheticController {

    private final SyntheticPatientFactory factory;
    private final PatientSummaryService summaryService;

    public SyntheticController(SyntheticPatientFactory factory, PatientSummaryService summaryService) {
        this.factory = factory;
        this.summaryService = summaryService;
    }

    /** JSON input for a synthetic patient - ready to POST to /summary/generate. */
    @GetMapping("/patient")
    public PatientSummaryRequest patient(@RequestParam(required = false) Long seed,
                                         @RequestParam(defaultValue = "LEGACY") Nhi.Format nhiFormat) {
        return factory.create(seedOrRandom(seed), nhiFormat);
    }

    /** A complete, validated NZPS Bundle for a synthetic patient. */
    @GetMapping(path = "/summary", produces = FhirMediaTypes.FHIR_JSON)
    public ResponseEntity<String> summary(@RequestParam(required = false) Long seed,
                                          @RequestParam(defaultValue = "LEGACY") Nhi.Format nhiFormat) {
        long effectiveSeed = seedOrRandom(seed);
        GeneratedSummary summary = summaryService.generate(
                factory.create(effectiveSeed, nhiFormat), true, SyntheticPatientFactory.idsFor(effectiveSeed));
        return SummaryController.withValidationHeaders(summary)
                .header("X-Synthetic-Seed", String.valueOf(effectiveSeed))
                .body(summary.json());
    }

    private static long seedOrRandom(Long seed) {
        return seed != null ? seed : ThreadLocalRandom.current().nextLong(1, 1_000_000);
    }
}
