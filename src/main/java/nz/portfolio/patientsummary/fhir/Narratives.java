package nz.portfolio.patientsummary.fhir;

import java.util.List;

import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Narrative.NarrativeStatus;

/** Builds minimal, safely escaped XHTML narratives (FHIR requires a narrative for each section). */
public final class Narratives {

    private Narratives() {
    }

    public static Narrative paragraph(String text) {
        return generated("<p>" + escape(text) + "</p>");
    }

    public static Narrative list(List<String> items) {
        StringBuilder html = new StringBuilder("<ul>");
        items.forEach(item -> html.append("<li>").append(escape(item)).append("</li>"));
        return generated(html.append("</ul>").toString());
    }

    private static Narrative generated(String innerHtml) {
        Narrative narrative = new Narrative();
        narrative.setStatus(NarrativeStatus.GENERATED);
        narrative.setDivAsString("<div xmlns=\"http://www.w3.org/1999/xhtml\">" + innerHtml + "</div>");
        return narrative;
    }

    static String escape(String text) {
        return text == null ? "" : text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
