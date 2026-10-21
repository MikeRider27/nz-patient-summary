package nz.portfolio.patientsummary.synthetic;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import nz.portfolio.patientsummary.dto.PatientSummaryRequest;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest.AddressInput;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest.AllergyInput;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest.AuthorInput;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest.ConditionInput;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest.ImmunizationInput;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest.MedicationInput;
import nz.portfolio.patientsummary.dto.PatientSummaryRequest.PatientInput;

/**
 * Produces realistic but entirely fictitious New Zealand patients. The same seed always yields the
 * same patient. No real patient data is used: names, addresses and NHIs are randomly combined, NHIs
 * start with "Z" and every resource is tagged HTEST (test health data).
 */
@Component
public class SyntheticPatientFactory {

    /** Reference date for ages and clinical dates, fixed so output is reproducible. */
    static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 1, 1);

    private record Ethnicity(String code, List<String> femaleNames, List<String> maleNames, List<String> familyNames) {
    }

    private record Place(String suburb, String city, String postcode) {
    }

    private record Problem(ConditionInput condition, MedicationInput medication) {
    }

    // Stats NZ level 4 ethnicity codes (validated against the NZ Base CodeSystem at runtime).
    private static final List<Ethnicity> ETHNICITIES = List.of(
            new Ethnicity("11111", List.of("Olivia", "Charlotte", "Isla", "Sophie", "Emily"),
                    List.of("Jack", "Oliver", "James", "Liam", "Thomas"),
                    List.of("Smith", "Wilson", "Brown", "Taylor", "Anderson", "Thompson")),
            new Ethnicity("21111", List.of("Aroha", "Mere", "Anahera", "Kahurangi", "Hine"),
                    List.of("Wiremu", "Tama", "Hemi", "Nikau", "Rawiri"),
                    List.of("Ngata", "Parata", "Te Huia", "Tipene", "Rangi")),
            new Ethnicity("31111", List.of("Losa", "Malia", "Mele", "Sina"),
                    List.of("Sione", "Tavita", "Ioane", "Pita"),
                    List.of("Leota", "Aumua", "Fale", "Tanuvasa")),
            new Ethnicity("33111", List.of("Ana", "Mele", "Salote", "Lesieli"),
                    List.of("Viliami", "Sione", "Tevita", "Siosaia"),
                    List.of("Taufa", "Fifita", "Vaipulu", "Lolohea")),
            new Ethnicity("42100", List.of("Mei", "Lina", "Xiaoling", "Yan"),
                    List.of("Wei", "Jun", "Hao", "Ming"),
                    List.of("Chen", "Wang", "Li", "Zhang", "Liu")),
            new Ethnicity("43100", List.of("Priya", "Anjali", "Divya", "Kavya"),
                    List.of("Arjun", "Rohan", "Sanjay", "Vikram"),
                    List.of("Patel", "Singh", "Sharma", "Kumar")));

    private static final List<Place> PLACES = List.of(
            new Place("Ponsonby", "Auckland", "1011"),
            new Place("Otahuhu", "Auckland", "1062"),
            new Place("Te Aro", "Wellington", "6011"),
            new Place("Riccarton", "Christchurch", "8011"),
            new Place("Hamilton East", "Hamilton", "3216"),
            new Place("Mount Maunganui", "Tauranga", "3116"),
            new Place("Glenholme", "Rotorua", "3010"),
            new Place("North Dunedin", "Dunedin", "9016"));

    private static final List<String> STREETS = List.of(
            "Kowhai Street", "Rimu Road", "Totara Avenue", "Pohutukawa Drive", "Harakeke Lane", "Nikau Place");

    // SNOMED CT problems, each paired with a typical first-line medicine (SNOMED CT substance).
    private static final List<Problem> PROBLEMS = List.of(
            new Problem(new ConditionInput("44054006", "Diabetes mellitus type 2", "active", null),
                    new MedicationInput("372567009", "Metformin", null, "500 mg orally twice daily with meals", null, "active")),
            new Problem(new ConditionInput("38341003", "Hypertensive disorder, systemic arterial", "active", null),
                    new MedicationInput("386864001", "Amlodipine", null, "5 mg orally once daily", null, "active")),
            new Problem(new ConditionInput("195967001", "Asthma", "active", null),
                    new MedicationInput("372897005", "Salbutamol", null, "100 microgram inhaled as required", null, "active")),
            new Problem(new ConditionInput("90560007", "Gout", "active", null),
                    new MedicationInput("387135004", "Allopurinol", null, "100 mg orally once daily", null, "active")),
            new Problem(new ConditionInput("414545008", "Ischemic heart disease", "active", null),
                    new MedicationInput("373444002", "Atorvastatin", null, "40 mg orally once daily at night", null, "active")),
            new Problem(new ConditionInput("49436004", "Atrial fibrillation", "active", null),
                    new MedicationInput("372756006", "Warfarin", null, "Orally once daily, dose adjusted to INR", null, "active")));

    private static final List<AllergyInput> ALLERGIES = List.of(
            new AllergyInput("764146007", "Penicillin", "medication", "high", "39579001", "Anaphylaxis"),
            new AllergyInput("762952008", "Peanut", "food", "high", "126485001", "Urticaria"),
            new AllergyInput("387406002", "Sulfonamide", "medication", "low", "271807003", "Eruption of skin"),
            new AllergyInput("288328004", "Bee venom", "environment", "high", "39579001", "Anaphylaxis"));

    private static final List<ImmunizationInput> VACCINES = List.of(
            new ImmunizationInput("1181000221105", "Influenza virus antigen only vaccine product", LocalDate.of(2025, 4, 14), null),
            new ImmunizationInput("1119349007", "SARS-CoV-2 mRNA vaccine", LocalDate.of(2023, 3, 2), null),
            new ImmunizationInput("871831003", "Measles and mumps and rubella vaccine", LocalDate.of(1995, 8, 21), null));

    private static final List<String> PRACTICES = List.of(
            "Kowhai Synthetic Medical Centre", "Harbourside Synthetic Health", "Te Puna Synthetic Hauora");

    public PatientSummaryRequest create(long seed, Nhi.Format nhiFormat) {
        RandomGenerator random = new SplittableRandom(seed);

        Ethnicity ethnicity = pick(random, ETHNICITIES);
        boolean female = random.nextBoolean();
        Set<String> ethnicityCodes = new LinkedHashSet<>();
        ethnicityCodes.add(ethnicity.code());
        if (random.nextInt(4) == 0) {
            ethnicityCodes.add(pick(random, ETHNICITIES).code()); // multiple ethnicities are common in NZ
        }

        Place place = pick(random, PLACES);
        LocalDate birthDate = REFERENCE_DATE.minusYears(18 + random.nextInt(70)).minusDays(random.nextInt(365));
        PatientInput patient = new PatientInput(
                Nhi.generate(nhiFormat, random),
                pick(random, ethnicity.familyNames()),
                List.of(pick(random, female ? ethnicity.femaleNames() : ethnicity.maleNames())),
                female ? "female" : "male",
                birthDate,
                List.copyOf(ethnicityCodes),
                new AddressInput(List.of((1 + random.nextInt(250)) + " " + pick(random, STREETS)),
                        place.suburb(), place.city(), place.postcode()),
                null,
                null);

        List<ConditionInput> conditions = new ArrayList<>();
        List<MedicationInput> medications = new ArrayList<>();
        for (Problem problem : sample(random, PROBLEMS, 1 + random.nextInt(3))) {
            LocalDate onset = birthDate.plusYears(18).plusDays(random.nextInt(
                    (int) Math.max(1, REFERENCE_DATE.toEpochDay() - birthDate.plusYears(18).toEpochDay())));
            ConditionInput c = problem.condition();
            MedicationInput m = problem.medication();
            conditions.add(new ConditionInput(c.snomedCode(), c.display(), c.clinicalStatus(), onset));
            medications.add(new MedicationInput(m.snomedCode(), m.display(), m.nzmtCode(), m.dosageText(), onset, m.status()));
        }

        List<AllergyInput> allergies = sample(random, ALLERGIES, random.nextInt(3));
        List<ImmunizationInput> immunizations = sample(random, VACCINES, random.nextInt(VACCINES.size() + 1));

        AuthorInput author = new AuthorInput(pick(random, PRACTICES), place.city());
        return new PatientSummaryRequest(patient, author, conditions, medications, allergies, immunizations);
    }

    /** A reproducible id supplier for a seed, so a seed maps to a byte-identical document. */
    public static Supplier<String> idsFor(long seed) {
        SplittableRandom random = new SplittableRandom(seed ^ 0x5DEECE66DL);
        return () -> {
            long msb = (random.nextLong() & 0xffffffffffff0fffL) | 0x0000000000004000L; // version 4
            long lsb = (random.nextLong() & 0x3fffffffffffffffL) | 0x8000000000000000L; // IETF variant
            return new UUID(msb, lsb).toString();
        };
    }

    private static <T> T pick(RandomGenerator random, List<T> values) {
        return values.get(random.nextInt(values.size()));
    }

    private static <T> List<T> sample(RandomGenerator random, List<T> values, int count) {
        List<T> pool = new ArrayList<>(values);
        List<T> result = new ArrayList<>();
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            result.add(pool.remove(random.nextInt(pool.size())));
        }
        return result;
    }
}
