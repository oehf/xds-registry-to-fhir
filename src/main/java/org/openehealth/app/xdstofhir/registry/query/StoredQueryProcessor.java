package org.openehealth.app.xdstofhir.registry.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.ListResource;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdFolder;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdSubmissionSet;
import org.openehealth.ipf.commons.core.URN;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Association;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssociationLabel;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssociationType;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.DocumentEntry;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Folder;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.ObjectReference;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.SubmissionSet;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Version;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.XDSMetaClass;
import org.openehealth.ipf.commons.ihe.xds.core.requests.QueryRegistry;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.QueryReturnType;
import org.openehealth.ipf.commons.ihe.xds.core.responses.ErrorCode;
import org.openehealth.ipf.commons.ihe.xds.core.responses.ErrorInfo;
import org.openehealth.ipf.commons.ihe.xds.core.responses.QueryResponse;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Severity;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Status;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoredQueryProcessor implements Iti18Service {
    @Value("${xds.query.max.results:1000}")
    private int maxResultCount;
    private final IGenericClient client;
    private final Function<DocumentReference, DocumentEntry> documentMapper;
    private final Function<MhdSubmissionSet, SubmissionSet> submissionMapper;
    private final Function<MhdFolder, Folder> folderMapper;
    private static final Version DEFAULT_VERSION = new Version("1");

    @Override
    public QueryResponse processQuery(QueryRegistry query) {
        var visitor = new StoredQueryVistorImpl(client);
        query.getQuery().accept(visitor);

        var response = new QueryResponse(Status.SUCCESS);

        var fhirDocuments = mapDocuments(response, visitor.getDocumentResult());
        var fhirSubmissions = mapSubmissionSets(response, visitor.getSubmissionSetResult());
        var fhirFolder = mapFolder(response, visitor.getFolderResult());
        response.getAssociations().addAll(createAssociationsFrom(fhirSubmissions, fhirDocuments));
        response.getAssociations().addAll(createAssociationsFrom(fhirSubmissions, fhirFolder));
        Collection<Association> fdDocAssoc = createAssociationsFrom(fhirFolder, fhirDocuments);
        response.getAssociations().addAll(fdDocAssoc);
        response.getAssociations().addAll(createAssociationsFrom(fhirSubmissions, fdDocAssoc));
        response.getAssociations().addAll(createAssociationsBetween(fhirDocuments));

        if (query.getReturnType().equals(QueryReturnType.OBJECT_REF)) {
            response.setReferences(Stream
                    .concat(Stream.concat(response.getDocumentEntries().stream(),
                            response.getSubmissionSets().stream()), response.getFolders().stream())
                    .map(xdsObject -> new ObjectReference(xdsObject.getEntryUuid())).collect(Collectors.toList()));
            response.getDocumentEntries().clear();
            response.getSubmissionSets().clear();
            response.getFolders().clear();
        }

        return response;
    }

    private Collection<? extends Association> createAssociationsBetween(List<DocumentReference> fhirDocuments) {
        var xdsAssocations = new ArrayList<Association>();
        for (var doc : fhirDocuments) {
            for (var related : doc.getRelatesTo()) {
                for (var doc2 : fhirDocuments) {
                    if (related.getTarget().hasReference() && doc2.getId().contains(related.getTarget().getReference())) {
                        String assocEntryUuid = related.getId() != null ? related.getId()
                                : new URN(UUID.randomUUID()).toString();
                        AssociationType type = MappingSupport.DOC_DOC_XDS_ASSOCIATIONS.get(related.getCode());
                        Association submissionAssociation = new Association(type,
                                assocEntryUuid, entryUuidFrom(doc), entryUuidFrom(doc2));
                        xdsAssocations.add(submissionAssociation);
                    }
                }
            }
        }
        return xdsAssocations;
    }

    private Collection<? extends Association> createAssociationsFrom(List<MhdSubmissionSet> fhirSubmissions,
            Collection<Association> fdDocAssoc) {
        var xdsAssocations = new ArrayList<Association>();
        for (var list : fhirSubmissions) {
            for (var entry : list.getEntry()) {
                for (var assoc : fdDocAssoc) {
                    if (assoc.getEntryUuid().equals(entry.getItem().getIdentifier().getValue())) {
                        var targetId = assoc.getEntryUuid();
                        var sourceId = entryUuidFrom(list);
                        if (targetId != null && sourceId != null) {
                            String assocEntryUuid = entry.getId() != null ? entry.getId()
                                    : new URN(UUID.randomUUID()).toString();
                            Association submissionAssociation = new Association(AssociationType.HAS_MEMBER,
                                    assocEntryUuid, sourceId, targetId);
                            submissionAssociation.setLabel(AssociationLabel.ORIGINAL);
                            xdsAssocations.add(submissionAssociation);
                        }
                    }
                }
            }
        }
        return xdsAssocations;
    }

    private Collection<Association> createAssociationsFrom(List<? extends ListResource> lists,
            List<? extends DomainResource> fhirDocuments) {
        var xdsAssocations = new ArrayList<Association>();
        for (var list : lists) {
            for (var entry : list.getEntry()) {
                for (var doc : fhirDocuments) {
                    if (entry.getItem().hasReference() &&
                            doc.getId().contains(entry.getItem().getReference())) {
                        var targetId = entryUuidFrom(doc);
                        var sourceId = entryUuidFrom(list);
                        if (targetId != null && sourceId != null) {
                            String assocEntryUuid = entry.getId() != null ? entry.getId()
                                    : new URN(UUID.randomUUID()).toString();
                            Association submissionAssociation = new Association(AssociationType.HAS_MEMBER,
                                    assocEntryUuid, sourceId, targetId);
                            submissionAssociation.setLabel(AssociationLabel.ORIGINAL);
                            xdsAssocations.add(submissionAssociation);
                        }
                    }
                }
            }
        }
        return xdsAssocations;
    }

    private List<MhdFolder>  mapFolder(QueryResponse response, Iterable<MhdFolder> fhirFolder) {
        var processedFhirFolders = new ArrayList<MhdFolder>();
        for (var folder : fhirFolder) {
            if (evaluateMaxCount(response)) {
                break;
            }
            var xdsFolder = folderMapper.apply(folder);
            if (xdsFolder != null) {
                assignDefaultVersioning().accept(xdsFolder);
                response.getFolders().add(xdsFolder);
                processedFhirFolders.add(folder);
            }
        }
        return processedFhirFolders;
    }

    private List<MhdSubmissionSet> mapSubmissionSets(QueryResponse response, Iterable<MhdSubmissionSet> fhirSubmissions) {
        var processedFhirSubmissions = new ArrayList<MhdSubmissionSet>();
        for (var submissionset : fhirSubmissions) {
            if (evaluateMaxCount(response)) {
                break;
            }
            var xdsSubmission = submissionMapper.apply(submissionset);
            if (xdsSubmission != null) {
                assignDefaultVersioning().accept(xdsSubmission);
                response.getSubmissionSets().add(xdsSubmission);
                processedFhirSubmissions.add(submissionset);
            }
        }
        return processedFhirSubmissions;
    }

    private List<DocumentReference> mapDocuments(QueryResponse response, Iterable<DocumentReference> fhirDocuments) {
        var processedFhirDocs = new ArrayList<DocumentReference>();
        for (var document : fhirDocuments) {
            if (evaluateMaxCount(response)) {
                break;
            }
            var xdsDoc = documentMapper.apply(document);
            if (xdsDoc != null) {
                assignDefaultVersioning().accept(xdsDoc);
                response.getDocumentEntries().add(xdsDoc);
                processedFhirDocs.add(document);
            }
        }
        return processedFhirDocs;
    }

    private String entryUuidFrom(IBaseResource resource) {
        List<Identifier> identifier;
        if (resource instanceof DocumentReference) {
            identifier = ((DocumentReference) resource).getIdentifier();
        } else if (resource instanceof ListResource) {
            identifier = ((ListResource) resource).getIdentifier();
        } else {
            return null;
        }
        return identifier.stream().filter(id -> Identifier.IdentifierUse.OFFICIAL.equals(id.getUse())).findFirst()
                .orElse(identifier.stream().findFirst().orElse(new Identifier())).getValue();
    }

    private boolean evaluateMaxCount(QueryResponse response) {
        int currentResourceCount = response.getDocumentEntries().size() + response.getSubmissionSets().size() + response.getFolders().size();
        if (currentResourceCount > maxResultCount) {
            response.setStatus(Status.PARTIAL_SUCCESS);
            response.setErrors(Collections.singletonList(new ErrorInfo(ErrorCode.TOO_MANY_RESULTS,
                    "Result exceed maximum of " + maxResultCount, Severity.WARNING, null, null)));
            return true;
        }
        return false;
    }

    /**
     * ebRIM chapter 2.5.1 requires versionInfo and lid to be set.
     *
     * @return consumer setting proper defaults for lid and versionInfo
     */
    private Consumer<? super XDSMetaClass> assignDefaultVersioning() {
        return meta -> {
            meta.setLogicalUuid(meta.getEntryUuid());
            meta.setVersion(DEFAULT_VERSION);
        };
    }

}
