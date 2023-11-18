package org.openehealth.app.xdstofhir.registry;

import java.io.IOException;
import java.io.InputStream;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import ca.uhn.fhir.util.BundleBuilder;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.openehealth.ipf.commons.spring.map.config.CustomMappings;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class XdsSpringContext {

    @Value("classpath*:profiles/*.json")
    private Resource[] profiles;
    @Value("classpath:META-INF/map/fhir-hl7v2-translation.map")
    private Resource hl7v2fhirMapping;

    @Bean(name = Bus.DEFAULT_BUS_ID)
    public SpringBus springBus() {
        SpringBus springBus = new SpringBus();
        LoggingFeature logging = new LoggingFeature();
        logging.setLogBinary(true);
        logging.setLogMultipart(true);
        logging.setVerbose(true);
        springBus.getFeatures().add(logging);
        BusFactory.setDefaultBus(springBus);
        return springBus;
    }

    @Bean
    public IGenericClient fhirClient(@Value("${fhir.server.base}") String fhirServerBase) {
        var ctx = FhirContext.forR4Cached();
        ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        LoggingInterceptor loggingInterceptor = new LoggingInterceptor();
        loggingInterceptor.setLogRequestSummary(true);
        loggingInterceptor.setLogResponseBody(true);
        loggingInterceptor.setLogRequestBody(true);
        loggingInterceptor.setLogRequestHeaders(true);
        IGenericClient client = ctx.newRestfulGenericClient(fhirServerBase);
        client.registerInterceptor(loggingInterceptor);
        return client;
    }

    @Bean
    public CustomMappings customMapping() {
        CustomMappings mapping = new CustomMappings();
        mapping.setMappingResource(hl7v2fhirMapping);
        return mapping;
    }

    /**
     * Verify after startup that the FHIR Server contain the required MHD FHIR profile and create them if not present.
     *
     * @param fhirClient
     * @return
     */
    @ConditionalOnProperty(value = "fhir.server.profile.bootstrap", havingValue = "true", matchIfMissing = true)
    @Bean
    public SmartInitializingSingleton createProfilesIfNeeded(IGenericClient fhirClient) {
        return () -> {
            var builder = new BundleBuilder(fhirClient.getFhirContext());
            var fhirParser = fhirClient.getFhirContext().newJsonParser();
            for (var profile : profiles) {
                try (InputStream inputStream = profile.getInputStream()) {
                    builder.addTransactionUpdateEntry(fhirParser.parseResource(inputStream));
                } catch (IOException e) {
                    throw new IllegalStateException("MHD Profile definition shall be present");
                }
            }
            fhirClient.transaction().withBundle(builder.getBundle()).execute();
        };
    }
}
