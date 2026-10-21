package nz.portfolio.patientsummary.controller;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ca.uhn.fhir.context.FhirContext;
import nz.portfolio.patientsummary.mapper.InvalidInputException;

/** Returns all client errors as FHIR OperationOutcomes, the way a FHIR server would. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private final FhirContext fhirContext;

    public ApiExceptionHandler(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<String> invalidRequest(MethodArgumentNotValidException e) {
        OperationOutcome outcome = new OperationOutcome();
        e.getBindingResult().getFieldErrors().forEach(error ->
                addIssue(outcome, error.getField() + ": " + error.getDefaultMessage(), error.getField()));
        return badRequest(outcome);
    }

    @ExceptionHandler(InvalidInputException.class)
    ResponseEntity<String> invalidInput(InvalidInputException e) {
        OperationOutcome outcome = new OperationOutcome();
        addIssue(outcome, e.getField() + ": " + e.getMessage(), e.getField());
        return badRequest(outcome);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<String> unreadable(HttpMessageNotReadableException e) {
        OperationOutcome outcome = new OperationOutcome();
        addIssue(outcome, "Request body is not valid JSON for this endpoint: " + e.getMostSpecificCause().getMessage(), null);
        return badRequest(outcome);
    }

    private static void addIssue(OperationOutcome outcome, String message, String expression) {
        OperationOutcome.OperationOutcomeIssueComponent issue = outcome.addIssue()
                .setSeverity(IssueSeverity.ERROR)
                .setCode(IssueType.INVALID)
                .setDiagnostics(message);
        if (expression != null) {
            issue.addExpression(expression);
        }
    }

    private ResponseEntity<String> badRequest(OperationOutcome outcome) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.valueOf(FhirMediaTypes.FHIR_JSON))
                .body(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(outcome));
    }
}
