package org.openehealth.app.xdstofhir.registry.query;

import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.URI_URN;
import static org.openehealth.app.xdstofhir.registry.query.StoredQueryMapper.assignDefaultVersioning;
import static org.openehealth.app.xdstofhir.registry.query.StoredQueryMapper.buildIdentifierQuery;
import static org.openehealth.app.xdstofhir.registry.query.StoredQueryMapper.entryUuidFrom;
import static org.openehealth.app.xdstofhir.registry.query.StoredQueryMapper.map;
import static org.openehealth.app.xdstofhir.registry.query.StoredQueryMapper.mapPatientIdToQuery;
import static org.openehealth.app.xdstofhir.registry.query.StoredQueryMapper.urnIdentifierList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.StreamSupport;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.util.BundleUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.ListResource;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdFolder;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdSubmissionSet;
import org.openehealth.ipf.commons.core.URN;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Association;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssociationLabel;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssociationType;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.ObjectReference;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindDocumentsByReferenceIdQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindDocumentsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindFoldersQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindSubmissionSetsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetAllQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetAssociationsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetDocumentsAndAssociationsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetDocumentsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetFolderAndContentsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetFoldersForDocumentQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetFoldersQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetRelatedDocumentsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetSubmissionSetAndContentsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetSubmissionSetsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.responses.ErrorCode;
import org.openehealth.ipf.commons.ihe.xds.core.responses.ErrorInfo;
import org.openehealth.ipf.commons.ihe.xds.core.responses.QueryResponse;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Severity;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Status;

/**
 * Implement ITI-18 queries using IPF visitor pattern.
 *
 * The XDS queries will be mapped to a FHIR query.
 *
 *
 */
@RequiredArgsConstructor
public class StoredQueryVistorImpl extends AbstractStoredQueryVisitor {
    /*
     * Hapi currently ignore "_list" parameter, workaround here with "_has" reverse chain search
     * https://github.com/hapifhir/hapi-fhir/issues/3761
     */
    private static final String _HAS_LIST_ITEM_IDENTIFIER = "_has:List:item:identifier";


    private final IGenericClient client;
    private final StoredQueryProcessor queryProcessor;
    private final boolean isObjectRefResult;

    @Getter
    private final QueryResponse response = new QueryResponse(Status.SUCCESS);

    @Override
    public void visit(FindDocumentsQuery query) {
        IQuery<Bundle> documentFhirQuery = prepareQuery(query);
        mapDocuments(buildResultForDocuments(documentFhirQuery));
    }


    @Override
    public void visit(GetDocumentsQuery query) {
        var documentFhirQuery = initDocumentQuery();
        var identifier = buildIdentifierQuery(query, DocumentReference.IDENTIFIER);
        documentFhirQuery.where(identifier);
        mapDocuments(buildResultForDocuments(documentFhirQuery));
    }


    @Override
    public void visit(FindFoldersQuery query) {
        var folderFhirQuery = initFolderQuery();
        mapPatientIdToQuery(query, folderFhirQuery);
        mapFolders(buildResultForFolder(folderFhirQuery));
    }

    @Override
    public void visit(GetSubmissionSetsQuery query) {
        var submissionSetfhirQuery = initSubmissionSetQuery();
        var searchIdentifiers = query.getUuids().stream().map(MappingSupport::toUrnCoded).toList();
        submissionSetfhirQuery.where(ListResource.ITEM.hasChainedProperty(
                new TokenClientParam("identifier").exactly().systemAndValues(URI_URN, searchIdentifiers)));
        buildResultForSubmissionSet(submissionSetfhirQuery);
        var mapSubmissionSets = mapSubmissionSets(buildResultForSubmissionSet(submissionSetfhirQuery));

        var documentFhirQuery = initDocumentQuery();
        documentFhirQuery.where(DocumentReference.IDENTIFIER.exactly().systemAndValues(URI_URN, searchIdentifiers));
        var folderFhirQuery = initFolderQuery();
        folderFhirQuery.where(DocumentReference.IDENTIFIER.exactly().systemAndValues(URI_URN, searchIdentifiers));

        mapAssociations(createAssociationsFrom(mapSubmissionSets, StreamSupport
                .stream(buildResultForDocuments(documentFhirQuery).spliterator(), false).toList()));
        mapAssociations(createAssociationsFrom(mapSubmissionSets, StreamSupport
                .stream(buildResultForFolder(folderFhirQuery).spliterator(), false).toList()));
    }



