package no.telefonhjelp.web;

import no.telefonhjelp.domain.ApiModels.CustomerMatch;
import no.telefonhjelp.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService service;
    public CustomerController(CustomerService service) { this.service = service; }

    @GetMapping("/match")
    ResponseEntity<CustomerMatch> match(@RequestParam String phone) {
        return service.match(phone).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
