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
        var closed = tickets.status(created.id(), TicketStatus.CLOSED, actor.id(), created.version(), "Oppdaterte appen.");
        assertThatThrownBy(() -> tickets.comment(closed.id(), "Skal ikke lagres", actor.id(), closed.version())).isInstanceOf(AppException.class).hasMessageContaining("Åpne saken igjen");
        var reopened = tickets.status(closed.id(), TicketStatus.IN_PROGRESS, actor.id(), closed.version(), null);
        assertThatThrownBy(() -> tickets.urgent(reopened.id(), true, actor.id(), closed.version())).isInstanceOf(AppException.class).hasMessageContaining("endret");
    }

    @Test
    void requiresResolutionAndPreservesEachClosureAcrossReopening() {
        var actor = employees.create("Resolution tester");
        var created = tickets.create(new TicketDraft("Resolution customer", "98044222", DeviceType.PHONE, "Apple", "iPhone", "", OperatingSystem.IOS, "E-post", "Ingen e-post.", actor.id()));
        for (var note : new String[]{null, "", "   ", "a".repeat(4001)}) {
            assertThatThrownBy(() -> tickets.status(created.id(), TicketStatus.CLOSED, actor.id(), created.version(), note)).isInstanceOf(AppException.class);
        }
        assertThat(tickets.get(created.id())).isEqualTo(created);
        var closed = tickets.status(created.id(), TicketStatus.CLOSED, actor.id(), created.version(), "  Oppdaterte kontoen.\nTestet e-post.  ");
        assertThat(closed.resolutionNote()).isEqualTo("Oppdaterte kontoen.\nTestet e-post.");
        assertThat(closed.closedAt()).isNotNull();
        assertThat(closed.version()).isEqualTo(created.version() + 1);
        var reopened = tickets.status(closed.id(), TicketStatus.IN_PROGRESS, actor.id(), closed.version(), null);
        assertThat(reopened.resolutionNote()).isEqualTo(closed.resolutionNote());
        assertThat(reopened.closedAt()).isNull();
        assertThatThrownBy(() -> tickets.status(reopened.id(), TicketStatus.CLOSED, actor.id(), closed.version(), "Stale note")).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> tickets.status(reopened.id(), TicketStatus.CLOSED, actor.id(), reopened.version(), null)).isInstanceOf(AppException.class);
        var closedAgain = tickets.status(reopened.id(), TicketStatus.CLOSED, actor.id(), reopened.version(), "Byttet innstillinger.");
        assertThat(tickets.get(closedAgain.id()).resolutionNote()).isEqualTo("Byttet innstillinger.");
        assertThat(closedAgain.history().stream().filter(event -> event.eventType().equals("RESOLUTION")))
                .extracting(HistoryEvent::summary).containsExactly(closed.resolutionNote(), closedAgain.resolutionNote());
    }

    @Test
    void customerHistoryMatchesExactNormalizedPhoneAndPagesNewestFirst() {
        var actor = employees.create("History tester");
        var first = tickets.create(new TicketDraft("Same name", "98044223", DeviceType.PHONE, "Apple", "iPhone", "", OperatingSystem.IOS, "E-post", "Første problem.", actor.id()));
        tickets.status(first.id(), TicketStatus.CLOSED, actor.id(), first.version(), "Løste problemet.");
        var second = tickets.create(new TicketDraft("Same name", "+47 980 44 223", DeviceType.TABLET, "Apple", "iPad", "", OperatingSystem.IOS, "E-post", "Andre problem.", actor.id()));
        tickets.create(new TicketDraft("Same name", "98044224", DeviceType.PHONE, "Apple", "iPhone", "", OperatingSystem.IOS, "E-post", "98044223", actor.id()));
        assertThat(tickets.customerHistory("0047 980 44 223", null, 0, 1)).extracting(Ticket::id).containsExactly(second.id());
        var older = tickets.customerHistory("98044223", null, 1, 1);
        assertThat(older).extracting(Ticket::id).containsExactly(first.id());
        assertThat(older.getFirst().resolutionNote()).isEqualTo("Løste problemet.");
        assertThat(older.getFirst().comments()).isEmpty();
        assertThat(older.getFirst().history()).isEmpty();
        assertThat(tickets.customerHistory("98044223", second.id(), 0, 10)).extracting(Ticket::id).containsExactly(first.id());
        assertThat(tickets.customerHistory("980442", null, 0, 10)).isEmpty();
        assertThat(tickets.customerHistory("98044223", null, 2, 1)).isEmpty();
        assertThatThrownBy(() -> tickets.customerHistory("98044223", null, -1, 10)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> tickets.customerHistory("98044223", null, 0, 101)).isInstanceOf(AppException.class);
    }
}
