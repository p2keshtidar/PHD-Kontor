package de.frauas.girokonto.contact;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.frauas.girokonto.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Adressbuch /F220/. */
class ContactFlowTest extends IntegrationTestBase {

    @Test
    void kontaktAnlegenAuflistenLoeschen() throws Exception {
        TestUser a = registerUser("Anna");
        TestUser b = registerUser("Ben");

        String body = mvc.perform(post("/api/contacts").header("Authorization", a.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"" + b.email() + "\",\"nickname\":\"Benni\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Benni"))
                .andExpect(jsonPath("$.iban").value(b.iban()))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();

        // doppelt -> 409
        mvc.perform(post("/api/contacts").header("Authorization", a.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"" + b.iban() + "\"}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/contacts").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$.length()").value(1));

        // Ben darf Annas Kontakt nicht löschen
        mvc.perform(delete("/api/contacts/" + id).header("Authorization", b.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/contacts/" + id).header("Authorization", a.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/contacts").header("Authorization", a.bearer()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void telefonnummernAbgleichen() throws Exception {
        TestUser a = registerUser("Anna");
        TestUser b = registerUser("Ben");
        String international = "+49 " + b.phone().substring(1, 4) + "-" + b.phone().substring(4);

        mvc.perform(post("/api/contacts/match").header("Authorization", a.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phones\":[\"" + international + "\",\"" + a.phone() + "\",\"0000\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fullName").value("Ben Muster"))
                .andExpect(jsonPath("$[0].alreadyContact").value(false));
    }
}
