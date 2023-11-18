package org.openehealth.app.xdstofhir.registry.query;

import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.OID_URN;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.URI_URN;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.toUrnCoded;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.DateClientParam;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import com.google.common.collect.Lists;
import lombok.Getter;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.codesystems.DocumentReferenceStatus;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
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

public class StoredQueryVistorImpl implements Visitor {
    @Getter
    private IQuery<Bundle> fhirQuery;
    private final IGenericClient client;

    public StoredQueryVistorImpl(IGenericClient client) {
        this.client = client;
    }

    @Override
    public void visit(FindDocumentsQuery query) {
        this.fhirQuery = client.search().forResource(DocumentReference.class)
                .withProfile(MappingSupport.MHD_COMPREHENSIVE_PROFILE)
                .include(DocumentReference.INCLUDE_SUBJECT)
                .returnBundle(Bundle.class);
        mapPatientIdToQuery(query);

        map(query.getClassCodes(), DocumentReference.CATEGORY);
        map(query.getTypeCodes(),DocumentReference.TYPE);
        map(query.getPracticeSettingCodes(),DocumentReference.SETTING);
        map(query.getHealthcareFacilityTypeCodes(),DocumentReference.FACILITY);
        map(query.getFormatCodes(),DocumentReference.FORMAT);
        map(query.getStatus());
        map(query.getEventCodes(), DocumentReference.EVENT);
        map(query.getConfidentialityCodes(), DocumentReference.SECURITY_LABEL);
        map(query.getCreationTime(), DocumentReference.DATE);
        map(query.getServiceStartTime(), DocumentReference.PERIOD);
        map(query.getServiceStopTime(), DocumentReference.PERIOD);
        //TODO: author
    }

    private void mapPatientIdToQuery(PatientIdBasedStoredQuery query) {
        var patientId = query.getPatientId();

        var identifier = DocumentReference.PATIENT
                .hasChainedProperty(Patient.IDENTIFIER.exactly().systemAndIdentifier(
                        OID_URN + patientId.getAssigningAuthority().getUniversalId(), patientId.getId()));
        fhirQuery.where(identifier);
    }

    @Override
    public void visit(GetDocumentsQuery query) {
        var searchIdentifiers = new ArrayList<String>();
        if (query.getUniqueIds() != null) {
            searchIdentifiers.addAll(query.getUniqueIds());
        }
        if (query.getUuids() != null) {
            searchIdentifiers.addAll(query.getUuids());
        }
        var identifier = DocumentReference.IDENTIFIER.exactly().systemAndValues(URI_URN,
                searchIdentifiers.stream().map(MappingSupport::toUrnCoded).collect(Collectors.toList()));
        fhirQuery.where(identifier);
    }


    private void map(TimeRange dateRange, DateClientParam date) {
        if (dateRange != null) {
            if (dateRange.getFrom() != null) {
                fhirQuery.where(date.afterOrEquals().millis(Date.from(dateRange.getFrom().getDateTime().toInstant())));
            }
            if (dateRange.getTo() != null) {
                fhirQuery.where(date.before().millis(Date.from(dateRange.getTo().getDateTime().toInstant())));
            }
        }
    }

    private void map(List<AvailabilityStatus> status) {
        List<String> fhirStatus = status.stream()
                .map(MappingSupport.STATUS_MAPPING_FROM_XDS::get)
                .filter(Objects::nonNull)
                .map(DocumentReferenceStatus::toCode)
                .collect(Collectors.toList());
        if (!fhirStatus.isEmpty())
            fhirQuery.where(DocumentReference.STATUS.exactly().codes(fhirStatus));
    }

    private void map (QueryList<Code> codes, TokenClientParam param) {
        if (codes != null)
            codes.getOuterList().forEach(eventList -> map(eventList, param));
    }

    private void map(List<Code> codes, TokenClientParam param) {
        if (codes != null && !codes.isEmpty()) {
            fhirQuery.where(param.exactly()
                    .codings(codes.stream()
                            .map(xdsCode -> new Coding(toUrnCoded(xdsCode.getSchemeName()), xdsCode.getCode(), null))
                            .collect(Collectors.toList()).toArray(new Coding[0])));
        }
    }

    @Override
    public void visit(FindDocumentsForMultiplePatientsQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(FindFoldersQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(FindFoldersForMultiplePatientsQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(GetSubmissionSetsQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(GetSubmissionSetAndContentsQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(GetRelatedDocumentsQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(GetFoldersQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(GetFoldersForDocumentQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(GetFolderAndContentsQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
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
        // TODO: Need to find another solution, since Hapi do not yet support Fhir's multi resource query
        // https://github.com/hapifhir/hapi-fhir/issues/685
        this.fhirQuery = client.search().forAllResources()
                .withAnyProfile(Lists.newArrayList(MappingSupport.MHD_COMPREHENSIVE_SUBMISSIONSET_PROFILE,
                        MappingSupport.MHD_COMPREHENSIVE_PROFILE))
                .where(new TokenClientParam(Constants.PARAM_TYPE).exactly().codes("Patient","List"))
                .include(DocumentReference.INCLUDE_SUBJECT)
                .include(MhdSubmissionSet.INCLUDE_SUBJECT)
                .returnBundle(Bundle.class);
        mapPatientIdToQuery(query);
    }

    @Override
    public void visit(FindSubmissionSetsQuery query) {
        this.fhirQuery = client.search().forResource(MhdSubmissionSet.class)
                .withProfile(MappingSupport.MHD_COMPREHENSIVE_SUBMISSIONSET_PROFILE)
                .where(MhdSubmissionSet.CODE.exactly()
                        .codings(MhdSubmissionSet.SUBMISSIONSET_CODEING.getCodingFirstRep()))
                .include(MhdSubmissionSet.INCLUDE_SUBJECT)
                .returnBundle(Bundle.class);
        mapPatientIdToQuery(query);
    }

    @Override
    public void visit(FindDocumentsByReferenceIdQuery query) {
        visit((FindDocumentsQuery)query);
        // TODO: Not yet working as expected
        if (query.getReferenceIds() != null){
            query.getTypedReferenceIds().getOuterList().stream().forEach(refId -> mapRefId(refId));
        }
    }

    private void mapRefId(List<ReferenceId> refId) {
        List<String> searchParam = refId.stream().map(this::asSearchToken).collect(Collectors.toList());
        fhirQuery.where(DocumentReference.RELATED.hasAnyOfIds(searchParam));
    }

    private String asSearchToken(ReferenceId id) {
        if (id.getAssigningAuthority() != null) {
            return OID_URN + id.getAssigningAuthority().getUniversalId() + "|" + id.getId();
        } else {
            return id.getId();
        }
    }

    @Override
    public void visit(FetchQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(FindMedicationTreatmentPlansQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(FindPrescriptionsQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(FindDispensesQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(FindMedicationAdministrationsQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(FindPrescriptionsForValidationQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(FindPrescriptionsForDispenseQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(FindMedicationListQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(FindDocumentsByTitleQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

}
