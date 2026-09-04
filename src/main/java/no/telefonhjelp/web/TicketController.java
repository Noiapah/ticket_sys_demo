package no.telefonhjelp.web;

import no.telefonhjelp.domain.ApiModels.*;
import no.telefonhjelp.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {
    private final TicketService service;
    public TicketController(TicketService service) { this.service = service; }

    @GetMapping List<Ticket> list(@RequestParam(defaultValue = "all") String scope, @RequestParam(required = false) String query, @RequestParam(required = false) Long employeeId, @RequestParam(required = false) String category, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) { return service.list(scope, query, employeeId, category, from, to); }
    @GetMapping("/{id}") Ticket get(@PathVariable long id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) Ticket create(@RequestBody TicketDraft draft) { return service.create(draft); }
    @PatchMapping("/{id}") Ticket update(@PathVariable long id, @RequestBody TicketPatch patch) { return service.update(id, patch); }
    @PostMapping("/{id}/comments") Ticket comment(@PathVariable long id, @RequestBody CommentRequest body) { return service.comment(id, body.text(), body.actorId(), body.version()); }
    @PostMapping("/{id}/assignment") Ticket assign(@PathVariable long id, @RequestBody AssignmentRequest body) { return service.assign(id, body.employeeId(), body.actorId(), body.version()); }
    @PostMapping("/{id}/status") Ticket status(@PathVariable long id, @RequestBody StatusRequest body) { return service.status(id, body.status(), body.actorId(), body.version()); }
    @PostMapping("/{id}/urgent") Ticket urgent(@PathVariable long id, @RequestBody UrgentRequest body) { return service.urgent(id, body.urgent(), body.actorId(), body.version()); }

    record CommentRequest(String text, long actorId, long version) {}
    record AssignmentRequest(long employeeId, long actorId, long version) {}
    record StatusRequest(TicketStatus status, long actorId, long version) {}
    record UrgentRequest(boolean urgent, long actorId, long version) {}
}