    @Override
    public void visit(GetSubmissionSetAndContentsQuery query) {
        var submissionSetfhirQuery = initSubmissionSetQuery();
        var documentFhirQuery = initDocumentQuery();
        map(query.getFormatCodes(),DocumentReference.FORMAT, documentFhirQuery);
        map(query.getConfidentialityCodes(), DocumentReference.SECURITY_LABEL, documentFhirQuery);
        var folderFhirQuery = initFolderQuery();
        var searchIdentifiers = urnIdentifierList(query);
        submissionSetfhirQuery.where(ListResource.IDENTIFIER.exactly().systemAndValues(URI_URN, searchIdentifiers));
        var reverseSearchCriteria = Collections.singletonMap(_HAS_LIST_ITEM_IDENTIFIER, searchIdentifiers);
        documentFhirQuery.whereMap(reverseSearchCriteria);
        folderFhirQuery.whereMap(reverseSearchCriteria);

        var fhirDocuments = mapDocuments(buildResultForDocuments(documentFhirQuery));
        var fhirFolder = mapFolders(buildResultForFolder(folderFhirQuery));
        var fhirSubmissions = mapSubmissionSets(buildResultForSubmissionSet(submissionSetfhirQuery));
        mapAssociations(createAssociationsFrom(fhirSubmissions, fhirDocuments));
        mapAssociations(createAssociationsFrom(fhirSubmissions, fhirFolder));
        var fdDocAssoc = createAssociationsFrom(fhirFolder, fhirDocuments);
        mapAssociations(fdDocAssoc);
        mapAssociations(createAssociationsFrom(fhirSubmissions, fdDocAssoc));
    }

    @Override
    public void visit(GetRelatedDocumentsQuery query) {
        var documentFhirQuery = initDocumentQuery();
        documentFhirQuery.include(DocumentReference.INCLUDE_RELATESTO);
        var identifier = MappingSupport.toUrnCoded(Objects.requireNonNullElse(query.getUniqueId(), query.getUuid()));
        documentFhirQuery.where(DocumentReference.IDENTIFIER.exactly().systemAndValues(URI_URN, identifier));
        documentFhirQuery.where(DocumentReference.RELATESTO.isMissing(false));
        var resultIterator = buildResultForDocuments(documentFhirQuery);
        List<DocumentReference> results = new ArrayList<>();
        resultIterator.iterator().forEachRemaining(results::add);
        documentFhirQuery = initDocumentQuery();
        documentFhirQuery.include(DocumentReference.INCLUDE_RELATESTO);
        documentFhirQuery.where(DocumentReference.RELATESTO
                .hasChainedProperty(DocumentReference.IDENTIFIER.exactly().systemAndValues(URI_URN, identifier)));
        documentFhirQuery.where(DocumentReference.RELATESTO.isMissing(false));
        resultIterator = buildResultForDocuments(documentFhirQuery);
        resultIterator.iterator().forEachRemaining(results::add);
        mapDocuments(results);
        mapAssociations(createAssociationsBetween(results));
    }

    @Override
    public void visit(GetFoldersQuery query) {
        var folderFhirQuery = initFolderQuery();
        var identifier = buildIdentifierQuery(query, ListResource.IDENTIFIER);
        folderFhirQuery.where(identifier);
        mapFolders(buildResultForFolder(folderFhirQuery));
    }

    @Override
    public void visit(GetFoldersForDocumentQuery query) {
        var folderFhirQuery = initFolderQuery();
        var identifier = MappingSupport.toUrnCoded(Objects.requireNonNullElse(query.getUniqueId(), query.getUuid()));
        folderFhirQuery.where(ListResource.ITEM.hasChainedProperty(DocumentReference.IDENTIFIER.exactly().systemAndValues(URI_URN, identifier)));
        mapFolders(buildResultForFolder(folderFhirQuery));
    }

    @Override
    public void visit(GetFolderAndContentsQuery query) {
        var documentFhirQuery = initDocumentQuery();
        map(query.getFormatCodes(),DocumentReference.FORMAT, documentFhirQuery);
        map(query.getConfidentialityCodes(), DocumentReference.SECURITY_LABEL, documentFhirQuery);
        var folderFhirQuery = initFolderQuery();
        var searchIdentifiers = urnIdentifierList(query);
        folderFhirQuery.where(ListResource.IDENTIFIER.exactly().systemAndValues(URI_URN, searchIdentifiers));
        var reverseSearchCriteria = Collections.singletonMap(_HAS_LIST_ITEM_IDENTIFIER, searchIdentifiers);
        documentFhirQuery.whereMap(reverseSearchCriteria);
        var fhirDocuments = mapDocuments(buildResultForDocuments(documentFhirQuery));
        var fhirFolder = mapFolders(buildResultForFolder(folderFhirQuery));
        mapAssociations(createAssociationsFrom(fhirFolder, fhirDocuments));
    }

