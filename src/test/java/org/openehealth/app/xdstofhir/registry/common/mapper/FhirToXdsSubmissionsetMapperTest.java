package org.openehealth.app.xdstofhir.registry.common.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdSubmissionSet;
import org.openehealth.ipf.commons.ihe.xds.core.SampleData;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssigningAuthority;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Identifiable;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.SubmissionSet;
import org.openehealth.ipf.commons.xml.XmlUtils;

class FhirToXdsSubmissionsetMapperTest {
    private BiFunction<SubmissionSet, List<Reference>, MhdSubmissionSet> xdsToFire;
    private Function<MhdSubmissionSet, SubmissionSet> fireToXds;

    @BeforeEach
    public void setupTestClass() {
        xdsToFire = new XdsToFhirSubmissionsetMapper();
        fireToXds = new FhirToXdsSubmissionsetMapper();
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
