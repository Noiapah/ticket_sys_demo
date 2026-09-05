package no.telefonhjelp.service;

import no.telefonhjelp.PhoneSupportApplication;
import no.telefonhjelp.domain.ApiModels.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = PhoneSupportApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TicketServiceIntegrationTest {
    @TempDir static Path data;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("phone.support.data-dir", data::toString); registry.add("spring.main.web-application-type", () -> "none"); }
    @Autowired EmployeeService employees;
    @Autowired TicketService tickets;

    @Test
    void createsAuditedTicketAndDoesNotEscalateOnReassignment() {
        var emma = employees.create("Emma"); var daniel = employees.create("Daniel");
        var created = tickets.create(new TicketDraft("Ola Hansen", "99 12 34 56", DeviceType.PHONE, "Apple", "iPhone 15 Pro", "iPhone 16 Pro", OperatingSystem.IOS, "Dataoverføring / sikkerhetskopi / oppsett", "Overfør innholdet.", emma.id()));
        assertThat(created.customerPhoneNormalized()).isEqualTo("+4799123456");
        assertThat(created.newDeviceModel()).isEqualTo("iPhone 16 Pro");
        assertThat(created.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(created.history()).extracting(HistoryEvent::eventType).containsExactly("CREATED", "STATUS");
        var assigned = tickets.assign(created.id(), daniel.id(), emma.id(), created.version());
        assertThat(assigned.assignedToName()).isEqualTo("Daniel");
        assertThat(assigned.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(assigned.history()).extracting(HistoryEvent::eventType).contains("ASSIGNED");
    }

    @Test
    void rejectsStaleAndClosedWrites() {
        var actor = employees.list().stream().findFirst().orElseGet(() -> employees.create("Sofie"));
        var created = tickets.create(new TicketDraft("Kari", "98044221", DeviceType.PHONE, "Samsung", "Samsung Galaxy S24", "", OperatingSystem.ANDROID, "App-problemer", "Appen stopper.", actor.id()));
        var closed = tickets.status(created.id(), TicketStatus.CLOSED, actor.id(), created.version());
        assertThatThrownBy(() -> tickets.comment(closed.id(), "Skal ikke lagres", actor.id(), closed.version())).isInstanceOf(AppException.class).hasMessageContaining("Åpne saken igjen");
        var reopened = tickets.status(closed.id(), TicketStatus.IN_PROGRESS, actor.id(), closed.version());
        assertThatThrownBy(() -> tickets.urgent(reopened.id(), true, actor.id(), closed.version())).isInstanceOf(AppException.class).hasMessageContaining("endret");
    }
}
