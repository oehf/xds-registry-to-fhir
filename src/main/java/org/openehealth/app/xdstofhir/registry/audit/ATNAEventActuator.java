package org.openehealth.app.xdstofhir.registry.audit;

import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id="atna")
@RequiredArgsConstructor
@ConditionalOnBean(RecentATNAEvents.class)
public class ATNAEventActuator {
    private final RecentATNAEvents atnaEvents;

    @ReadOperation(produces = "text/plain")
    public String getReleaseNotes() {
        return atnaEvents.getMessages().reversed().stream()
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
