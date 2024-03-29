package org.openehealth.app.xdstofhir.registry.remove;

import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.URI_URN;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.util.BundleBuilder;
import ca.uhn.fhir.util.BundleUtil;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.instance.model.api.IBaseElement;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.DocumentReference.DocumentReferenceRelatesToComponent;
import org.hl7.fhir.r4.model.DocumentReference.DocumentRelationshipType;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.Reference;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.app.xdstofhir.registry.common.PagingFhirResultIterator;
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
        var errorInfo = new ArrayList<ErrorInfo>();
        var uuidsToDelete = new ArrayList<String>(metadataToRemove.getReferences().stream().map(ObjectReference::getId).toList());
        var unmodifiedUuidsToDelete = new ArrayList<String>(uuidsToDelete);
        var builder = new BundleBuilder(client.getFhirContext());

        var docBundleResult = client.search().forResource(DocumentReference.class)
                .withProfile(MappingSupport.MHD_COMPREHENSIVE_PROFILE)
                .include(DocumentReference.INCLUDE_RELATESTO)
                .revInclude(ListResource.INCLUDE_ITEM)
                .revInclude(DocumentReference.INCLUDE_RELATED)
                .where(DocumentReference.IDENTIFIER.exactly().systemAndValues(URI_URN,uuidsToDelete))
                .returnBundle(Bundle.class)
                .execute();

        // collect all List Resources (SubmissionSet, Folder) without duplicates (hapi domain objects do not implement equal / hashcode)
        var uniqueResults = new TreeSet<ListResource>((a, b) -> Comparator.comparing(IAnyResource::getId).compare(a,  b));

        var docResult = new PagingFhirResultIterator<DocumentReference>(docBundleResult, DocumentReference.class, client);
        uniqueResults.addAll(BundleUtil.toListOfResourcesOfType(client.getFhirContext(), docBundleResult, ListResource.class));
        docResult.forEachRemaining(doc -> {
            processAssociations(doc, doc.getRelatesTo(), uuidsToDelete, builder);
            if (addToDeleteTransaction(doc, uuidsToDelete, builder)) {
                validateNoReferencesExists(doc, docResult, unmodifiedUuidsToDelete, errorInfo);
            }
        });

        var reverseSearchCriteria = Collections.singletonMap("item:identifier", Collections.singletonList(
                uuidsToDelete.stream().map(MappingSupport::toUrnCoded).map(urnCoded -> URI_URN + "|" + urnCoded).collect(Collectors.joining(","))));
        var referenceQuery = client.search().forResource(ListResource.class)
                .whereMap(reverseSearchCriteria)
                .revInclude(ListResource.INCLUDE_ITEM)
                .returnBundle(Bundle.class);

        var listQuery = client.search().forResource(ListResource.class)
                .where(ListResource.IDENTIFIER.exactly().systemAndValues(URI_URN,uuidsToDelete))
                .revInclude(ListResource.INCLUDE_ITEM)
                .returnBundle(Bundle.class);

        new PagingFhirResultIterator<ListResource>(referenceQuery.execute(), ListResource.class, client)
                .forEachRemaining(uniqueResults::add);

        new PagingFhirResultIterator<ListResource>(listQuery.execute(), ListResource.class, client)
                .forEachRemaining(uniqueResults::add);

        uniqueResults.forEach(ref -> {
            processAssociations(ref, ref.getEntry(), uuidsToDelete, builder);
        });

        uniqueResults.forEach(ref -> {
            boolean toDeleteTransaction = addToDeleteTransaction(ref, uuidsToDelete, builder);
            if (toDeleteTransaction && !ref.getEntry().isEmpty()) {
                errorInfo.add(new ErrorInfo(ErrorCode.REFERENCE_EXISTS_EXCEPTION, "Some references still exists to " + ref.getId(),
                        Severity.ERROR, null, null));
            } else if (!toDeleteTransaction && ref.getEntry().isEmpty()
                    && ref.getMeta().hasProfile(MappingSupport.MHD_COMPREHENSIVE_SUBMISSIONSET_PROFILE)) {
                errorInfo.add(new ErrorInfo(ErrorCode.UNREFERENCED_OBJECT_EXCEPTION,
                        "SubmissionSet without references not permitted " + ref.getId(), Severity.ERROR, null, null));
            }
        });

        if (!uuidsToDelete.isEmpty()) {
            errorInfo.add(new ErrorInfo(ErrorCode.UNRESOLVED_REFERENCE_EXCEPTION,
                    "Some references can not be resolved " + String.join(",", uuidsToDelete), Severity.ERROR, null, null));
        }
        final Response response;
        if (!errorInfo.isEmpty()) {
            response = new Response(Status.FAILURE);
            response.setErrors(errorInfo);
        } else {
            client.transaction().withBundle(builder.getBundle()).execute();
            response = new Response(Status.SUCCESS);
        }

        return response;
    }

    private void validateNoReferencesExists(DocumentReference doc,
            PagingFhirResultIterator<DocumentReference> docResult, ArrayList<String> uuidsToDelete, List<ErrorInfo> errorInfo) {
        var listOfRelations = new ArrayList<DocumentReferenceRelatesToComponent>();
        docResult.forEachRemaining(aDoc -> {
            var entryUuid = StoredQueryMapper.entryUuidFrom(aDoc);
            if (!uuidsToDelete.contains(entryUuid) && aDoc != doc)
                listOfRelations.addAll(aDoc.getRelatesTo());
        });
        doc.getRelatesTo().stream().filter(docRel -> docRel.getCode().equals(DocumentRelationshipType.REPLACES)).forEach(relDoc -> {
            var entryUuid = StoredQueryMapper.entryUuidFrom(relDoc.getTarget().getResource());
            if (!uuidsToDelete.contains(entryUuid)){
                errorInfo.add(new ErrorInfo(ErrorCode.REFERENCE_EXISTS_EXCEPTION, "Some references still exists to " + entryUuid,
                        Severity.ERROR, null, null));
            }
        });
        listOfRelations.stream().map(DocumentReferenceRelatesToComponent::getTarget).filter(Objects::nonNull)
                .map(Reference::getResource).filter(DocumentReference.class::isInstance)
                .map(DocumentReference.class::cast)
                .filter(docRef -> docRef.equalsDeep(doc))
                .findAny().ifPresent(res -> errorInfo.add(new ErrorInfo(ErrorCode.REFERENCE_EXISTS_EXCEPTION, "Some references still exists to " + res.getId(),
                        Severity.ERROR, null, null)));
    }

    /**
     * Remove metadata will also remove associations between registry entries. This method will ensure that these
     * entries are correctly removed.
     *
     * @param resource
     * @param associatedObjects
     * @param uuidsToDelete
     * @param builder
     */
    private void processAssociations(IAnyResource resource, List<? extends IBaseElement> associatedObjects,
            List<String> uuidsToDelete, BundleBuilder builder) {
        final var deletedElements = new ArrayList<IBaseElement>();
        if (associatedObjects.stream().filter(Objects::nonNull).filter(rel -> uuidsToDelete.contains(rel.getId())).map(rel -> {
            uuidsToDelete.remove(rel.getId());
            deletedElements.add(rel);
            var entryUuid = StoredQueryMapper.entryUuidFrom(resource);
            return !uuidsToDelete.contains(entryUuid);
        }).filter(updateRequired -> updateRequired == true).count() > 0)
            builder.addTransactionUpdateEntry(resource);
        associatedObjects.removeAll(deletedElements);
    }

    private boolean addToDeleteTransaction(IAnyResource resource, List<String> uuidsToDelete, BundleBuilder builder) {
        var entryUuid = StoredQueryMapper.entryUuidFrom(resource);
        if (uuidsToDelete.contains(entryUuid)) {
            builder.addTransactionDeleteEntry(resource);
            uuidsToDelete.remove(entryUuid);
            return true;
        }
        return false;
    }

}