    @Override
    public void visit(GetDocumentsAndAssociationsQuery query) {
        var documentFhirQuery = initDocumentQuery();
        var identifier = buildIdentifierQuery(query, DocumentReference.IDENTIFIER);
        documentFhirQuery.where(identifier);
        var fhirDocuments = mapDocuments(buildResultForDocuments(documentFhirQuery));

        var folderFhirQuery = initFolderQuery();
        folderFhirQuery.where(ListResource.ITEM.hasChainedProperty(identifier));
        List<MhdFolder> folders = new ArrayList<>();
        buildResultForFolder(folderFhirQuery).iterator().forEachRemaining(folders::add);

        var submissionSetfhirQuery = initSubmissionSetQuery();
        submissionSetfhirQuery.where(ListResource.ITEM.hasChainedProperty(identifier));
        List<MhdSubmissionSet> submissionSets = new ArrayList<>();
        buildResultForSubmissionSet(submissionSetfhirQuery).iterator().forEachRemaining(submissionSets::add);

        mapAssociations(createAssociationsFrom(folders, fhirDocuments));
        mapAssociations(createAssociationsFrom(submissionSets, fhirDocuments));
        mapAssociations(createAssociationsBetween(fhirDocuments));
    }

    @Override
    public void visit(GetAssociationsQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(GetAllQuery query) {
        var documentFhirQuery = initDocumentQuery();
        var submissionSetfhirQuery = initSubmissionSetQuery();
        var folderFhirQuery = initFolderQuery();
        mapPatientIdToQuery(query, documentFhirQuery);
        mapPatientIdToQuery(query, submissionSetfhirQuery);
        mapPatientIdToQuery(query, folderFhirQuery);
        var fhirDocuments = mapDocuments(buildResultForDocuments(documentFhirQuery));
        var fhirSubmissions = mapSubmissionSets(buildResultForSubmissionSet(submissionSetfhirQuery));
        var fhirFolder = mapFolders(buildResultForFolder(folderFhirQuery));
        mapAssociations(createAssociationsFrom(fhirSubmissions, fhirDocuments));
        mapAssociations(createAssociationsFrom(fhirSubmissions, fhirFolder));
        var fdDocAssoc = createAssociationsFrom(fhirFolder, fhirDocuments);
        mapAssociations(fdDocAssoc);
        mapAssociations(createAssociationsFrom(fhirSubmissions, fdDocAssoc));
        mapAssociations(createAssociationsBetween(fhirDocuments));
    }

    @Override
    public void visit(FindSubmissionSetsQuery query) {
        var submissionSetfhirQuery = initSubmissionSetQuery();
        mapPatientIdToQuery(query, submissionSetfhirQuery);
        if (query.getSourceIds() != null && !query.getSourceIds().isEmpty())
            submissionSetfhirQuery.where(new TokenClientParam("sourceId").exactly().codes(query.getSourceIds()));
        mapSubmissionSets(buildResultForSubmissionSet(submissionSetfhirQuery));
    }

    @Override
    public void visit(FindDocumentsByReferenceIdQuery query) {
        var documentFhirQuery = prepareQuery(query);
        // TODO: Not yet working as expected
        if (query.getReferenceIds() != null){
            var searchToken = query.getTypedReferenceIds().getOuterList().stream()
                    .flatMap(List::stream)
                    .map(StoredQueryMapper::asSearchToken)
                    .toList();
            if (!searchToken.isEmpty()) {
                documentFhirQuery.where(DocumentReference.RELATED.hasAnyOfIds(searchToken));
            }
        }
        mapDocuments(buildResultForDocuments(documentFhirQuery));
    }


    private Iterable<MhdFolder> buildResultForFolder(IQuery<Bundle> folderFhirQuery) {
        return () -> new PagingFhirResultIterator<MhdFolder>(folderFhirQuery.execute(), MhdFolder.class);
    }

    private Iterable<DocumentReference> buildResultForDocuments(IQuery<Bundle> documentFhirQuery) {
        return () -> new PagingFhirResultIterator<DocumentReference>(documentFhirQuery.execute(),
                DocumentReference.class);
    }

    private Iterable<MhdSubmissionSet> buildResultForSubmissionSet(IQuery<Bundle> submissionSetfhirQuery) {
        return () -> new PagingFhirResultIterator<MhdSubmissionSet>(submissionSetfhirQuery.execute(),
                MhdSubmissionSet.class);
    }

    private IQuery<Bundle> prepareQuery(FindDocumentsQuery query) {
        IQuery<Bundle> documentFhirQuery = initDocumentQuery();
        mapPatientIdToQuery(query, documentFhirQuery);

        map(query.getClassCodes(), DocumentReference.CATEGORY, documentFhirQuery);
        map(query.getTypeCodes(),DocumentReference.TYPE, documentFhirQuery);
        map(query.getPracticeSettingCodes(),DocumentReference.SETTING, documentFhirQuery);
        map(query.getHealthcareFacilityTypeCodes(),DocumentReference.FACILITY, documentFhirQuery);
        map(query.getFormatCodes(),DocumentReference.FORMAT, documentFhirQuery);
        map(query.getStatus(), documentFhirQuery);
        map(query.getEventCodes(), DocumentReference.EVENT, documentFhirQuery);
        map(query.getConfidentialityCodes(), DocumentReference.SECURITY_LABEL, documentFhirQuery);
        map(query.getCreationTime(), DocumentReference.DATE, documentFhirQuery);
        map(query.getServiceStartTime(), DocumentReference.PERIOD, documentFhirQuery);
        map(query.getServiceStopTime(), DocumentReference.PERIOD, documentFhirQuery);
        return documentFhirQuery;
    }


    private IQuery<Bundle> initSubmissionSetQuery() {
        return client.search().forResource(MhdSubmissionSet.class)
                .withProfile(MappingSupport.MHD_COMPREHENSIVE_SUBMISSIONSET_PROFILE)
                .where(ListResource.CODE.exactly()
                        .codings(MhdSubmissionSet.SUBMISSIONSET_CODEING.getCodingFirstRep()))
                .include(ListResource.INCLUDE_SUBJECT)
                .returnBundle(Bundle.class);
    }

    private IQuery<Bundle> initFolderQuery() {
        return client.search().forResource(MhdFolder.class)
                .withProfile(MappingSupport.MHD_COMPREHENSIVE_FOLDER_PROFILE)
                .where(ListResource.CODE.exactly()
                        .codings(MhdFolder.FOLDER_CODEING.getCodingFirstRep()))
                .include(ListResource.INCLUDE_SUBJECT)
                .returnBundle(Bundle.class);
    }

    private IQuery<Bundle> initDocumentQuery() {
        return client.search().forResource(DocumentReference.class)
                .withProfile(MappingSupport.MHD_COMPREHENSIVE_PROFILE)
                .include(DocumentReference.INCLUDE_SUBJECT)
                .returnBundle(Bundle.class);
    }


    /**
     * Lazy Fhir Page Iterator. Fetches the next result page when the iterator has loaded the last element.
     *
     * @param <T>
     */
    public class PagingFhirResultIterator<T extends DomainResource> implements Iterator<T> {

        private Bundle resultBundle;
        private final Class<T> resultTypeClass;
        private int currentIteratorIndex = 0;

        public PagingFhirResultIterator(Bundle resultBundle, Class<T> resultTypeClass) {
            this.resultBundle = resultBundle;
            this.resultTypeClass = resultTypeClass;
        }

        @Override
        public boolean hasNext() {
            if (currentIteratorIndex == getResourcesFromBundle().size()) {
                nextPageIfAvailable();
            }
            return currentIteratorIndex < getResourcesFromBundle().size();
        }

        private void nextPageIfAvailable() {
            if (resultBundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                resultBundle = client.loadPage().next(resultBundle).execute();
                currentIteratorIndex = 0;
            }
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more elements present.");
            }
            T result = getResourcesFromBundle().get(currentIteratorIndex);
            currentIteratorIndex++;
            return result;
        }

        private List<T> getResourcesFromBundle(){
            return BundleUtil.toListOfResourcesOfType(client.getFhirContext(),
                    resultBundle, resultTypeClass);
        }
    }

