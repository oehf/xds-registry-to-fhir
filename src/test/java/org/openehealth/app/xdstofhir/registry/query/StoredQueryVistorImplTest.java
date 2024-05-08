package org.openehealth.app.xdstofhir.registry.query;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockserver.model.MediaType;
import org.openehealth.app.xdstofhir.registry.AbstractFhirMockserver;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.ipf.commons.ihe.xds.core.SampleData;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindDocumentsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindSubmissionSetsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetAllQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetDocumentsAndAssociationsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetFoldersForDocumentQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetFoldersQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetRelatedDocumentsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetSubmissionSetAndContentsQuery;

public class StoredQueryVistorImplTest extends AbstractFhirMockserver {
    private StoredQueryVistorImpl classUnderTest;

    @Override
    protected void initClassUnderTest()  {
        classUnderTest = new StoredQueryVistorImpl(newRestfulGenericClient, Mockito.mock(StoredQueryProcessor.class), true);
    }

    @Test
    void testFindDocumentsQuery (){
        mockServer.when(
                request().withPath("/DocumentReference"))
                .respond(response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
                .withBody(EMPTY_BUNDLE_RESULT));
        var query = (FindDocumentsQuery) SampleData.createFindDocumentsQuery().getQuery();
        classUnderTest.visit(query);

        mockServer.verify(request()
                .withQueryStringParameter("date", "ge19.*", "lt19.*")//skip verify the whole datetime to avoid timezone issues
                .withQueryStringParameter("period",  "ge19.*", "lt19.*", "ge19.*", "lt19.*")
                .withQueryStringParameter("patient:Patient.identifier", "urn:oid:1.3|id3")
                .withQueryStringParameter("format", "urn:ihe:xds:scheme13|code13,urn:ihe:xds:scheme14|code14")
                .withQueryStringParameter("type", "urn:ihe:xds:schemet1|codet1,urn:ihe:xds:schemet2|codet2")
                .withQueryStringParameter("setting", "urn:ihe:xds:scheme3|code3,urn:ihe:xds:scheme4|code4")
                .withQueryStringParameter("_include", "DocumentReference:subject")
                .withQueryStringParameter("_profile", MappingSupport.MHD_COMPREHENSIVE_PROFILE)
                .withQueryStringParameter("category", "urn:ihe:xds:scheme1|code1,urn:ihe:xds:scheme2|code2")
                .withQueryStringParameter("event", "urn:ihe:xds:scheme7|code7,urn:ihe:xds:scheme8|code8", "urn:ihe:xds:scheme9|code9")
                .withQueryStringParameter("facility", "urn:ihe:xds:scheme5|code5,urn:ihe:xds:scheme6|code6")
                .withQueryStringParameter("status", "current")
                );
    }

    @Test
    void testFindSubmissionSetQuery (){
        mockServer.when(
                request().withPath("/List"))
                .respond(response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
                .withBody(EMPTY_BUNDLE_RESULT));
        var query = (FindSubmissionSetsQuery) SampleData.createFindSubmissionSetsQuery().getQuery();
        classUnderTest.visit(query);

        mockServer.verify(request()
                .withQueryStringParameter("patient:Patient.identifier", "urn:oid:1.2|id1")
                .withQueryStringParameter("_include", "List:subject")
                .withQueryStringParameter("_profile", MappingSupport.MHD_COMPREHENSIVE_SUBMISSIONSET_PROFILE)
                .withQueryStringParameter("code", "https://profiles.ihe.net/ITI/MHD/CodeSystem/MHDlistTypes|submissionset")
                );
    }

