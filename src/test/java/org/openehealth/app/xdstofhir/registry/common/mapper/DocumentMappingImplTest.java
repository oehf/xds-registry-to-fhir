package org.openehealth.app.xdstofhir.registry.common.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import java.io.IOException;
import java.util.function.Function;

import org.apache.groovy.util.Maps;
import org.hl7.fhir.r4.model.DocumentReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openehealth.app.xdstofhir.registry.common.RegistryConfiguration;
import org.openehealth.ipf.commons.ihe.xds.core.SampleData;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssigningAuthority;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.DocumentEntry;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Identifiable;
import org.openehealth.ipf.commons.spring.map.SpringBidiMappingService;
import org.openehealth.ipf.commons.xml.XmlUtils;
import org.springframework.core.io.ClassPathResource;

class DocumentMappingImplTest {
    private Function<DocumentEntry, DocumentReference> xdsToFire;
    private Function<DocumentReference, DocumentEntry> fireToXds;

    @BeforeEach
    public void setupTestClass() {
        RegistryConfiguration config = new RegistryConfiguration();
        config.setDefaultHash("0000000000000000000000000000000000000000");
        config.setRepositoryEndpoint(Maps.of("1.2.3.4", "http://my.doc.retrieve/binary/$documentUniqueId"));
        config.setUnknownRepositoryId("2.999.1.2.3");
        var mapService = new SpringBidiMappingService();
        mapService.setMappingResource(new ClassPathResource("META-INF/map/fhir-hl7v2-translation.map"));
        xdsToFire = new XdsToFhirDocumentMapper(config, mapService);
        fireToXds = new FhirToXdsDocumentMapper(config, mapService);
    }

    @Test
    void verifyBirectionalMapping() throws JAXBException {
        DocumentEntry testDocument = SampleData
                .createDocumentEntry(new Identifiable("123", new AssigningAuthority("2.999.3.4")));
        testDocument.setUri(null); //no useful mapping for uri
        verifyFhirXdsMapping(testDocument);
    }


    @Test
    void minimalDocumentMapping() throws JAXBException, IOException {
        DocumentEntry minimalDoc = (DocumentEntry) JAXBContext.newInstance(DocumentEntry.class).createUnmarshaller()
                .unmarshal(new ClassPathResource("messages/minimal-doc.xml").getFile());
        verifyFhirXdsMapping(minimalDoc);
    }

    private void verifyFhirXdsMapping(DocumentEntry testDocument) throws JAXBException {
        DocumentReference mappedFhirDocument = xdsToFire.apply(testDocument);
        DocumentEntry reverseMappedDocument = fireToXds.apply(mappedFhirDocument);
        String transformedDoc = XmlUtils.renderJaxb(JAXBContext.newInstance(DocumentEntry.class), reverseMappedDocument,
                true);
        String originalDoc = XmlUtils.renderJaxb(JAXBContext.newInstance(DocumentEntry.class), testDocument,
                true);
        assertEquals(originalDoc, transformedDoc);
    }

}
