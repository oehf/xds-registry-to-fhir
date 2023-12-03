package org.openehealth.app.xdstofhir.registry.common.mapper;

import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.Identifier;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdFolder;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Folder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class XdsToFhirFolderMapper extends AbstractXdsToFhirMapper
        implements Function<Folder, MhdFolder> {
    @Override
    public MhdFolder apply(Folder xdFolder) {
        var mhdList = new MhdFolder();
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
        return mhdList;
    }

}
