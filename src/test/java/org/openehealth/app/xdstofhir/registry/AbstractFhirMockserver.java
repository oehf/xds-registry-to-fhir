package org.openehealth.app.xdstofhir.registry;

import java.io.IOException;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.impl.GenericClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;

@ExtendWith(MockServerExtension.class)
public abstract class AbstractFhirMockserver {

    protected static final String EMPTY_BUNDLE_RESULT = "{\"resourceType\":\"Bundle\",\"type\":\"searchset\"}";
    protected GenericClient newRestfulGenericClient;

    protected MockServerClient mockServer;

    @BeforeEach
    void setupMockContext(MockServerClient mockServer) throws IOException {
        this.mockServer = mockServer;
        var ctx = FhirContext.forR4Cached();
        ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        newRestfulGenericClient = (GenericClient) ctx.newRestfulGenericClient("http://localhost:"+mockServer.getPort()+"/");
        initClassUnderTest();
    }

    @AfterEach
    void cleanUpMock() {
        mockServer.reset();
    }

    protected abstract void initClassUnderTest();
}
