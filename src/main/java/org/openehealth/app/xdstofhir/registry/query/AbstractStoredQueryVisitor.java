package org.openehealth.app.xdstofhir.registry.query;

import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FetchQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindDispensesQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindDocumentsByTitleQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindDocumentsForMultiplePatientsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindFoldersForMultiplePatientsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindMedicationAdministrationsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindMedicationListQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindMedicationTreatmentPlansQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindPrescriptionsForDispenseQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindPrescriptionsForValidationQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.FindPrescriptionsQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.SubscriptionForDocumentEntryQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.SubscriptionForFolderQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.SubscriptionForPatientIndependentDocumentEntryQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.SubscriptionForPatientIndependentSubmissionSetQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.SubscriptionForSubmissionSetQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.Query.Visitor;

/**
 * Supportive abstract class to ignore certain StoredQueries that are not part of
 * ITI-18, but requested by the Visitor interface.
 */
public abstract class AbstractStoredQueryVisitor implements Visitor {
    //===========================================================================
    //Queries not part of ITI-18 and not yet implemented
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


    @Override
    public void visit(SubscriptionForDocumentEntryQuery arg0) {
        throw new UnsupportedOperationException("DSUB query not yet supported");
    }


    @Override
    public void visit(SubscriptionForFolderQuery arg0) {
        throw new UnsupportedOperationException("DSUB query not yet supported");
    }


    @Override
    public void visit(SubscriptionForPatientIndependentDocumentEntryQuery arg0) {
        throw new UnsupportedOperationException("DSUB query not yet supported");
    }


    @Override
    public void visit(SubscriptionForSubmissionSetQuery arg0) {
        throw new UnsupportedOperationException("DSUB query not yet supported");
    }


    @Override
    public void visit(SubscriptionForPatientIndependentSubmissionSetQuery arg0) {
        throw new UnsupportedOperationException("DSUB query not yet supported");
    }
}
