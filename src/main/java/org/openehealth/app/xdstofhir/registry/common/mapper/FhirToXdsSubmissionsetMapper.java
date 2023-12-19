package org.openehealth.app.xdstofhir.registry.common.mapper;

import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.urnDecodedScheme;

import java.util.function.Function;

import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Identifier;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdSubmissionSet;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AvailabilityStatus;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.LocalizedString;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.SubmissionSet;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FhirToXdsSubmissionsetMapper extends AbstractFhirToXdsMapper
        implements Function<MhdSubmissionSet, SubmissionSet> {
    @Override
    public SubmissionSet apply(MhdSubmissionSet mhdSubmission) {
        if (mhdSubmission.getIdentifier().isEmpty())
            return null;
        var patientId = obtainIndexPatientId(mhdSubmission.getSubject());
        if (patientId == null)
            return null;
        var submissionSet = new SubmissionSet();
        submissionSet.setPatientId(patientId);
        submissionSet.setUniqueId(urnDecodedScheme(mhdSubmission.getIdentifier().stream()
                .filter(id -> Identifier.IdentifierUse.USUAL.equals(id.getUse())).findFirst().get().getValue()));
        submissionSet.setEntryUuid(bestQualifiedIdentified(mhdSubmission.getIdentifier()).getId());
        submissionSet.setAvailabilityStatus(AvailabilityStatus.APPROVED);
        submissionSet.setSourceId(MappingSupport.urnDecodedScheme(mhdSubmission.getSourceId().getValue()));
        submissionSet.setSubmissionTime(fromDateTime(mhdSubmission.getDateElement()));
        submissionSet.setContentTypeCode(fromCode(mhdSubmission.getDesignationType().getCodingFirstRep()));
        submissionSet.setAuthor(fromAuthor(mhdSubmission.getSource()));
        if (mhdSubmission.getTitle() != null)
            submissionSet.setTitle(new LocalizedString(mhdSubmission.getTitle()));
        if (!mhdSubmission.getNote().isEmpty())
            submissionSet.setComments(new LocalizedString(mhdSubmission.getNoteFirstRep().getText()));
        return submissionSet;
    }

}
