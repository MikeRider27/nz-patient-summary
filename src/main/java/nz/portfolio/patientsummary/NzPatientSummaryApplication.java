package nz.portfolio.patientsummary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NzPatientSummaryApplication {

    public static void main(String[] args) {
        SpringApplication.run(NzPatientSummaryApplication.class, args);
    }
}
