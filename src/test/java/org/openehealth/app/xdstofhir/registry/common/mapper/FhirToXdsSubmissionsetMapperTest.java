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
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdSubmissionSet;
import org.openehealth.ipf.commons.ihe.xds.core.SampleData;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssigningAuthority;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Identifiable;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.SubmissionSet;
import org.openehealth.ipf.commons.spring.map.SpringBidiMappingService;
import org.openehealth.ipf.commons.xml.XmlUtils;
import org.springframework.core.io.ClassPathResource;

class FhirToXdsSubmissionsetMapperTest {
    private BiFunction<SubmissionSet, List<ListEntryComponent>, MhdSubmissionSet> xdsToFire;
    private Function<MhdSubmissionSet, SubmissionSet> fireToXds;

    @BeforeEach
    public void setupTestClass() {
        var mapService = new SpringBidiMappingService();
        mapService.setMappingResource(new ClassPathResource("META-INF/map/fhir-hl7v2-translation.map"));
        mapService.setMappingResource(new ClassPathResource("META-INF/map/codesystem-fhir-translation.map"));
        var xds2Fhir = new XdsToFhirSubmissionsetMapper();
        xds2Fhir.setFhirMapping(mapService);
        var fhir2xds = new FhirToXdsSubmissionsetMapper();
        fhir2xds.setFhirMapping(mapService);
        xdsToFire = xds2Fhir;
        fireToXds = fhir2xds;
    }

    @Test
    void verifyBirectionalMapping() throws JAXBException {
        SubmissionSet testSubmissionSet = SampleData
                .createSubmissionSet(new Identifiable("123", new AssigningAuthority("2.999.3.4")));
        testSubmissionSet.setHomeCommunityId(null); // ignore for now
        testSubmissionSet.getIntendedRecipients().clear(); // ignore now
        verifyFhirXdsMapping(testSubmissionSet);
    }

    private void verifyFhirXdsMapping(SubmissionSet testSubmission) throws JAXBException {
        MhdSubmissionSet mappedFhirSubmission = xdsToFire.apply(testSubmission, Collections.emptyList());
        SubmissionSet reverseMappedSubmission = fireToXds.apply(mappedFhirSubmission);
        String transformedSubmission = XmlUtils.renderJaxb(JAXBContext.newInstance(SubmissionSet.class),
                reverseMappedSubmission, true);
        String originalSubmission = XmlUtils.renderJaxb(JAXBContext.newInstance(SubmissionSet.class), testSubmission,
                true);
        assertEquals(originalSubmission, transformedSubmission);
    }

}
