package no.telefonhjelp.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class InputLimits {
    private InputLimits() {}
    public static void text(String value, int maximum) {
        if (value != null && value.length() > maximum) throw AppException.badRequest("Et felt er for langt. Maksimalt " + maximum + " tegn er tillatt.");
    }
    public static void dates(LocalDate from, LocalDate to, boolean report) {
        if (report && (from == null || to == null)) throw AppException.badRequest("Velg fra- og til-dato.");
        for (var date : new LocalDate[]{from, to}) {
            if (date != null && (date.getYear() < 2000 || date.getYear() > 2100)) throw AppException.badRequest("Datoen må være mellom 2000 og 2100.");
        }
        if (from != null && to != null && (to.isBefore(from) || (report && ChronoUnit.DAYS.between(from, to) > 365))) throw AppException.badRequest("Velg en gyldig periode på maksimalt 366 dager.");
    }
}
