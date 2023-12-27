package org.openehealth.app.xdstofhir.registry.register;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import org.apache.groovy.util.Maps;
import org.junit.jupiter.api.Test;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.JsonBody;
import org.mockserver.model.MediaType;
import org.openehealth.app.xdstofhir.registry.AbstractFhirMockserver;
import org.openehealth.app.xdstofhir.registry.common.RegistryConfiguration;
import org.openehealth.app.xdstofhir.registry.common.mapper.XdsToFhirDocumentMapper;
import org.openehealth.app.xdstofhir.registry.common.mapper.XdsToFhirFolderMapper;
import org.openehealth.app.xdstofhir.registry.common.mapper.XdsToFhirSubmissionsetMapper;
import org.openehealth.ipf.commons.ihe.xds.core.SampleData;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Status;
import org.openehealth.ipf.commons.ihe.xds.core.validate.ValidationMessage;
import org.openehealth.ipf.commons.ihe.xds.core.validate.XDSMetaDataException;
import org.openehealth.ipf.commons.spring.map.SpringBidiMappingService;
import org.springframework.core.io.ClassPathResource;

class RegisterDocumentsProcessorTest extends AbstractFhirMockserver {
    private RegisterDocumentsProcessor classUnderTest;


    @Override
    protected void initClassUnderTest() {
        var registryConfig = new RegistryConfiguration();
        registryConfig.setDefaultHash("0000000000000000000000000000000000000000");
        registryConfig.setRepositoryEndpoint(Maps.of("1.2.3.4", "http://my.doc.retrieve/binary/$documentUniqueId"));
        registryConfig.setUnknownRepositoryId("2.999.1.2.3");
        var mappingService = new SpringBidiMappingService();
        mappingService.setMappingResource(new ClassPathResource("META-INF/map/fhir-hl7v2-translation.map"));
        var documentMapper = new XdsToFhirDocumentMapper(registryConfig, mappingService);
        var submissionSetMapper = new XdsToFhirSubmissionsetMapper();
        var folderMapper = new XdsToFhirFolderMapper();

        classUnderTest = new RegisterDocumentsProcessor(newRestfulGenericClient, documentMapper, submissionSetMapper,
                folderMapper, registryConfig);
    }

    @Test
    void registerDocumentForUnknownPatient() {
        mockServer.when(request().withPath("/DocumentReference")).respond(response().withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON).withBody(EMPTY_BUNDLE_RESULT));
        mockServer.when(request().withPath("/Patient")).respond(response().withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON).withBody(EMPTY_BUNDLE_RESULT));

        var someRegister = SampleData.createRegisterDocumentSet();
        var validationError = assertThrows(XDSMetaDataException.class,
                () -> classUnderTest.processRegister(someRegister));
        assertEquals(ValidationMessage.UNKNOWN_PATIENT_ID, validationError.getValidationMessage());
    }

    @Test
    void registerDocumentSuccess() {
        mockServer.when(request().withPath("/DocumentReference")).respond(response().withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON).withBody(EMPTY_BUNDLE_RESULT));

        String patientExists = "{\"resourceType\":\"Bundle\",\"id\":\"98925b67-fef2-4d69-9c5a-678b5af65035\",\"type\":\"searchset\","
                + "\"total\":1,\"link\":[{\"relation\":\"self\",\"url\":\"https://test.server\"}],\"entry\":[{\"fullUrl\":\"https://test.server/Patient/41689208\","
                + "\"resource\":{\"resourceType\":\"Patient\",\"id\":\"41689208\",\"identifier\":[{\"system\":\"urn:oid:2.999.1.2.3.4\",\"value\":\"IPF-1702811148996\"}],"
                + "\"active\":true,\"name\":[{\"family\":\"BEN\",\"given\":[\"STILLER\"]}]},\"search\":{\"mode\":\"match\"}}]}";
        mockServer.when(request().withPath("/Patient")).respond(response().withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON).withBody(patientExists));
        mockServer.when(
                request().withMethod("POST").withPath("/"))
                .respond(response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
                .withBody(EMPTY_BUNDLE_RESULT));

        var someRegister = SampleData.createRegisterDocumentSet();
        var response = classUnderTest.processRegister(someRegister);
        assertEquals(Status.SUCCESS, response.getStatus());

        mockServer.verify(request()
                .withMethod("POST")
                .withBody(new JsonBody("{resourceType: 'Bundle'}", MatchType.ONLY_MATCHING_FIELDS))
                );
    }

}
