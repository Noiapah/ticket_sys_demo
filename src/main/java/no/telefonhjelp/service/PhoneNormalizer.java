package no.telefonhjelp.service;

public final class PhoneNormalizer {
    private PhoneNormalizer() {}
    public static String normalize(String input) {
        if (input == null || input.isBlank()) throw AppException.badRequest("Telefonnummer er påkrevd.");
        var trimmed = input.trim();
        var digits = trimmed.replaceAll("\\D", "");
        if (digits.startsWith("0047") && digits.length() == 12) return "+" + digits.substring(2);
        if (trimmed.startsWith("+47") && digits.length() == 10) return "+" + digits;
        if (digits.length() == 8) return "+47" + digits;
        if (trimmed.startsWith("+") && digits.length() >= 7 && digits.length() <= 15) return "+" + digits;
        return digits;
    }
}

