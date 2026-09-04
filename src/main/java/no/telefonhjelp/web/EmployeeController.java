package no.telefonhjelp.web;

import no.telefonhjelp.domain.ApiModels.Bootstrap;
import no.telefonhjelp.domain.ApiModels.Employee;
import no.telefonhjelp.service.EmployeeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class EmployeeController {
    private final EmployeeService service;
    public EmployeeController(EmployeeService service) { this.service = service; }

    @GetMapping("/bootstrap") Bootstrap bootstrap() { return new Bootstrap(service.list(), service.currentEmployeeId()); }
    @GetMapping("/employees") List<Employee> list() { return service.list(); }
    @PostMapping("/employees") @ResponseStatus(HttpStatus.CREATED) Employee create(@RequestBody NameRequest body) { return service.create(body.name()); }
    @PatchMapping("/employees/{id}") Employee update(@PathVariable long id, @RequestBody EmployeePatch body) { return service.update(id, body.name(), body.active()); }
    @PutMapping("/settings/current-employee") @ResponseStatus(HttpStatus.NO_CONTENT) void current(@RequestBody CurrentEmployee body) { service.setCurrentEmployee(body.employeeId()); }

    record NameRequest(String name) {}
    record EmployeePatch(String name, Boolean active) {}
    record CurrentEmployee(long employeeId) {}
}

