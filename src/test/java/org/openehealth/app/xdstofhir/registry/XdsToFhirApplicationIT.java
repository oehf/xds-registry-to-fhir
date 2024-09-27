package org.openehealth.app.xdstofhir.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openehealth.ipf.commons.ihe.xds.core.responses.Status.FAILURE;
import static org.openehealth.ipf.commons.ihe.xds.core.responses.Status.SUCCESS;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;
import lombok.SneakyThrows;
import org.apache.camel.Produce;
import org.junit.jupiter.api.Test;
import org.openehealth.app.xdstofhir.registry.patientfeed.Iti8Service;
import org.openehealth.app.xdstofhir.registry.query.Iti18Service;
import org.openehealth.app.xdstofhir.registry.register.Iti42Service;
import org.openehealth.app.xdstofhir.registry.remove.Iti62Service;
import org.openehealth.ipf.commons.ihe.xds.core.SampleData;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssigningAuthority;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AvailabilityStatus;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Folder;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Identifiable;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.ObjectReference;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.SubmissionSet;
import org.openehealth.ipf.commons.ihe.xds.core.requests.QueryRegistry;
import org.openehealth.ipf.commons.ihe.xds.core.requests.RemoveMetadata;
import org.openehealth.ipf.commons.ihe.xds.core.requests.builder.RegisterDocumentSetBuilder;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindDocumentsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindSubmissionSetsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetAllQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetFolderAndContentsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetSubmissionSetAndContentsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.QueryReturnType;
import org.openehealth.ipf.commons.ihe.xds.core.responses.ErrorCode;
import org.openehealth.ipf.commons.ihe.xds.core.responses.QueryResponse;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Response;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext
class XdsToFhirApplicationIT {

    @Produce("xds-iti18://localhost:{{local.server.port}}/services/registry/iti18")
    Iti18Service storedQuery;

    @Produce("xds-iti42://localhost:{{local.server.port}}/services/registry/iti42")
    Iti42Service registerDocuments;

    @Produce("rmd-iti62://localhost:{{local.server.port}}/services/registry/iti62")
    Iti62Service removeDocuments;

    @Produce("xds-iti8:0.0.0.0:2575")
    Iti8Service patientIdentityFeed;

    /**
     * Perform a basic roundtrip by:
     * * Register a new patient with a ADT A01 using ITI-8
     * * Register one document metadata in the registry for this patient using ITI-42
     * * Query the patient documents using ITI-18 and verify that 1 document is now present
     */
    @Test
    void xdsRoundTrip() throws InterruptedException {
        var patientId = new Identifiable("IPF-" + System.currentTimeMillis(),
                new AssigningAuthority("2.999.1.2.3.4"));

        patientIdentityFeed.registerPatient(somePatientFromMPI(patientId));

        registerSampleDocForPatient(patientId);

        var response = findDocumentFor(patientId);
        assertEquals(SUCCESS, response.getStatus());
        assertEquals(1, response.getDocumentEntries().size());
        assertEquals(0, response.getSubmissionSets().size());


        response = findSubmissionSetsFor(patientId);
        assertEquals(SUCCESS, response.getStatus());
        assertEquals(1, response.getSubmissionSets().size());
        assertEquals(0, response.getDocumentEntries().size());

        response = getAllFor(patientId);
        assertEquals(SUCCESS, response.getStatus());
        List<SubmissionSet> submissionSets = response.getSubmissionSets();
        assertEquals(1, submissionSets.size());
        assertEquals(1, response.getDocumentEntries().size());
        assertEquals(1, response.getFolders().size());
        assertEquals(3, response.getAssociations().size());

        response = getSubmissionSetAndContent(submissionSets.iterator().next().getEntryUuid());
        assertEquals(SUCCESS, response.getStatus());
        assertEquals(1, response.getSubmissionSets().size());
        assertEquals(1, response.getDocumentEntries().size());
        List<Folder> folders = response.getFolders();
        assertEquals(1, folders.size());
        assertEquals(3, response.getAssociations().size());

        response = getFolderAndContents(folders.iterator().next().getEntryUuid());
        assertEquals(SUCCESS, response.getStatus());
        assertEquals(0, response.getSubmissionSets().size());
        assertEquals(1, response.getDocumentEntries().size());
        assertEquals(1, response.getFolders().size());

    }

    @Test
    void verifyPatientNotExist() {
        var response = findDocumentFor(new Identifiable("no-present-patient",
                new AssigningAuthority("2.999.1.2.3.4")));
        assertTrue(response.getDocumentEntries().isEmpty());
        assertEquals(SUCCESS, response.getStatus());
    }

    @Test
    void unknownRepositoryId() {
        var unknownRepositoryId = "2.999.1.1.1.1";
        var register = SampleData.createRegisterDocumentSet();
        register.getDocumentEntries().forEach(doc -> doc.setRepositoryUniqueId(unknownRepositoryId));
        Response registerResponse = registerDocuments.processRegister(register);
        assertEquals(FAILURE, registerResponse.getStatus());
        assertEquals(1,registerResponse.getErrors().size());
        assertEquals(ErrorCode.UNKNOWN_REPOSITORY_ID, registerResponse.getErrors().get(0).getErrorCode());
    }

