package org.openehealth.app.xdstofhir.registry.common.mapper;

import static java.util.Objects.requireNonNullElse;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.urnDecodedScheme;

import java.util.Date;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdFolder;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AvailabilityStatus;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Folder;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.LocalizedString;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FhirToXdsFolderMapper extends AbstractFhirToXdsMapper
        implements Function<MhdFolder, Folder> {
    @Override
    public Folder apply(MhdFolder mhdFolder) {
        if (mhdFolder.getIdentifier().isEmpty())
            return null;
        var patientId = obtainIndexPatientId(mhdFolder.getSubject());
        if (patientId == null)
            return null;
        var folder = new Folder();
        folder.setPatientId(patientId);
        folder.setUniqueId(urnDecodedScheme(mhdFolder.getIdentifier().stream()
                .filter(id -> Identifier.IdentifierUse.USUAL.equals(id.getUse())).findFirst().get().getValue()));
        folder.setEntryUuid(bestQualifiedIdentified(mhdFolder.getIdentifier()).getId());
        folder.setAvailabilityStatus(AvailabilityStatus.APPROVED);
        var dateElement = requireNonNullElse(mhdFolder.getDateElement(), new DateTimeType(new Date()));
        folder.setLastUpdateTime(fromDateTime(dateElement));
        folder.getCodeList().addAll(mhdFolder.getDesignationType().stream()
                .map(CodeableConcept::getCodingFirstRep)
                .map(this::fromCode)
                .collect(Collectors.toList()));
        if (mhdFolder.getTitle() != null)
            folder.setTitle(new LocalizedString(mhdFolder.getTitle()));
        if (!mhdFolder.getNote().isEmpty())
            folder.setComments(new LocalizedString(mhdFolder.getNoteFirstRep().getText()));
        return folder;
    }

}
