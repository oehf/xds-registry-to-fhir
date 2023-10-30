package org.openehealth.app.xdstofhir.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openehealth.ipf.commons.ihe.xds.core.responses.Status.FAILURE;
import static org.openehealth.ipf.commons.ihe.xds.core.responses.Status.SUCCESS;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;
import lombok.SneakyThrows;
import org.apache.camel.Produce;
import org.junit.jupiter.api.Test;
import org.openehealth.app.xdstofhir.registry.patientfeed.Iti8Service;
import org.openehealth.app.xdstofhir.registry.query.Iti18Service;
import org.openehealth.app.xdstofhir.registry.register.Iti42Service;
import org.openehealth.ipf.commons.ihe.xds.core.SampleData;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssigningAuthority;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AvailabilityStatus;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Identifiable;
import org.openehealth.ipf.commons.ihe.xds.core.requests.QueryRegistry;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindDocumentsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.QueryReturnType;
import org.openehealth.ipf.commons.ihe.xds.core.responses.ErrorCode;
import org.openehealth.ipf.commons.ihe.xds.core.responses.QueryResponse;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Response;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class XdsToFhirApplicationIT {

    @Produce("xds-iti18://localhost:{{local.server.port}}/services/registry/iti18")
    Iti18Service storedQuery;

    @Produce("xds-iti42://localhost:{{local.server.port}}/services/registry/iti42")
    Iti42Service registerDocuments;

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

        TimeUnit.SECONDS.sleep(2); //TODO: Kodjin seems to need a short delay before the resource can be used

        registerSampleDocForPatient(patientId);

        var response = findDocumentFor(patientId);
        assertEquals(SUCCESS, response.getStatus());

        assertTrue(response.getDocumentEntries().size() == 1);
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

    private QueryResponse findDocumentFor(Identifiable patientId) {
        var fd = new FindDocumentsQuery();
        fd.setStatus(List.of(AvailabilityStatus.APPROVED));
        // https://hapi.fhir.org/baseR4/DocumentReference?_pretty=true&_profile=https://profiles.ihe.net/ITI/MHD/StructureDefinition/IHE.MHD.Comprehensive.DocumentReference&patient.identifier=urn:oid:1.2.40.0.10.1.4.3.1|1419180172&_include=DocumentReference:subject
        fd.setPatientId(patientId);
        var query = new QueryRegistry(fd, QueryReturnType.LEAF_CLASS);
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