    @Test
    void testGetAllQuery (){
        mockServer.when(
                request().withPath("/DocumentReference"))
                .respond(response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
                .withBody(EMPTY_BUNDLE_RESULT));
        mockServer.when(
                request().withPath("/List"))
                .respond(response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
                .withBody(EMPTY_BUNDLE_RESULT));
        var query = (GetAllQuery) SampleData.createGetAllQuery().getQuery();
        classUnderTest.visit(query);

        mockServer.verify(request("/DocumentReference")
                .withQueryStringParameter("patient:Patient.identifier", "urn:oid:1.2|id1")
                .withQueryStringParameter("_include", "DocumentReference:subject")
                .withQueryStringParameter("_profile",
                       MappingSupport.MHD_COMPREHENSIVE_PROFILE));
        mockServer.verify(request("/List")
                .withQueryStringParameter("patient:Patient.identifier", "urn:oid:1.2|id1")
                .withQueryStringParameter("_include", "List:subject")
                .withQueryStringParameter("_profile",
                        MappingSupport.MHD_COMPREHENSIVE_SUBMISSIONSET_PROFILE));
        mockServer.verify(request("/List")
                .withQueryStringParameter("patient:Patient.identifier", "urn:oid:1.2|id1")
                .withQueryStringParameter("_include", "List:subject")
                .withQueryStringParameter("_profile",
                        MappingSupport.MHD_COMPREHENSIVE_FOLDER_PROFILE));
    }

    @Test
    void testGetSubmissionSetAndContentsQuery (){
        mockServer.when(
                request().withPath("/DocumentReference"))
                .respond(response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
                .withBody(EMPTY_BUNDLE_RESULT));
        mockServer.when(
                request().withPath("/List"))
                .respond(response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
                .withBody(EMPTY_BUNDLE_RESULT));
        var query = (GetSubmissionSetAndContentsQuery) SampleData.createGetSubmissionSetAndContentsQuery().getQuery();
        classUnderTest.visit(query);

        mockServer.verify(request("/List")
                .withQueryStringParameter("identifier", "urn:ietf:rfc:3986|urn:ihe:xds:12.21.34,urn:ietf:rfc:3986|urn:uuid:1.2.3.4")
                .withQueryStringParameter("_include", "List:subject")
                .withQueryStringParameter("_profile",
                        MappingSupport.MHD_COMPREHENSIVE_SUBMISSIONSET_PROFILE));

        mockServer.verify(request("/List")
                .withQueryStringParameter("_has:List:item:identifier", "urn:ihe:xds:12.21.34", "urn:uuid:1.2.3.4")
                .withQueryStringParameter("_include", "List:subject")
                .withQueryStringParameter("_profile",
                        MappingSupport.MHD_COMPREHENSIVE_FOLDER_PROFILE));

        mockServer.verify(request("/DocumentReference")
                .withQueryStringParameter("_has:List:item:identifier", "urn:ihe:xds:12.21.34", "urn:uuid:1.2.3.4")
                .withQueryStringParameter("_include", "DocumentReference:subject")
                .withQueryStringParameter("_profile",
                       MappingSupport.MHD_COMPREHENSIVE_PROFILE));
    }

    @Test
    void testGetFoldersQuery (){
        mockServer.when(
                request().withPath("/List"))
                .respond(response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
                .withBody(EMPTY_BUNDLE_RESULT));
        var query = (GetFoldersQuery) SampleData.createGetFoldersQuery().getQuery();
        classUnderTest.visit(query);

        mockServer.verify(request()
                .withQueryStringParameter("identifier",
                        "urn:ietf:rfc:3986|urn:ihe:xds:12.21.34,urn:ietf:rfc:3986|"
                               + "urn:ihe:xds:43.56.89,urn:ietf:rfc:3986|"
                               + "urn:uuid:1.2.3.4,urn:ietf:rfc:3986|urn:uuid:2.3.4.5")
                .withQueryStringParameter("_include", "List:subject")
                .withQueryStringParameter("_profile", MappingSupport.MHD_COMPREHENSIVE_FOLDER_PROFILE)
                .withQueryStringParameter("code", "https://profiles.ihe.net/ITI/MHD/CodeSystem/MHDlistTypes|folder"));
    }

