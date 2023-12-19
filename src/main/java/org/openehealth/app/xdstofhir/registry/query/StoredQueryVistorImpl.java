package org.openehealth.app.xdstofhir.registry.query;

import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.OID_URN;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.URI_URN;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.toUrnCoded;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.DateClientParam;
import ca.uhn.fhir.rest.gclient.ICriterion;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.util.BundleUtil;
import lombok.Getter;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.codesystems.DocumentReferenceStatus;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdFolder;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdSubmissionSet;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AvailabilityStatus;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Code;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.ReferenceId;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.TimeRange;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FetchQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindDispensesQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindDocumentsByReferenceIdQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindDocumentsByTitleQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindDocumentsForMultiplePatientsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindDocumentsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindFoldersForMultiplePatientsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindFoldersQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindMedicationAdministrationsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindMedicationListQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindMedicationTreatmentPlansQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindPrescriptionsForDispenseQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindPrescriptionsForValidationQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindPrescriptionsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindSubmissionSetsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetAllQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetAssociationsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetByIdAndCodesQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetByIdQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetDocumentsAndAssociationsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetDocumentsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetFolderAndContentsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetFoldersForDocumentQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetFoldersQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetRelatedDocumentsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetSubmissionSetAndContentsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetSubmissionSetsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.PatientIdBasedStoredQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.Query.Visitor;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.QueryList;

/**
 * Implement ITI-18 queries using IPF visitor pattern.
 *
 * The XDS queries will be mapped to a FHIR query.
 *
 *
 */
public class StoredQueryVistorImpl implements Visitor {
    /*
     * Hapi currently ignore "_list" parameter, workaround here with "_has" reverse chain search
     * https://github.com/hapifhir/hapi-fhir/issues/3761
     */
    private static final String _HAS_LIST_ITEM_IDENTIFIER = "_has:List:item:identifier";
    @Getter
    private Iterable<DocumentReference> documentResult;
    @Getter
    private Iterable<MhdSubmissionSet> submissionSetResult;
    @Getter
    private Iterable<MhdFolder> folderResult;

    private final IGenericClient client;

    public StoredQueryVistorImpl (IGenericClient client) {
        this.client = client;
        submissionSetResult = () -> Collections.emptyIterator();
        documentResult = () -> Collections.emptyIterator();
        folderResult = () -> Collections.emptyIterator();
    }

    @Override
    public void visit(FindDocumentsQuery query) {
        IQuery<Bundle> documentFhirQuery = prepareQuery(query);
        buildResultForDocuments(documentFhirQuery);
    }


    @Override
    public void visit(GetDocumentsQuery query) {
        IQuery<Bundle> documentFhirQuery = initDocumentQuery();
        var identifier = buildIdentifierQuery(query, DocumentReference.IDENTIFIER);
        documentFhirQuery.where(identifier);

        buildResultForDocuments(documentFhirQuery);
    }


    @Override
    public void visit(FindFoldersQuery query) {
        IQuery<Bundle> folderFhirQuery = initFolderQuery();
        mapPatientIdToQuery(query, folderFhirQuery);
        buildResultForFolder(folderFhirQuery);
    }

