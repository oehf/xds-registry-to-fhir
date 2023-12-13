package org.openehealth.app.xdstofhir.registry.common.mapper;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.ListResource.ListEntryComponent;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdFolder;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Folder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class XdsToFhirFolderMapper extends AbstractXdsToFhirMapper
        implements BiFunction<Folder, List<ListEntryComponent>, MhdFolder> {
    @Override
    public MhdFolder apply(Folder xdFolder, List<ListEntryComponent> references) {
        var mhdList = new MhdFolder();
        mhdList.setId(xdFolder.getEntryUuid());
        mhdList.addIdentifier(fromIdentifier(xdFolder.getEntryUuid(), Identifier.IdentifierUse.OFFICIAL));
        mhdList.addIdentifier(
                fromIdentifier(MappingSupport.OID_URN + xdFolder.getUniqueId(), Identifier.IdentifierUse.USUAL));
        mhdList.setSubject(patientReferenceFrom(xdFolder));
        mhdList.setDateElement(fromTimestamp(xdFolder.getLastUpdateTime()));
        mhdList.setDesignationType(xdFolder.getCodeList().stream().map(this::fromCode).collect(Collectors.toList()));
        if (xdFolder.getTitle() != null)
            mhdList.setTitle(xdFolder.getTitle().getValue());
        if (xdFolder.getComments() != null) {
            Annotation annotation = new Annotation();
            annotation.setText(xdFolder.getComments().getValue());
            mhdList.setNote(Collections.singletonList(annotation));
        }
        mhdList.setEntry(references);
        return mhdList;
    }

}
