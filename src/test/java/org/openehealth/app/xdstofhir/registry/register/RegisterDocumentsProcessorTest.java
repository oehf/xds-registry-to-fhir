package org.openehealth.app.xdstofhir.registry.register;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssigningAuthority;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Association;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssociationType;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Identifiable;
import org.openehealth.ipf.commons.ihe.xds.core.requests.builder.RegisterDocumentSetBuilder;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Status;
import org.openehealth.ipf.commons.ihe.xds.core.validate.ValidationMessage;
import org.openehealth.ipf.commons.ihe.xds.core.validate.XDSMetaDataException;
import org.openehealth.ipf.commons.spring.map.SpringBidiMappingService;
import org.springframework.core.io.ClassPathResource;

class RegisterDocumentsProcessorTest extends AbstractFhirMockserver {
	
    private RegisterDocumentsProcessor classUnderTest;
    
    private static String PATIENT_RESPONSE_MOCK = """
    		{"resourceType":"Bundle","id":"98925b67-fef2-4d69-9c5a-678b5af65035","type":"searchset",
            "total":1,"link":[{"relation":"self","url":"https://test.server"}],"entry":[{"fullUrl":"https://test.server/Patient/41689208",
            "resource":{"resourceType":"Patient","id":"41689208","identifier":[{"system":"urn:oid:2.999.1.2.3.4","value":"IPF-1702811148996"}],                 
			"active":true,"name":[{"family":"BEN","given":["STILLER"]}]},"search":{"mode":"match"}}]}
    		""";


    @Override
    protected void initClassUnderTest() {
        var registryConfig = new RegistryConfiguration();
        registryConfig.setDefaultHash("0000000000000000000000000000000000000000");
        registryConfig.setRepositoryEndpoint(Maps.of("1.2.3.4", "http://my.doc.retrieve/binary/$documentUniqueId"));
        registryConfig.setUnknownRepositoryId("2.999.1.2.3");
        var mappingService = new SpringBidiMappingService();
        mappingService.setMappingResource(new ClassPathResource("META-INF/map/fhir-hl7v2-translation.map"));
        mappingService.setMappingResource(new ClassPathResource("META-INF/map/codesystem-fhir-translation.map"));
        var documentMapper = new XdsToFhirDocumentMapper(registryConfig);
        documentMapper.setFhirMapping(mappingService);
        var submissionSetMapper = new XdsToFhirSubmissionsetMapper();
        submissionSetMapper.setFhirMapping(mappingService);
        var folderMapper = new XdsToFhirFolderMapper();
        folderMapper.setFhirMapping(mappingService);

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

        mockServer.when(request().withPath("/Patient")).respond(response().withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON).withBody(PATIENT_RESPONSE_MOCK));
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
    
    @Test
    void registerDocumentSuccessAndValidateRequest() throws IOException, URISyntaxException {
        mockServer.when(request().withPath("/DocumentReference")).respond(response().withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON).withBody(EMPTY_BUNDLE_RESULT));

        mockServer.when(request().withPath("/Patient")).respond(response().withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON).withBody(PATIENT_RESPONSE_MOCK));
        mockServer.when(
                request().withMethod("POST").withPath("/"))
                .respond(response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
                .withBody(EMPTY_BUNDLE_RESULT));

        var patientID = new Identifiable("id3", new AssigningAuthority("1.3"));

        var submissionSet = SampleData.createSubmissionSet(patientID);
        submissionSet.setEntryUuid("urn:uuid:58642ea0-cb28-4757-af3f-88bca44b3001");
        var docEntry = SampleData.createDocumentEntry(patientID);
        docEntry.setEntryUuid("urn:uuid:58642ea0-cb28-4757-af3f-88bca44b3002");
        var folder = SampleData.createFolder(patientID);
        folder.setEntryUuid("urn:uuid:58642ea0-cb28-4757-af3f-88bca44b3003");
        var docFolderAssoc = new Association(AssociationType.HAS_MEMBER, "urn:uuid:58642ea0-cb28-4757-af3f-88bca44b3004", folder.getEntryUuid(), docEntry.getEntryUuid());
        var docSubAssoc = new Association(AssociationType.HAS_MEMBER, "urn:uuid:58642ea0-cb28-4757-af3f-88bca44b3005", submissionSet.getEntryUuid(), docEntry.getEntryUuid());
        var folderSubAssoc = new Association(AssociationType.HAS_MEMBER, "urn:uuid:58642ea0-cb28-4757-af3f-88bca44b3006", submissionSet.getEntryUuid(), folder.getEntryUuid());
        var assocAssoc = new Association(AssociationType.HAS_MEMBER, "urn:uuid:58642ea0-cb28-4757-af3f-88bca44b3007", submissionSet.getEntryUuid(), docFolderAssoc.getEntryUuid());
               
        
        var someRegister = new RegisterDocumentSetBuilder(true, submissionSet)
        		.withDocument(docEntry)
        		.withFolder(folder)
        		.withAssociations(List.of(docFolderAssoc, docSubAssoc, folderSubAssoc, assocAssoc)).build();
        var response = classUnderTest.processRegister(someRegister);
        assertEquals(Status.SUCCESS, response.getStatus());
        
        // validate against a json Unit template
        var validateResponseTemplate = Files.readString(Path.of(getClass().getResource("/messages/fhir-register-validation-template.json").toURI()));

        mockServer.verify(request()
                .withMethod("POST")
                .withBody(new JsonBody(validateResponseTemplate, MatchType.ONLY_MATCHING_FIELDS))
                );
    }

}
