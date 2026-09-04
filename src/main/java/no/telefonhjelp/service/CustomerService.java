package no.telefonhjelp.service;

import no.telefonhjelp.domain.ApiModels.CustomerMatch;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomerService {
    private final JdbcTemplate jdbc;

    public CustomerService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<CustomerMatch> match(String phone) {
        if (phone == null || phone.isBlank()) return Optional.empty();
        var normalized = PhoneNormalizer.normalize(phone);
        return jdbc.query("SELECT c.id,c.name,c.phone_normalized,COUNT(t.id) previous_tickets FROM customers c LEFT JOIN tickets t ON t.customer_id=c.id WHERE c.phone_normalized=? GROUP BY c.id,c.name,c.phone_normalized",
                (result, row) -> new CustomerMatch(result.getLong("id"), result.getString("name"), result.getString("phone_normalized"), result.getLong("previous_tickets")), normalized)
                .stream().findFirst();
    }
}
