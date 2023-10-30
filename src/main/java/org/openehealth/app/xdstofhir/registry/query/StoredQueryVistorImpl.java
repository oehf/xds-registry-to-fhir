package org.openehealth.app.xdstofhir.registry.query;

import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.OID_URN;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.URI_URN;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.toUrnCoded;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import lombok.Getter;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Patient;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Code;
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
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.Query.Visitor;

public class StoredQueryVistorImpl implements Visitor {
    @Getter
    private final IQuery<Bundle> fhirQuery;

    public StoredQueryVistorImpl(IGenericClient client) {
        this.fhirQuery = client.search().forResource(DocumentReference.class)
                .withProfile(MappingSupport.MHD_COMPREHENSIVE_PROFILE)
                .include(DocumentReference.INCLUDE_SUBJECT)
                .returnBundle(Bundle.class);
    }

    @Override
    public void visit(FindDocumentsQuery query) {
        var patientId = query.getPatientId();

        var identifier = DocumentReference.PATIENT
                .hasChainedProperty(Patient.IDENTIFIER.exactly().systemAndIdentifier(
                        OID_URN + patientId.getAssigningAuthority().getUniversalId(), patientId.getId()));
        fhirQuery.where(identifier);

        map(query.getClassCodes(), DocumentReference.CATEGORY);
        map(query.getTypeCodes(),DocumentReference.TYPE);
        map(query.getPracticeSettingCodes(),DocumentReference.SETTING);
        map(query.getHealthcareFacilityTypeCodes(),DocumentReference.FACILITY);
        map(query.getFormatCodes(),DocumentReference.FORMAT);
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

    private void map(List<Code> codes, TokenClientParam param) {
        if (codes != null) {
            codes.forEach(code -> {
                var codeCriteria = param.exactly().systemAndCode(toUrnCoded(code.getSchemeName()), code.getCode());
                fhirQuery.where(codeCriteria);
            });
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
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(FindSubmissionSetsQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void visit(FindDocumentsByReferenceIdQuery query) {
        throw new UnsupportedOperationException("Not yet implemented");
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
