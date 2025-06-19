package org.openehealth.app.xdstofhir.registry.common.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.hl7.fhir.r4.model.ListResource.ListEntryComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdFolder;
import org.openehealth.ipf.commons.ihe.xds.core.SampleData;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssigningAuthority;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Folder;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Identifiable;
import org.openehealth.ipf.commons.spring.map.SpringBidiMappingService;
import org.openehealth.ipf.commons.xml.XmlUtils;
import org.springframework.core.io.ClassPathResource;

class FolderMappingImplTest {
    private BiFunction<Folder, List<ListEntryComponent>, MhdFolder> xdsToFire;
    private Function<MhdFolder, Folder> fireToXds;

    @BeforeEach
    public void setupTestClass() {
        var mapService = new SpringBidiMappingService();
        mapService.setMappingResource(new ClassPathResource("META-INF/map/fhir-hl7v2-translation.map"));
        mapService.setMappingResource(new ClassPathResource("META-INF/map/codesystem-fhir-translation.map"));
        var xds2Fhir = new XdsToFhirFolderMapper();
        xds2Fhir.setFhirMapping(mapService);
        var fhir2xds = new FhirToXdsFolderMapper();
        fhir2xds.setFhirMapping(mapService);
        xdsToFire = xds2Fhir;
        fireToXds = fhir2xds;
    }

    @Test
    void verifyBirectionalMapping() throws JAXBException {
        Folder testFolder = SampleData
                .createFolder(new Identifiable("123", new AssigningAuthority("2.999.3.4")));
        verifyFhirXdsMapping(testFolder);
    }

    private void verifyFhirXdsMapping(Folder testFolder) throws JAXBException {
        MhdFolder mappedFhirFolder = xdsToFire.apply(testFolder, Collections.emptyList());
        Folder reverseMappedFolder = fireToXds.apply(mappedFhirFolder);
        //ignore lastUpdated field
        reverseMappedFolder.setLastUpdateTime(testFolder.getLastUpdateTime());
        String transformedFolder = XmlUtils.renderJaxb(JAXBContext.newInstance(Folder.class), reverseMappedFolder,
                true);
        String originalFolder = XmlUtils.renderJaxb(JAXBContext.newInstance(Folder.class), testFolder,
                true);
        assertEquals(originalFolder, transformedFolder);
    }

}
