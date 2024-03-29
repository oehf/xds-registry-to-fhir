package org.openehealth.app.xdstofhir.registry.remove;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import org.junit.jupiter.api.Test;
import org.mockserver.model.MediaType;
import org.openehealth.app.xdstofhir.registry.AbstractFhirMockserver;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.ipf.commons.ihe.xds.core.SampleData;
import org.openehealth.ipf.commons.ihe.xds.core.responses.ErrorCode;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Status;

class RemoveDocumentsProcessorTest  extends AbstractFhirMockserver {
    private RemoveDocumentsProcessor classUnderTest;


    @Override
    protected void initClassUnderTest() {
        classUnderTest = new RemoveDocumentsProcessor(newRestfulGenericClient);
    }


    @Test
    void removeForNotPresent() {
        mockServer.when(request()).respond(response().withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON).withBody(EMPTY_BUNDLE_RESULT));

        var removeRemove = SampleData.createRemoveMetadata();
        var response = classUnderTest.remove(removeRemove);
        assertEquals(Status.FAILURE, response.getStatus());
        assertEquals(1, response.getErrors().size());
        assertEquals(ErrorCode.UNRESOLVED_REFERENCE_EXCEPTION, response.getErrors().get(0).getErrorCode());

        mockServer.verify(request("/DocumentReference")
                .withQueryStringParameter("identifier", "urn:ietf:rfc:3986|urn:uuid:b2632452-1de7-480d-94b1-c2074d79c871,urn:ietf:rfc:3986|urn:uuid:b2632df2-1de7-480d-1045-c2074d79aabd")
                .withQueryStringParameter("_revinclude", "List:item")
                .withQueryStringParameter("_profile",
                       MappingSupport.MHD_COMPREHENSIVE_PROFILE));
        mockServer.verify(request("/List")
                .withQueryStringParameter("item:identifier", "urn:ietf:rfc:3986|urn:uuid:b2632452-1de7-480d-94b1-c2074d79c871,urn:ietf:rfc:3986|urn:uuid:b2632df2-1de7-480d-1045-c2074d79aabd")
                .withQueryStringParameter("_revinclude", "List:item"));
        mockServer.verify(request("/List")
                .withQueryStringParameter("identifier", "urn:ietf:rfc:3986|urn:uuid:b2632452-1de7-480d-94b1-c2074d79c871,urn:ietf:rfc:3986|urn:uuid:b2632df2-1de7-480d-1045-c2074d79aabd")
                .withQueryStringParameter("_revinclude", "List:item"));
    }
}