    @Override
    public void visit(GetSubmissionSetsQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(GetSubmissionSetAndContentsQuery query) {
        IQuery<Bundle> submissionSetfhirQuery = initSubmissionSetQuery();
        IQuery<Bundle> documentFhirQuery = initDocumentQuery();
        map(query.getFormatCodes(),DocumentReference.FORMAT, documentFhirQuery);
        map(query.getConfidentialityCodes(), DocumentReference.SECURITY_LABEL, documentFhirQuery);
        IQuery<Bundle> folderFhirQuery = initFolderQuery();
        List<String> searchIdentifiers = urnIdentifierList(query);
        submissionSetfhirQuery.where(MhdFolder.IDENTIFIER.exactly().systemAndValues(URI_URN, searchIdentifiers));
        var reverseSearchCriteria = Collections.singletonMap(_HAS_LIST_ITEM_IDENTIFIER, searchIdentifiers);
        documentFhirQuery.whereMap(reverseSearchCriteria);
        folderFhirQuery.whereMap(reverseSearchCriteria);

        buildResultForDocuments(documentFhirQuery);
        buildResultForSubmissionSet(submissionSetfhirQuery);
        buildResultForFolder(folderFhirQuery);
    }

    @Override
    public void visit(GetRelatedDocumentsQuery query) {
        IQuery<Bundle> documentFhirQuery = initDocumentQuery();
        documentFhirQuery.include(DocumentReference.INCLUDE_RELATESTO);
        String identifier = MappingSupport.toUrnCoded(Objects.requireNonNullElse(query.getUniqueId(), query.getUuid()));
        documentFhirQuery.where(DocumentReference.IDENTIFIER.exactly().systemAndValues(URI_URN, identifier));
        documentFhirQuery.where(DocumentReference.RELATESTO.isMissing(false));
        buildResultForDocuments(documentFhirQuery);
        List<DocumentReference> results = new ArrayList<>();
        populateDocumentTo(results);
        documentFhirQuery = initDocumentQuery();
        documentFhirQuery.include(DocumentReference.INCLUDE_RELATESTO);
        documentFhirQuery.where(DocumentReference.RELATESTO
                .hasChainedProperty(DocumentReference.IDENTIFIER.exactly().systemAndValues(URI_URN, identifier)));
        documentFhirQuery.where(DocumentReference.RELATESTO.isMissing(false));
        buildResultForDocuments(documentFhirQuery);
        populateDocumentTo(results);
        documentResult = () -> results.iterator();
    }

    @Override
    public void visit(GetFoldersQuery query) {
        IQuery<Bundle> folderFhirQuery = initFolderQuery();
        var identifier = buildIdentifierQuery(query, MhdFolder.IDENTIFIER);
        folderFhirQuery.where(identifier);

        buildResultForFolder(folderFhirQuery);
    }

    @Override
    public void visit(GetFoldersForDocumentQuery query) {
        IQuery<Bundle> folderFhirQuery = initFolderQuery();

        String identifier = MappingSupport.toUrnCoded(Objects.requireNonNullElse(query.getUniqueId(), query.getUuid()));
        folderFhirQuery.where(MhdFolder.ITEM.hasChainedProperty(DocumentReference.IDENTIFIER.exactly().systemAndValues(URI_URN, identifier)));

        buildResultForFolder(folderFhirQuery);
    }

    @Override
    public void visit(GetFolderAndContentsQuery query) {
        IQuery<Bundle> documentFhirQuery = initDocumentQuery();
        map(query.getFormatCodes(),DocumentReference.FORMAT, documentFhirQuery);
        map(query.getConfidentialityCodes(), DocumentReference.SECURITY_LABEL, documentFhirQuery);
        IQuery<Bundle> folderFhirQuery = initFolderQuery();
        List<String> searchIdentifiers = urnIdentifierList(query);
        folderFhirQuery.where(MhdFolder.IDENTIFIER.exactly().systemAndValues(URI_URN, searchIdentifiers));
        var reverseSearchCriteria = Collections.singletonMap(_HAS_LIST_ITEM_IDENTIFIER, searchIdentifiers);
        documentFhirQuery.whereMap(reverseSearchCriteria);

        buildResultForDocuments(documentFhirQuery);
        buildResultForFolder(folderFhirQuery);
    }

    @Override
    public void visit(GetDocumentsAndAssociationsQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(GetAssociationsQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(GetAllQuery query) {
        IQuery<Bundle> documentFhirQuery = initDocumentQuery();
        IQuery<Bundle> submissionSetfhirQuery = initSubmissionSetQuery();
        IQuery<Bundle> folderFhirQuery = initFolderQuery();

        mapPatientIdToQuery(query, documentFhirQuery);
        mapPatientIdToQuery(query, submissionSetfhirQuery);
        mapPatientIdToQuery(query, folderFhirQuery);

        buildResultForDocuments(documentFhirQuery);
        buildResultForSubmissionSet(submissionSetfhirQuery);
        buildResultForFolder(folderFhirQuery);
    }

    @Override
    public void visit(FindSubmissionSetsQuery query) {
        IQuery<Bundle> submissionSetfhirQuery = initSubmissionSetQuery();
        mapPatientIdToQuery(query, submissionSetfhirQuery);
        buildResultForSubmissionSet(submissionSetfhirQuery);
    }

    @Override
    public void visit(FindDocumentsByReferenceIdQuery query) {
        IQuery<Bundle> documentFhirQuery = prepareQuery(query);
        // TODO: Not yet working as expected
        if (query.getReferenceIds() != null){
            var searchToken = query.getTypedReferenceIds().getOuterList().stream()
                    .flatMap(List::stream)
                    .map(this::asSearchToken)
                    .collect(Collectors.toList());
            if (!searchToken.isEmpty()) {
                documentFhirQuery.where(DocumentReference.RELATED.hasAnyOfIds(searchToken));
            }
        }
        buildResultForDocuments(documentFhirQuery);
    }

    private void populateDocumentTo(List<DocumentReference> results) {
        for (var result : documentResult) {
            results.add(result);
        }
    }

    private void buildResultForFolder(IQuery<Bundle> folderFhirQuery) {
        folderResult =  () -> new PagingFhirResultIterator<MhdFolder>(folderFhirQuery.execute(), MhdFolder.class);
    }

    private String asSearchToken(ReferenceId id) {
        if (id.getAssigningAuthority() != null) {
            return OID_URN + id.getAssigningAuthority().getUniversalId() + "|" + id.getId();
        } else {
            return id.getId();
        }
    }

    private void buildResultForDocuments(IQuery<Bundle> documentFhirQuery) {
        documentResult =  () -> new PagingFhirResultIterator<DocumentReference>(documentFhirQuery.execute(), DocumentReference.class);
    }

    private void buildResultForSubmissionSet(IQuery<Bundle> submissionSetfhirQuery) {
        submissionSetResult = () -> new PagingFhirResultIterator<MhdSubmissionSet>(submissionSetfhirQuery.execute(), MhdSubmissionSet.class);
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

    private List<String> urnIdentifierList(GetByIdAndCodesQuery query) {
        List<String> searchIdentifiers = new ArrayList<String>();
        if (query.getUniqueId() != null) {
            searchIdentifiers.add(query.getUniqueId());
        }
        if (query.getUuid() != null) {
            searchIdentifiers.add(query.getUuid());
        }
        searchIdentifiers = searchIdentifiers.stream().map(MappingSupport::toUrnCoded).collect(Collectors.toList());
        return searchIdentifiers;
    }

    private ICriterion<?> buildIdentifierQuery(GetByIdQuery query, TokenClientParam param) {
        var searchIdentifiers = new ArrayList<String>();
        if (query.getUniqueIds() != null) {
            searchIdentifiers.addAll(query.getUniqueIds());
        }
        if (query.getUuids() != null) {
            searchIdentifiers.addAll(query.getUuids());
        }
        var identifier = param.exactly().systemAndValues(URI_URN,
                searchIdentifiers.stream().map(MappingSupport::toUrnCoded).collect(Collectors.toList()));
        return identifier;
    }


    private void mapPatientIdToQuery(PatientIdBasedStoredQuery query, IQuery<Bundle> fhirQuery) {
        var patientId = query.getPatientId();

        var identifier = DocumentReference.PATIENT
                .hasChainedProperty(Patient.IDENTIFIER.exactly().systemAndIdentifier(
                        OID_URN + patientId.getAssigningAuthority().getUniversalId(), patientId.getId()));
        fhirQuery.where(identifier);
    }

    private IQuery<Bundle> initSubmissionSetQuery() {
        return client.search().forResource(MhdSubmissionSet.class)
                .withProfile(MappingSupport.MHD_COMPREHENSIVE_SUBMISSIONSET_PROFILE)
                .where(MhdSubmissionSet.CODE.exactly()
                        .codings(MhdSubmissionSet.SUBMISSIONSET_CODEING.getCodingFirstRep()))
                .include(MhdSubmissionSet.INCLUDE_SUBJECT)
                .returnBundle(Bundle.class);
    }

    private IQuery<Bundle> initFolderQuery() {
        return client.search().forResource(MhdFolder.class)
                .withProfile(MappingSupport.MHD_COMPREHENSIVE_FOLDER_PROFILE)
                .where(MhdFolder.CODE.exactly()
                        .codings(MhdFolder.FOLDER_CODEING.getCodingFirstRep()))
                .include(MhdFolder.INCLUDE_SUBJECT)
                .returnBundle(Bundle.class);
    }

    private IQuery<Bundle> initDocumentQuery() {
        return client.search().forResource(DocumentReference.class)
                .withProfile(MappingSupport.MHD_COMPREHENSIVE_PROFILE)
                .include(DocumentReference.INCLUDE_SUBJECT)
                .returnBundle(Bundle.class);
    }


    private void map(TimeRange dateRange, DateClientParam date, IQuery<Bundle> fhirQuery) {
        if (dateRange != null) {
            if (dateRange.getFrom() != null) {
                fhirQuery.where(date.afterOrEquals().millis(Date.from(dateRange.getFrom().getDateTime().toInstant())));
            }
            if (dateRange.getTo() != null) {
                fhirQuery.where(date.before().millis(Date.from(dateRange.getTo().getDateTime().toInstant())));
            }
        }
    }

    private void map(List<AvailabilityStatus> status, IQuery<Bundle> fhirQuery) {
        List<String> fhirStatus = status.stream()
                .map(MappingSupport.STATUS_MAPPING_FROM_XDS::get)
                .filter(Objects::nonNull)
                .map(DocumentReferenceStatus::toCode)
                .collect(Collectors.toList());
        if (!fhirStatus.isEmpty())
            fhirQuery.where(DocumentReference.STATUS.exactly().codes(fhirStatus));
    }

    private void map (QueryList<Code> codes, TokenClientParam param, IQuery<Bundle> fhirQuery) {
        if (codes != null)
            codes.getOuterList().forEach(eventList -> map(eventList, param, fhirQuery));
    }

    private void map(List<Code> codes, TokenClientParam param, IQuery<Bundle> fhirQuery) {
        if (codes != null && !codes.isEmpty()) {
            fhirQuery.where(param.exactly()
                    .codings(codes.stream()
                            .map(xdsCode -> new Coding(toUrnCoded(xdsCode.getSchemeName()), xdsCode.getCode(), null))
                            .collect(Collectors.toList()).toArray(new Coding[0])));
        }
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

    //===========================================================================
    //Queries below are not part of ITI-18 and not yet implemented
    //===========================================================================

    @Override
    public void visit(FindDocumentsForMultiplePatientsQuery query) {
        throw new UnsupportedOperationException("ITI-51 not yet supported");
    }

    @Override
    public void visit(FindFoldersForMultiplePatientsQuery query) {
        throw new UnsupportedOperationException("Not yet supported");
    }

    @Override
    public void visit(FetchQuery query) {
        throw new UnsupportedOperationException("ITI-63 not yet supported");
    }

    @Override
    public void visit(FindMedicationTreatmentPlansQuery query) {
        throw new UnsupportedOperationException("Not yet supported");
    }

    @Override
    public void visit(FindPrescriptionsQuery query) {
        throw new UnsupportedOperationException("Not yet supported");
    }

    @Override
    public void visit(FindDispensesQuery query) {
        throw new UnsupportedOperationException("Not yet supported");
    }

    @Override
    public void visit(FindMedicationAdministrationsQuery query) {
        throw new UnsupportedOperationException("Not yet supported");
    }

    @Override
    public void visit(FindPrescriptionsForValidationQuery query) {
        throw new UnsupportedOperationException("Not yet supported");
    }

    @Override
    public void visit(FindPrescriptionsForDispenseQuery query) {
        throw new UnsupportedOperationException("Not yet supported");
    }

    @Override
    public void visit(FindMedicationListQuery query) {
        throw new UnsupportedOperationException("Not yet supported");
    }

    @Override
    public void visit(FindDocumentsByTitleQuery query) {
        throw new UnsupportedOperationException("Gematik ePA query not yet supported");
    }

}
