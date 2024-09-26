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
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.app.xdstofhir.registry.common.RegistryConfiguration;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdFolder;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdSubmissionSet;
import org.openehealth.ipf.commons.spring.map.config.CustomMappings;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import static org.openehealth.app.xdstofhir.registry.common.Wss4jConfigurator.createWss4jInterceptor;
import static org.openehealth.app.xdstofhir.registry.common.Wss4jConfigurator.createWss4jProperties;

@Configuration
public class XdsSpringContext {

    @Value("classpath*:profiles/*.json")
    private Resource[] profiles;
    @Value("classpath:META-INF/map/fhir-hl7v2-translation.map")
    private Resource hl7v2fhirMapping;

    @Bean(name = Bus.DEFAULT_BUS_ID)
    SpringBus springBus() {
        var springBus = new SpringBus();
        var logging = new LoggingFeature();
        logging.setLogBinary(true);
        logging.setLogMultipart(true);
        logging.setVerbose(true);
        springBus.getFeatures().add(logging);
        BusFactory.setDefaultBus(springBus);
        return springBus;
    }

    @Bean
    IGenericClient fhirClient(@Value("${fhir.server.base}") String fhirServerBase) {
        var ctx = FhirContext.forR4Cached();
        ctx.setDefaultTypeForProfile(MappingSupport.MHD_COMPREHENSIVE_FOLDER_PROFILE, MhdFolder.class);
        ctx.setDefaultTypeForProfile(MappingSupport.MHD_COMPREHENSIVE_SUBMISSIONSET_PROFILE, MhdSubmissionSet.class);
        ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        var loggingInterceptor = new LoggingInterceptor();
        loggingInterceptor.setLogRequestSummary(true);
        loggingInterceptor.setLogResponseBody(true);
        loggingInterceptor.setLogRequestBody(true);
        loggingInterceptor.setLogRequestHeaders(true);
        var client = ctx.newRestfulGenericClient(fhirServerBase);
        client.registerInterceptor(loggingInterceptor);
        return client;
    }

    @Bean
    CustomMappings customMapping() {
        var mapping = new CustomMappings();
        mapping.setMappingResource(hl7v2fhirMapping);
        return mapping;
    }
    
    @ConditionalOnProperty(value = "xds.xua.enabled", havingValue = "true")
    @Bean
    SmartInitializingSingleton applyXuaConfiguration(SpringBus springBus, RegistryConfiguration registryConfiguration) {
    	return () -> {
	        springBus.setProperties(createWss4jProperties(registryConfiguration.getXua()));
	        springBus.getInInterceptors().add(createWss4jInterceptor(registryConfiguration));
    	};
    }
    

    /**
     * Verify after startup that the FHIR Server contain the required MHD FHIR profile and create them if not present.
     *
     * @param fhirClient
     * @return
     */
    @ConditionalOnProperty(value = "fhir.server.profile.bootstrap", havingValue = "true", matchIfMissing = true)
    @Bean
    SmartInitializingSingleton createProfilesIfNeeded(IGenericClient fhirClient) {
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
