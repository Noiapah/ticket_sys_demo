package no.telefonhjelp.web;

import no.telefonhjelp.PhoneSupportApplication;
import no.telefonhjelp.service.MaintenanceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {
    private final MaintenanceService service;
    public MaintenanceController(MaintenanceService service){this.service=service;}
    @PostMapping("/backup") Message backup(@RequestBody(required=false) FileRequest request) throws Exception { return new Message(service.backup(request == null ? null : request.path()), false); }
    @PostMapping("/restore") Message restore(@RequestBody FileRequest request) throws Exception {
        var message = service.prepareRestore(request.path());
        var restarting = PhoneSupportApplication.requestRestart();
        return new Message(message, restarting);
    }
    record FileRequest(String path) {}
    record Message(String message, boolean restarting) {}
}