    @Test
    void removeDocument() {
        var patientId = new Identifiable("IPF-" + System.currentTimeMillis(),
                new AssigningAuthority("2.999.1.2.3.4"));

        patientIdentityFeed.registerPatient(somePatientFromMPI(patientId));

        var doc = SampleData.createDocumentEntry(patientId);
        doc.assignEntryUuid();
        var sub = SampleData.createSubmissionSet(patientId);
        sub.assignEntryUuid();
        var register = new RegisterDocumentSetBuilder(true, sub).withDocument(doc).build();
        registerDocuments.processRegister(register);

        var metadataRequest = new RemoveMetadata();
        metadataRequest.getReferences().add(new ObjectReference(doc.getEntryUuid()));
        metadataRequest.getReferences().add(new ObjectReference(sub.getEntryUuid()));
        register.getAssociations()
                .forEach(assoc -> metadataRequest.getReferences().add(new ObjectReference(assoc.getEntryUuid())));

        var response = removeDocuments.remove(metadataRequest);
        assertEquals(SUCCESS, response.getStatus());

    }

    private QueryResponse findDocumentFor(Identifiable patientId) {
        var fd = new FindDocumentsQuery();
        fd.setStatus(List.of(AvailabilityStatus.APPROVED));
        // https://hapi.fhir.org/baseR4/DocumentReference?_pretty=true&_profile=https://profiles.ihe.net/ITI/MHD/StructureDefinition/IHE.MHD.Comprehensive.DocumentReference&patient.identifier=urn:oid:1.2.40.0.10.1.4.3.1|1419180172&_include=DocumentReference:subject
        fd.setPatientId(patientId);
        var query = new QueryRegistry(fd, QueryReturnType.LEAF_CLASS);
        var response = storedQuery.processQuery(query);
        return response;
    }

    private QueryResponse getAllFor(Identifiable patientId) {
        var ga = new GetAllQuery();
        ga.setStatusSubmissionSets(List.of(AvailabilityStatus.APPROVED));
        ga.setStatusDocuments(List.of(AvailabilityStatus.APPROVED));
        ga.setStatusFolders(List.of(AvailabilityStatus.APPROVED));
        ga.setPatientId(patientId);
        var query = new QueryRegistry(ga, QueryReturnType.LEAF_CLASS);
        var response = storedQuery.processQuery(query);
        return response;
    }


    private QueryResponse getSubmissionSetAndContent(String uniqueId) {
        var subContentQ = new GetSubmissionSetAndContentsQuery();
        subContentQ.setUniqueId(uniqueId);
        var query = new QueryRegistry(subContentQ, QueryReturnType.LEAF_CLASS);
        var response = storedQuery.processQuery(query);
        return response;
    }

    private QueryResponse findSubmissionSetsFor(Identifiable patientId) {
        var fd = new FindSubmissionSetsQuery();
        fd.setStatus(List.of(AvailabilityStatus.APPROVED));
        // https://hapi.fhir.org/baseR4/DocumentReference?_pretty=true&_profile=https://profiles.ihe.net/ITI/MHD/StructureDefinition/IHE.MHD.Comprehensive.DocumentReference&patient.identifier=urn:oid:1.2.40.0.10.1.4.3.1|1419180172&_include=DocumentReference:subject
        fd.setPatientId(patientId);
        var query = new QueryRegistry(fd, QueryReturnType.LEAF_CLASS);
        var response = storedQuery.processQuery(query);
        return response;
    }


    private QueryResponse getFolderAndContents(String uniqueId) {
        var foldContentQ = new GetFolderAndContentsQuery();
        foldContentQ.setUniqueId(uniqueId);
        var query = new QueryRegistry(foldContentQ, QueryReturnType.LEAF_CLASS);
        var response = storedQuery.processQuery(query);
        return response;
    }

    private void registerSampleDocForPatient(Identifiable patientId) {
        var register = SampleData.createRegisterDocumentSet();
        register.getDocumentEntries().forEach(doc -> doc.setPatientId(patientId));
        register.getFolders().forEach(doc -> doc.setPatientId(patientId));
        register.getSubmissionSet().setPatientId(patientId);
        registerDocuments.processRegister(register);
    }

    @SneakyThrows
    private Message somePatientFromMPI(Identifiable indexPatientId) {
        var content = Files
                .readString(Paths.get(getClass().getClassLoader().getResource("messages/msg-01.hl7").toURI()));
        content = content
                .replace("$PATIENTID", indexPatientId.getId())
                .replace("$MPI-OID", indexPatientId.getAssigningAuthority().getUniversalId());
        var hl7Parser = new PipeParser();
        var message = hl7Parser.parse(content);
        return message;
    }
}
