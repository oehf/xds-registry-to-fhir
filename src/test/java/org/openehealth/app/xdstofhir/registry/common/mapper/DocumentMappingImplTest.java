package org.openehealth.app.xdstofhir.registry.common.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.groovy.util.Maps;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.DocumentReference.DocumentReferenceRelatesToComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openehealth.app.xdstofhir.registry.common.RegistryConfiguration;
import org.openehealth.ipf.commons.ihe.xds.core.SampleData;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssigningAuthority;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.DocumentEntry;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Identifiable;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Organization;
import org.openehealth.ipf.commons.ihe.xds.core.validate.XDSMetaDataException;
import org.openehealth.ipf.commons.spring.map.SpringBidiMappingService;
import org.openehealth.ipf.commons.xml.XmlUtils;
import org.springframework.core.io.ClassPathResource;

class DocumentMappingImplTest {
    private BiFunction<DocumentEntry, List<DocumentReferenceRelatesToComponent>, DocumentReference> xdsToFire;
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
    void verifyXcnWithNoIdentifierShallBeMapped() throws JAXBException {
        DocumentEntry testDocument = SampleData
                .createDocumentEntry(new Identifiable("123", new AssigningAuthority("2.999.3.4")));
        testDocument.setUri(null);
        testDocument.getLegalAuthenticator().setId(null);

        verifyFhirXdsMapping(testDocument);
    }

    @Test
    void verifyXcnWithNoNameShallBeMapped() throws JAXBException {
        DocumentEntry testDocument = SampleData
                .createDocumentEntry(new Identifiable("123", new AssigningAuthority("2.999.3.4")));
        testDocument.setUri(null);
        testDocument.getLegalAuthenticator().setName(null);

        verifyFhirXdsMapping(testDocument);
    }

    @Test
    void verifyXonWithNoAssigningAuthorityShallBeMapped() throws JAXBException {
        DocumentEntry testDocument = SampleData
                .createDocumentEntry(new Identifiable("123", new AssigningAuthority("2.999.3.4")));
        testDocument.setUri(null);
        Organization organization = testDocument.getAuthors().getFirst().getAuthorInstitution().getFirst();
        organization.setIdNumber("1.2.3");
        organization.setAssigningAuthority(null);

        verifyFhirXdsMapping(testDocument);
    }

    @Test
    void verifyXonWithNonOidIdentifierAndNoAssigningAuthorityShallNotBeAccepted() {
        DocumentEntry testDocument = SampleData
                .createDocumentEntry(new Identifiable("123", new AssigningAuthority("2.999.3.4")));
        testDocument.setUri(null);
        Organization organization = testDocument.getAuthors().getFirst().getAuthorInstitution().getFirst();
        organization.setIdNumber("112");
        organization.setAssigningAuthority(null);

        assertThrowsExactly(XDSMetaDataException.class, () -> verifyFhirXdsMapping(testDocument));
    }

    @Test
    void minimalDocumentMapping() throws JAXBException, IOException {
        DocumentEntry minimalDoc = (DocumentEntry) JAXBContext.newInstance(DocumentEntry.class).createUnmarshaller()
                .unmarshal(new ClassPathResource("messages/minimal-doc.xml").getFile());
        verifyFhirXdsMapping(minimalDoc);
    }

    private void verifyFhirXdsMapping(DocumentEntry testDocument) throws JAXBException {
        DocumentReference mappedFhirDocument = xdsToFire.apply(testDocument, Collections.emptyList());
        DocumentEntry reverseMappedDocument = fireToXds.apply(mappedFhirDocument);
        String transformedDoc = XmlUtils.renderJaxb(JAXBContext.newInstance(DocumentEntry.class), reverseMappedDocument,
                true);
        String originalDoc = XmlUtils.renderJaxb(JAXBContext.newInstance(DocumentEntry.class), testDocument,
                true);
        assertEquals(originalDoc, transformedDoc);
    }

}
