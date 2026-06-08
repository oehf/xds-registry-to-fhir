package org.openehealth.app.xdstofhir.registry;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ca.uhn.fhir.util.BundleBuilder;
import lombok.extern.slf4j.Slf4j;

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

/**
 * Spring configuration for XDS registry adapter.
 * 
 * Configures FHIR client, CXF bus for web services, and profile initialization.
 */
@Configuration
@Slf4j
public class XdsSpringContext {

    @Value("classpath*:profiles/*.json")
    private Resource[] profiles;
    @Value("classpath*:META-INF/map/*.map")
    private Resource[] fhirMappings;

    /**
     * Creates the CXF Spring Bus bean for web services configuration.
     * 
     * Enables verbose logging for binary and multipart content for debugging purposes.
     *
     * @return configured SpringBus instance
     */
    @Bean(name = Bus.DEFAULT_BUS_ID)
    SpringBus springBus() {
        log.debug("Initializing CXF Spring Bus with logging features");
        var springBus = new SpringBus();
        var logging = new LoggingFeature();
        logging.setLogBinary(true);
        logging.setLogMultipart(true);
        logging.setVerbose(true);
        springBus.getFeatures().add(logging);
        BusFactory.setDefaultBus(springBus);
        log.info("CXF Spring Bus initialized successfully");
        return springBus;
    }

    /**
     * Creates the FHIR generic client bean for communication with FHIR server.
     * 
     * Configures:
     * - MHD profile mappings (Folder, SubmissionSet)
     * - Server validation mode to NEVER (validation delegated to server)
     * - Request/response logging interceptor
     * - Cache control directives
     *
     * @param fhirServerBase the base URL of the FHIR server (from fhir.server.base property)
     * @return configured IGenericClient instance
     * @throws IllegalArgumentException if fhirServerBase is invalid
     */
    @Bean
    IGenericClient fhirClient(@Value("${fhir.server.base}") String fhirServerBase) {
        log.info("Initializing FHIR client for server: {}", fhirServerBase);
        try {
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
            log.info("FHIR client initialized successfully with logging interceptor");
            return client;
        } catch (Exception e) {
            log.error("Failed to initialize FHIR client for server: {}", fhirServerBase, e);
            throw e;
        }
    }

    /**
     * Creates custom mappings bean for XDS-FHIR transformations.
     * 
     * Loads mapping definitions from classpath resources (META-INF/map/*.map).
     *
     * @return CustomMappings instance with configured mapping resources
     */
    @Bean
    CustomMappings customMapping() {
        log.debug("Loading custom FHIR mappings from classpath");
        var mapping = new CustomMappings();
        mapping.setMappingResources(List.of(fhirMappings));
        log.info("Custom mappings loaded: {} mapping files", fhirMappings.length);
        return mapping;
    }

    /**
     * Configures XUA (eXtensible User Authentication) support when enabled.
     * 
     * Only activated when property xds.xua.enabled=true.
     * Sets up WS-Security interceptors for SAML authentication.
     *
     * @param springBus the CXF Spring Bus
     * @param registryConfiguration configuration containing XUA settings
     * @return SmartInitializingSingleton that applies XUA configuration after bean initialization
     */
    @ConditionalOnProperty(prefix="xds.xua", value = "enabled", havingValue = "true")
    @Bean
    SmartInitializingSingleton applyXuaConfiguration(SpringBus springBus, RegistryConfiguration registryConfiguration) {
        return () -> {
            try {
                log.info("Applying XUA (eXtensible User Authentication) configuration");
                springBus.setProperties(createWss4jProperties(registryConfiguration.getXua()));
                springBus.getInInterceptors().add(createWss4jInterceptor());
                log.info("XUA configuration applied successfully");
            } catch (Exception e) {
                log.error("Failed to apply XUA configuration", e);
                throw new IllegalStateException("XUA configuration failed", e);
            }
        };
    }

    /**
     * Initializes MHD FHIR profiles in the server on application startup.
     * 
     * This bean:
     * 1. Loads MHD profile definitions from classpath resources (profiles/*.json)
     * 2. Creates a transaction bundle with all profiles
     * 3. Uploads profiles to FHIR server via transaction endpoint
     * 4. Gracefully handles already-existing profiles
     * 
     * Activation:
     * - Enabled by default (matchIfMissing=true)
     * - Can be disabled with property: fhir.server.profile.bootstrap=false
     *
     * @param fhirClient the FHIR generic client
     * @return SmartInitializingSingleton that bootstraps profiles after bean initialization
     * @throws IllegalStateException if MHD profile definitions cannot be read from classpath
     */
    @ConditionalOnProperty(prefix="fhir.server.profile", value = "bootstrap", havingValue = "true", matchIfMissing = true)
    @Bean
    SmartInitializingSingleton createProfilesIfNeeded(IGenericClient fhirClient) {
        return () -> {
            try {
                log.info("Starting MHD profile bootstrap: {} profiles to process", profiles.length);
                var builder = new BundleBuilder(fhirClient.getFhirContext());
                var fhirParser = fhirClient.getFhirContext().newJsonParser();
                
                for (var profile : profiles) {
                    try (InputStream inputStream = profile.getInputStream()) {
                        log.debug("Loading MHD profile from resource: {}", profile.getFilename());
                        builder.addTransactionUpdateEntry(fhirParser.parseResource(inputStream));
                    } catch (UnprocessableEntityException e) {
                        log.warn("Profile already exists or is invalid, skipping: {} - {}", profile.getFilename(), e.getMessage());
                    }
                }
                
                if (profiles.length > 0) {
                    log.info("Uploading {} MHD profiles to FHIR server via transaction", profiles.length);
                    fhirClient.transaction().withBundle(builder.getBundle()).execute();
                    log.info("MHD profile bootstrap completed successfully");
                }
            } catch (IOException e) {
                log.error("Failed to read MHD profile definitions from classpath", e);
                throw new IllegalStateException("MHD Profile definitions shall be present in classpath at classpath*:profiles/*.json", e);
            } catch (Exception e) {
                log.error("Unexpected error during profile bootstrap", e);
                throw new IllegalStateException("Profile bootstrap failed: " + e.getMessage(), e);
            }
        };
    }
}
