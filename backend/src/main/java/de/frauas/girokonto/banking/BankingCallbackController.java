package de.frauas.girokonto.banking;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rücksprung nach der Freigabe bei der Bank. Die Seite ändert nichts – sie bittet nur, zur App
 * zurückzukehren; dort prüft "Freigabe abschließen" den Status beim Anbieter.
 */
@RestController
public class BankingCallbackController {

    static final String PAGE = """
            <!doctype html>
            <html lang="de"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Girokonto – Bankfreigabe</title>
            <style>body{font-family:system-ui,sans-serif;max-width:32rem;margin:3rem auto;padding:0 1rem;line-height:1.5}</style>
            </head><body>
            <h1>Fast geschafft</h1>
            <p>Die Bank hat dich zurückgeleitet. Kehre jetzt zur Girokonto-App zurück und tippe auf
            <b>„Freigabe abschließen“</b>.</p>
            <hr>
            <p lang="en">The bank has sent you back. Return to the Girokonto app and tap <b>“Complete authorisation”</b>.</p>
            </body></html>
            """;

    @GetMapping(value = "/api/banking/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String callback() {
        return PAGE;
    }
}
