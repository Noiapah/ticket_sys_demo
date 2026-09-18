package no.telefonhjelp.web;

import no.telefonhjelp.domain.ApiModels.CustomerMatch;
import no.telefonhjelp.domain.ApiModels.Ticket;
import no.telefonhjelp.service.CustomerService;
import no.telefonhjelp.service.TicketService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService service;
    private final TicketService tickets;
    public CustomerController(CustomerService service, TicketService tickets) { this.service = service; this.tickets = tickets; }

    @GetMapping("/history")
    List<Ticket> history(@RequestParam String phone, @RequestParam(required = false) Long excludeTicketId,
                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return tickets.customerHistory(phone, excludeTicketId, page, size);
    }

    @GetMapping("/match")
    ResponseEntity<CustomerMatch> match(@RequestParam String phone) {
        return service.match(phone).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