    @Test
    void testGetFolderForDocuments (){
        mockServer.when(
                request().withPath("/List"))
                .respond(response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
                .withBody(EMPTY_BUNDLE_RESULT));
        var query = (GetFoldersForDocumentQuery) SampleData.createGetFoldersForDocumentQuery().getQuery();
        classUnderTest.visit(query);

        mockServer.verify(request()
                .withQueryStringParameter("item:DocumentReference.identifier", "urn:ietf:rfc:3986|urn:ihe:xds:12.21.34")
                .withQueryStringParameter("_include", "List:subject")
                .withQueryStringParameter("_profile", MappingSupport.MHD_COMPREHENSIVE_FOLDER_PROFILE)
                .withQueryStringParameter("code", "https://profiles.ihe.net/ITI/MHD/CodeSystem/MHDlistTypes|folder")
                );
    }

    @Test
    void testGetRelatedDocuments (){
        mockServer.when(
                request().withPath("/DocumentReference"))
                .respond(response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
                .withBody(EMPTY_BUNDLE_RESULT));
        var query = (GetRelatedDocumentsQuery) SampleData.createGetRelatedDocumentsQuery().getQuery();
        classUnderTest.visit(query);

        mockServer.verify(request()
                .withQueryStringParameter("identifier", "urn:ietf:rfc:3986|urn:ihe:xds:12.21.34")
                .withQueryStringParameter("_include", "DocumentReference:subject", "DocumentReference:relatesto")
                .withQueryStringParameter("_profile", MappingSupport.MHD_COMPREHENSIVE_PROFILE)
                .withQueryStringParameter("relatesto:missing", "false")
                );
        mockServer.verify(request()
                .withQueryStringParameter("relatesto.identifier", "urn:ietf:rfc:3986|urn:ihe:xds:12.21.34")
                .withQueryStringParameter("_include", "DocumentReference:subject", "DocumentReference:relatesto")
                .withQueryStringParameter("_profile", MappingSupport.MHD_COMPREHENSIVE_PROFILE)
                .withQueryStringParameter("relatesto:missing", "false")
                );
    }

    @Test
    void testGetDocumentsAndAssociations (){
        mockServer.when(
                request().withPath("/DocumentReference"))
                .respond(response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
                .withBody(EMPTY_BUNDLE_RESULT));
        mockServer.when(
                request().withPath("/List"))
                .respond(response().withStatusCode(200).withContentType(MediaType.APPLICATION_JSON)
                .withBody(EMPTY_BUNDLE_RESULT));
        var query = (GetDocumentsAndAssociationsQuery) SampleData.createGetDocumentsAndAssociationsQuery().getQuery();
        classUnderTest.visit(query);

        mockServer.verify(request()
                .withQueryStringParameter("identifier", "urn:ietf:rfc:3986|urn:ihe:xds:12.21.34,urn:ietf:rfc:3986|urn:ihe:xds:43.56.89,urn:ietf:rfc:3986|urn:uuid:1.2.3.4,urn:ietf:rfc:3986|urn:uuid:2.3.4.5")
                .withQueryStringParameter("_include", "DocumentReference:subject")
                .withQueryStringParameter("_profile", MappingSupport.MHD_COMPREHENSIVE_PROFILE)
                );
        mockServer.verify(request("/List")
                .withQueryStringParameter("item:DocumentReference.identifier", "urn:ietf:rfc:3986|urn:ihe:xds:12.21.34,urn:ietf:rfc:3986|urn:ihe:xds:43.56.89,urn:ietf:rfc:3986|urn:uuid:1.2.3.4,urn:ietf:rfc:3986|urn:uuid:2.3.4.5")
                .withQueryStringParameter("_include", "List:subject")
                .withQueryStringParameter("_profile",
                        MappingSupport.MHD_COMPREHENSIVE_SUBMISSIONSET_PROFILE));

        mockServer.verify(request("/List")
                .withQueryStringParameter("item:DocumentReference.identifier", "urn:ietf:rfc:3986|urn:ihe:xds:12.21.34,urn:ietf:rfc:3986|urn:ihe:xds:43.56.89,urn:ietf:rfc:3986|urn:uuid:1.2.3.4,urn:ietf:rfc:3986|urn:uuid:2.3.4.5")
                .withQueryStringParameter("_include", "List:subject")
                .withQueryStringParameter("_profile",
                        MappingSupport.MHD_COMPREHENSIVE_FOLDER_PROFILE));
    }

}