    private Collection<Association> createAssociationsBetween(List<DocumentReference> fhirDocuments) {
        var xdsAssocations = new ArrayList<Association>();
        for (var doc : fhirDocuments) {
            for (var related : doc.getRelatesTo()) {
                for (var doc2 : fhirDocuments) {
                    if (related.getTarget().hasReference() && doc2.getId().contains(related.getTarget().getReference())) {
                        var assocEntryUuid = related.getId() != null ? related.getId()
                                : new URN(UUID.randomUUID()).toString();
                        var type = MappingSupport.DOC_DOC_XDS_ASSOCIATIONS.get(related.getCode());
                        var submissionAssociation = new Association(type,
                                assocEntryUuid, entryUuidFrom(doc), entryUuidFrom(doc2));
                        xdsAssocations.add(submissionAssociation);
                    }
                }
            }
        }
        return xdsAssocations;
    }

    private Collection<Association> createAssociationsFrom(List<MhdSubmissionSet> fhirSubmissions,
            Collection<Association> fdDocAssoc) {
        var xdsAssocations = new ArrayList<Association>();
        for (var list : fhirSubmissions) {
            for (var entry : list.getEntry()) {
                for (var assoc : fdDocAssoc) {
                    if (assoc.getEntryUuid().equals(entry.getItem().getIdentifier().getValue())) {
                        var targetId = assoc.getEntryUuid();
                        var sourceId = entryUuidFrom(list);
                        if (targetId != null && sourceId != null) {
                            var assocEntryUuid = entry.getId() != null ? entry.getId()
                                    : new URN(UUID.randomUUID()).toString();
                            var submissionAssociation = new Association(AssociationType.HAS_MEMBER,
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
            List<? extends DomainResource> fhirResource) {
        var xdsAssocations = new ArrayList<Association>();
        for (var list : lists) {
            for (var entry : list.getEntry()) {
                for (var doc : fhirResource) {
                    if (entry.getItem().hasReference() &&
                            doc.getId().contains(entry.getItem().getReference())) {
                        var targetId = entryUuidFrom(doc);
                        var sourceId = entryUuidFrom(list);
                        if (targetId != null && sourceId != null) {
                            var assocEntryUuid = entry.getId() != null ? entry.getId()
                                    : new URN(UUID.randomUUID()).toString();
                            var submissionAssociation = new Association(AssociationType.HAS_MEMBER,
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

    private List<MhdFolder>  mapFolders(Iterable<MhdFolder> fhirFolder) {
        var processedFhirFolders = new ArrayList<MhdFolder>();
        for (var folder : fhirFolder) {
            if (evaluateMaxCount(response)) {
                break;
            }
            var xdsFolder = queryProcessor.apply(folder);
            if (xdsFolder != null) {
                assignDefaultVersioning().accept(xdsFolder);
                if (isObjectRefResult)
                    response.getReferences().add(new ObjectReference(xdsFolder.getEntryUuid()));
                else
                    response.getFolders().add(xdsFolder);
                processedFhirFolders.add(folder);
            }
        }
        return processedFhirFolders;
    }

    private void mapAssociations(Collection<Association> associations) {
        if (isObjectRefResult)
            response.getReferences().addAll(associations.stream()
                    .map(assoc -> new ObjectReference(assoc.getEntryUuid())).toList());
        else
            response.getAssociations().addAll(associations);
    }

    private List<MhdSubmissionSet> mapSubmissionSets(Iterable<MhdSubmissionSet> fhirSubmissions) {
        var processedFhirSubmissions = new ArrayList<MhdSubmissionSet>();
        for (var submissionset : fhirSubmissions) {
            if (evaluateMaxCount(response)) {
                break;
            }
            var xdsSubmission = queryProcessor.apply(submissionset);
            if (xdsSubmission != null) {
                assignDefaultVersioning().accept(xdsSubmission);
                if (isObjectRefResult)
                    response.getReferences().add(new ObjectReference(xdsSubmission.getEntryUuid()));
                else
                    response.getSubmissionSets().add(xdsSubmission);
                processedFhirSubmissions.add(submissionset);
            }
        }
        return processedFhirSubmissions;
    }

    private List<DocumentReference> mapDocuments(Iterable<DocumentReference> fhirDocuments) {
        var processedFhirDocs = new ArrayList<DocumentReference>();
        for (var document : fhirDocuments) {
            if (evaluateMaxCount(response)) {
                break;
            }
            var xdsDoc = queryProcessor.apply(document);
            if (xdsDoc != null) {
                assignDefaultVersioning().accept(xdsDoc);
                if (isObjectRefResult)
                    response.getReferences().add(new ObjectReference(xdsDoc.getEntryUuid()));
                else
                    response.getDocumentEntries().add(xdsDoc);
                processedFhirDocs.add(document);
            }
        }
        return processedFhirDocs;
    }


    private boolean evaluateMaxCount(QueryResponse response) {
        int currentResourceCount = response.getDocumentEntries().size() + response.getSubmissionSets().size() + response.getFolders().size();
        if (currentResourceCount > queryProcessor.getMaxResultCount()) {
            response.setStatus(Status.PARTIAL_SUCCESS);
            response.setErrors(Collections.singletonList(new ErrorInfo(ErrorCode.TOO_MANY_RESULTS,
                    "Result exceed maximum of " + queryProcessor.getMaxResultCount(), Severity.WARNING, null, null)));
            return true;
        }
        return false;
    }


}
