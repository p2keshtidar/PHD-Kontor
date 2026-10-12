package de.frauas.girokonto.contact;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Telefonnummern aus dem Gerät, die mit App-Nutzern abgeglichen werden. Sie werden nicht gespeichert (/NF080/). */
public record MatchRequest(@NotNull @Size(max = 1000, message = "Zu viele Telefonnummern.") List<String> phones) {
}
