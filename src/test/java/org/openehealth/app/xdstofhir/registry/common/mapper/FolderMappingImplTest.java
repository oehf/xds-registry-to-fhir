package org.openehealth.app.xdstofhir.registry.common.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdFolder;
import org.openehealth.ipf.commons.ihe.xds.core.SampleData;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssigningAuthority;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Folder;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Identifiable;
import org.openehealth.ipf.commons.xml.XmlUtils;

class FolderMappingImplTest {
    private Function<Folder, MhdFolder> xdsToFire;
    private Function<MhdFolder, Folder> fireToXds;

    @BeforeEach
    public void setupTestClass() {
        xdsToFire = new XdsToFhirFolderMapper();
        fireToXds = new FhirToXdsFolderMapper();
    }

    @Test
    void verifyBirectionalMapping() throws JAXBException {
        Folder testFolder = SampleData
                .createFolder(new Identifiable("123", new AssigningAuthority("2.999.3.4")));
        verifyFhirXdsMapping(testFolder);
    }

    private void verifyFhirXdsMapping(Folder testFolder) throws JAXBException {
        MhdFolder mappedFhirFolder = xdsToFire.apply(testFolder);
        Folder reverseMappedFolder = fireToXds.apply(mappedFhirFolder);
        String transformedFolder = XmlUtils.renderJaxb(JAXBContext.newInstance(Folder.class), reverseMappedFolder,
                true);
        String originalFolder = XmlUtils.renderJaxb(JAXBContext.newInstance(Folder.class), testFolder,
                true);
        assertEquals(originalFolder, transformedFolder);
    }

}
