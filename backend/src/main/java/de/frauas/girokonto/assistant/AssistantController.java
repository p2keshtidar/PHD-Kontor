package de.frauas.girokonto.assistant;

import de.frauas.girokonto.assistant.AssistantService.Answer;
import de.frauas.girokonto.assistant.AssistantService.Report;
import de.frauas.girokonto.assistant.AssistantService.Scenario;
import de.frauas.girokonto.assistant.AssistantService.ScenarioRequest;
import de.frauas.girokonto.auth.AuthUser;
import de.frauas.girokonto.common.ApiException;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Persönlicher Finanzassistent (regelbasiert, nur App-Daten). */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    public record Question(String question) {}

    private final AssistantService service;
    private final Clock clock;

    public AssistantController(AssistantService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/questions")
    public List<AssistantService.Suggestion> questions(@AuthenticationPrincipal AuthUser auth) {
        return service.questions(auth.id());
    }

    @PostMapping("/ask")
    public Answer ask(@AuthenticationPrincipal AuthUser auth, @RequestBody Question q) {
        return service.ask(auth.id(), q.question());
    }

    @GetMapping("/report")
    public Report report(@AuthenticationPrincipal AuthUser auth, @RequestParam(required = false) String month) {
        YearMonth ym;
        try {
            ym = month == null || month.isBlank() ? YearMonth.now(clock).minusMonths(1) : YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "Monat bitte als JJJJ-MM angeben.");
        }
        return service.report(auth.id(), ym);
    }

    @PostMapping("/scenario")
    public Scenario scenario(@AuthenticationPrincipal AuthUser auth, @RequestBody ScenarioRequest req) {
        return service.scenario(auth.id(), req);
    }
}
