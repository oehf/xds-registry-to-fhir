package org.openehealth.app.xdstofhir.registry.audit;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import org.openehealth.ipf.commons.audit.AuditContext;
import org.openehealth.ipf.commons.audit.AuditMetadataProvider;
import org.openehealth.ipf.commons.audit.protocol.AuditTransmissionProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "ipf.atna.mock.enabled", havingValue = "true")
public class RecentATNAEvents implements AuditTransmissionProtocol {
    @Getter
    private final List<String> messages = new ArrayList<>();

    @Value("${ipf.atna.mock.recent:20}")
    private int recentAuditValues;

    @Override
    public void send(AuditContext auditContext, AuditMetadataProvider auditMetadataProvider, String auditMessage) {
        if (auditMessage != null) {
            messages.add(auditMessage);
        }
        if (messages.size()>recentAuditValues)
            messages.removeFirst();
    }

    @Override
    public void shutdown() {
      // no-op
    }

    @Override
    public String getTransportName() {
        return "recentEventsMock";
    }


}
