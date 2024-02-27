package org.openehealth.app.xdstofhir.registry.remove;

import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.URI_URN;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.util.BundleBuilder;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.ListResource;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.app.xdstofhir.registry.common.PagingFhirResultIterator;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdSubmissionSet;
import org.openehealth.app.xdstofhir.registry.query.StoredQueryMapper;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.ObjectReference;
import org.openehealth.ipf.commons.ihe.xds.core.requests.RemoveMetadata;
import org.openehealth.ipf.commons.ihe.xds.core.responses.ErrorCode;
import org.openehealth.ipf.commons.ihe.xds.core.responses.ErrorInfo;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Response;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Severity;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Status;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RemoveDocumentsProcessor implements Iti62Service {
    private final IGenericClient client;

    @Override
    public Response remove(RemoveMetadata metadataToRemove) {
        var uuidsToDelete = new ArrayList<String>(metadataToRemove.getReferences().stream().map(ObjectReference::getId).toList());
        var builder = new BundleBuilder(client.getFhirContext());

        var documentFhirQuery = client.search().forResource(DocumentReference.class)
                .withProfile(MappingSupport.MHD_COMPREHENSIVE_PROFILE)
                .where(DocumentReference.IDENTIFIER.exactly().systemAndValues(URI_URN,uuidsToDelete))
                .returnBundle(Bundle.class);

        var docResult = new PagingFhirResultIterator<DocumentReference>(documentFhirQuery.execute(),
                DocumentReference.class, client);
        docResult.forEachRemaining(doc -> {
            checkAssociations(doc, uuidsToDelete, builder);
            addToDeleteTransaction(doc, uuidsToDelete, builder);
        });


        var submissionSetFhirQuery = client.search().forResource(MhdSubmissionSet.class)
                .withProfile(MappingSupport.MHD_COMPREHENSIVE_SUBMISSIONSET_PROFILE)
                .where(ListResource.CODE.exactly().codings(MhdSubmissionSet.SUBMISSIONSET_CODEING.getCodingFirstRep()))
                .where(ListResource.IDENTIFIER.exactly().systemAndValues(URI_URN,uuidsToDelete))
                .returnBundle(Bundle.class);

        var submissionSetResults = new PagingFhirResultIterator<MhdSubmissionSet>(submissionSetFhirQuery.execute(),
                MhdSubmissionSet.class, client);
        submissionSetResults.forEachRemaining(sub -> {
            checkAssociations(sub, uuidsToDelete, builder);
            addToDeleteTransaction(sub, uuidsToDelete, builder);
        });

        final Response response;
        if (!uuidsToDelete.isEmpty()) {
            response = new Response(Status.FAILURE);
            response.setErrors(Collections.singletonList(new ErrorInfo(ErrorCode.UNRESOLVED_REFERENCE_EXCEPTION,
                    "Result exceed maximum of " + String.join(",", uuidsToDelete), Severity.ERROR, null, null)));
        } else {
            client.transaction().withBundle(builder.getBundle()).execute();
            response = new Response(Status.SUCCESS);
        }

        return response;
    }

    private void checkAssociations(MhdSubmissionSet sub, ArrayList<String> uuidsToDelete, BundleBuilder builder) {
        sub.getEntry().stream().filter(rel -> uuidsToDelete.contains(rel.getId())).findAny().ifPresent(rel -> {
            uuidsToDelete.remove(rel.getId());
            var entryUuid = StoredQueryMapper.entryUuidFrom(sub);
            if (!uuidsToDelete.contains(entryUuid)) {
                sub.getEntry().remove(rel);
                builder.addTransactionUpdateEntry(sub);
            }
        });
    }

    private void checkAssociations(DocumentReference doc, ArrayList<String> uuidsToDelete, BundleBuilder builder) {
        doc.getRelatesTo().stream().filter(rel -> uuidsToDelete.contains(rel.getId())).findAny().ifPresent(rel -> {
            uuidsToDelete.remove(rel.getId());
            var entryUuid = StoredQueryMapper.entryUuidFrom(doc);
            if (!uuidsToDelete.contains(entryUuid)) {
                doc.getRelatesTo().remove(rel);
                builder.addTransactionUpdateEntry(doc);
            }
        });
    }

    private void addToDeleteTransaction(IAnyResource resource, List<String> uuidsToDelete, BundleBuilder builder) {
        var entryUuid = StoredQueryMapper.entryUuidFrom(resource);
        if (uuidsToDelete.contains(entryUuid)) {
            builder.addTransactionDeleteEntry(resource);
            uuidsToDelete.remove(entryUuid);
        }
    }

}
