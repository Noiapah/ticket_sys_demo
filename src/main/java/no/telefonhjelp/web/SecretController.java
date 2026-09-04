package no.telefonhjelp.web;

import no.telefonhjelp.domain.ApiModels.TemporaryCredential;
import no.telefonhjelp.domain.ApiModels.TemporaryValues;
import no.telefonhjelp.service.SecretService;
import no.telefonhjelp.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets/{ticketId}/temporary-info")
public class SecretController {
    private final SecretService secrets;
    private final TicketService tickets;
    public SecretController(SecretService secrets, TicketService tickets) { this.secrets = secrets; this.tickets = tickets; }

    @GetMapping List<TemporaryCredential> list(@PathVariable long ticketId) throws Exception { tickets.get(ticketId); return secrets.list(ticketId); }
    @PutMapping List<TemporaryCredential> replace(@PathVariable long ticketId, @RequestBody TemporaryValues body) throws Exception { tickets.get(ticketId); return secrets.replace(ticketId, body.values()); }
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT) void clear(@PathVariable long ticketId) throws Exception { secrets.clear(ticketId, null); }
    @DeleteMapping("/{key}") @ResponseStatus(HttpStatus.NO_CONTENT) void clearOne(@PathVariable long ticketId, @PathVariable String key) throws Exception { secrets.clear(ticketId, key); }
}

