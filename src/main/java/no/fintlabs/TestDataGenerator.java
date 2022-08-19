package no.fintlabs;

import lombok.extern.slf4j.Slf4j;
import no.fintlabs.model.Error;
import no.fintlabs.model.Event;
import no.fintlabs.model.EventType;
import no.fintlabs.repositories.EventRepository;
import no.fintlabs.model.InstanceFlowHeadersEmbeddable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
@ConditionalOnProperty(value = "generateTestData", havingValue = "true")
public class TestDataGenerator {

    public TestDataGenerator(EventRepository eventRepository) {
        eventRepository.saveAll(List.of(
                createEvent("incoming-instance", EventType.INFO, "1", null, null, null, null, null, 0),
                createEvent("new-instance", EventType.INFO, "1", "1", null, null, null, null, 0),
                createEvent("instance-to-case-mapping-error", EventType.ERROR, "1", "1", "1", null, null, null, 4),
                createEvent("reemitted-instance", EventType.INFO, "2", "1", null, null, null, null, 0),
                createEvent("new-case", EventType.INFO, "2", "1", "2", "1", null, null, 0),
                createEvent("dispatch-case", EventType.INFO, "2", "1", "2", "1", "1", null, 0),
                createEvent("case-dispatched", EventType.INFO, "2", "1", "2", "1", "1", "1", 0)
        ));
    }

    private Event createEvent(String name, EventType type, String correlationId, String instanceId, String configurationId, String caseId, String dispatchId, String archiveCaseFolderId, int numOfErrors) {
        Event event = new Event();
        event.setInstanceFlowHeaders(
                InstanceFlowHeadersEmbeddable
                        .builder()
                        .orgId("orgId")
                        .service("service")
                        .sourceApplication("sourceApplication")
                        .sourceApplicationIntegrationId("sourceApplicationIntegrationId")
                        .sourceApplicationInstanceId("sourceApplicationInstanceId")
                        .correlationId(correlationId)
                        .instanceId(instanceId)
                        .configurationId(configurationId)
                        .caseId(caseId)
                        .dispatchId(dispatchId)
                        .archiveCaseFolderId(archiveCaseFolderId)
                        .build()
        );
        event.setName(name);
        event.setType(type);
        event.setTimestamp(LocalDateTime.now());


        List<Error> errors = IntStream.range(0, numOfErrors)
                .mapToObj(this::createError)
                .collect(Collectors.toList());
        event.setErrors(errors);

        return event;
    }

    private Error createError(int num) {
        Error error = new Error();
        error.setErrorCode("error" + num + "Code");
        error.setArgs(Map.of(
                "arg0", "arg0value",
                "arg1", "arg1Value"
        ));
        return error;
    }
}
