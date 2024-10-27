package org.openehealth.app.xdstofhir.registry.common.mapper;

import ca.uhn.hl7v2.model.v25.datatype.XCN;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.DocumentReference.DocumentReferenceRelatesToComponent;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Enumerations.DocumentReferenceStatus;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.app.xdstofhir.registry.common.RegistryConfiguration;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Person;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.*;
import org.openehealth.ipf.commons.map.BidiMappingService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiFunction;

import static java.util.Collections.singletonList;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.MHD_COMPREHENSIVE_PROFILE;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.toUrnCoded;
import static org.openehealth.ipf.commons.ihe.xds.core.metadata.AvailabilityStatus.APPROVED;

@Component
@RequiredArgsConstructor
public class XdsToFhirDocumentMapper extends AbstractXdsToFhirMapper
        implements BiFunction<DocumentEntry, List<DocumentReferenceRelatesToComponent>, DocumentReference> {

    private static final String HL7V2FHIR_PATIENT_ADMINISTRATIVE_GENDER = "hl7v2fhir-patient-administrativeGender";
    private final RegistryConfiguration registryConfig;
    private final BidiMappingService hl7v2FhirMapping;

    @Override
    public DocumentReference apply(final DocumentEntry xdsDoc, List<DocumentReferenceRelatesToComponent> documentReferences) {
        var fhirDoc = new DocumentReference();
        fhirDoc.setId(xdsDoc.getEntryUuid());
        fhirDoc.getMeta().setProfile(singletonList(new CanonicalType(MHD_COMPREHENSIVE_PROFILE)));
        fhirDoc.setStatus(xdsDoc.getAvailabilityStatus() == APPROVED ? DocumentReferenceStatus.CURRENT
                : DocumentReferenceStatus.SUPERSEDED);
        fhirDoc.addIdentifier(fromIdentifier(xdsDoc.getEntryUuid(), Identifier.IdentifierUse.OFFICIAL));
        fhirDoc.setMasterIdentifier(fromIdentifier(toUrnCoded(xdsDoc.getUniqueId()), Identifier.IdentifierUse.USUAL));
        fhirDoc.setType(fromCode(xdsDoc.getTypeCode()));
        fhirDoc.addCategory(fromCode(xdsDoc.getClassCode()));
        fhirDoc.getContentFirstRep().setFormat(map(xdsDoc.getFormatCode()));
        fhirDoc.getContext().setPracticeSetting(fromCode(xdsDoc.getPracticeSettingCode()));
        fhirDoc.getContext().setFacilityType(fromCode(xdsDoc.getHealthcareFacilityTypeCode()));
        fhirDoc.setSecurityLabel(
                xdsDoc.getConfidentialityCodes().stream().map(this::fromCode).toList());
        if (xdsDoc.getComments() != null)
            fhirDoc.setDescription(xdsDoc.getComments().getValue());
        fhirDoc.getContext().setEvent(xdsDoc.getEventCodeList().stream().map(this::fromCode).toList());
        var attachment = fhirDoc.getContentFirstRep().getAttachment();
        attachment.setContentType(xdsDoc.getMimeType());
        if (xdsDoc.getTitle() != null)
            attachment.setTitle(xdsDoc.getTitle().getValue());
        attachment.setSize(xdsDoc.getSize().intValue());
        attachment.setHashElement(new Base64BinaryType(xdsDoc.getHash()));
        var fromTimestamp = fromTimestamp(xdsDoc.getCreationTime());
        attachment.setCreationElement(fromTimestamp);
        attachment.setLanguage(xdsDoc.getLanguageCode());
        attachment.setUrl(registryConfig.urlFrom(xdsDoc.getRepositoryUniqueId(), xdsDoc.getUniqueId()));
        fhirDoc.setDate(fromTimestamp.getValue());
        var sourcePatientReference = new Reference();
        sourcePatientReference.setType(Patient.class.getSimpleName());
        sourcePatientReference.setIdentifier(fromIdentifier(xdsDoc.getSourcePatientId()));
        if (xdsDoc.getSourcePatientInfo() != null)
            sourcePatientReference.setResource(fromSourcePatientInfo(xdsDoc.getSourcePatientInfo()));
        fhirDoc.getContext().setSourcePatientInfo(sourcePatientReference);
        fhirDoc.setSubject(patientReferenceFrom(xdsDoc));
        fhirDoc.getContext()
                .setEncounter(xdsDoc.getReferenceIdList().stream()
                        .filter(refId -> ReferenceId.ID_TYPE_ENCOUNTER_ID.equals(refId.getIdTypeCode()))
                        .map(this::mapReferenceId).toList());
        fhirDoc.getContext()
                .setRelated(xdsDoc.getReferenceIdList().stream()
                        .filter(refId -> !ReferenceId.ID_TYPE_ENCOUNTER_ID.equals(refId.getIdTypeCode()))
                        .map(this::mapReferenceId).toList());
        fhirDoc.setAuthor(xdsDoc.getAuthors().stream().map(this::fromAuthor).toList());
        if (xdsDoc.getLegalAuthenticator() != null && !xdsDoc.getLegalAuthenticator().isEmpty()) {
            fhirDoc.setAuthenticator(fromAuthenticator(xdsDoc.getLegalAuthenticator()));
        }
        mapServicePeriod(xdsDoc, fhirDoc);
        fhirDoc.setRelatesTo(documentReferences);
        return fhirDoc;
    }

    private Reference fromAuthenticator(Person xdsAuthenticator) {
        var authenticator = new Reference();
        authenticator.setType(Practitioner.class.getSimpleName());
        var practitioner = new Practitioner();

        // XCN parser already verifies presence of either name or ID
        Name<XCN> name = xdsAuthenticator.getName();
        if (!name.isEmpty()) {
            practitioner.setName(singletonList(fromName(name)));
        }
        Identifiable id = xdsAuthenticator.getId();
        if (!id.isEmpty()) {
            practitioner.addIdentifier(fromIdentifier(id));
        }

        authenticator.setResource(practitioner);
        return authenticator;
    }

    private Patient fromSourcePatientInfo(PatientInfo sourcePatientInfo) {
        var fhirSourcePatient = new Patient();
        fhirSourcePatient.setGender(AdministrativeGender.fromCode((String) hl7v2FhirMapping
                .get(HL7V2FHIR_PATIENT_ADMINISTRATIVE_GENDER, sourcePatientInfo.getGender())));
        sourcePatientInfo.getNames().forEachRemaining(name -> fhirSourcePatient.addName(fromName(name)));
        if (sourcePatientInfo.getDateOfBirth() != null) {
            DateTimeType birthTimeConverted = fromTimestamp(sourcePatientInfo.getDateOfBirth());
            fhirSourcePatient.setBirthDateElement(new DateType(birthTimeConverted.getValue(), birthTimeConverted.getPrecision()));
        }
        sourcePatientInfo.getAddresses().forEachRemaining(address -> fhirSourcePatient.addAddress(fromAddress(address)));
        return fhirSourcePatient;
    }

    private Address fromAddress(org.openehealth.ipf.commons.ihe.xds.core.metadata.Address xdsAddress) {
        var address = new Address();
        address.setCity(xdsAddress.getCity());
        address.setCountry(xdsAddress.getCountry());
        address.setPostalCode(xdsAddress.getZipOrPostalCode());
        address.addLine(xdsAddress.getStreetAddress());
        return address;
    }

    private void mapServicePeriod(final DocumentEntry xdsDoc, final DocumentReference fhirDoc) {
        if (xdsDoc.getServiceStartTime() != null || xdsDoc.getServiceStopTime() != null) {
            var servicePeriod = new Period();
            if (xdsDoc.getServiceStartTime() != null)
                servicePeriod.setStartElement(fromTimestamp(xdsDoc.getServiceStartTime()));
            if (xdsDoc.getServiceStopTime() != null)
                servicePeriod.setEndElement(fromTimestamp(xdsDoc.getServiceStopTime()));
            fhirDoc.getContext().setPeriod(servicePeriod);
        }
    }

    private Reference mapReferenceId(final ReferenceId fhirRef) {
        var reference = new Reference();
        reference.setType(fhirRef.getIdTypeCode());
        var id = new Identifier();
        id.setValue(fhirRef.getId());
        id.setSystem(MappingSupport.OID_URN + fhirRef.getAssigningAuthority().getUniversalId());
        reference.setDisplay(fhirRef.getAssigningAuthority().getNamespaceId());
        reference.setIdentifier(id);
        return reference;
    }
}
