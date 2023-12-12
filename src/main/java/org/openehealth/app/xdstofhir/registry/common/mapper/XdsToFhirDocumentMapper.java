package org.openehealth.app.xdstofhir.registry.common.mapper;

import static java.util.Collections.singletonList;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.MHD_COMPREHENSIVE_PROFILE;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.toUrnCoded;
import static org.openehealth.ipf.commons.ihe.xds.core.metadata.AvailabilityStatus.APPROVED;

import java.util.Date;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Enumerations.DocumentReferenceStatus;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.app.xdstofhir.registry.common.RegistryConfiguration;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.DocumentEntry;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.PatientInfo;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Person;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.ReferenceId;
import org.openehealth.ipf.commons.map.BidiMappingService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class XdsToFhirDocumentMapper extends AbstractXdsToFhirMapper
        implements Function<DocumentEntry, DocumentReference> {

    private static final String HL7V2FHIR_PATIENT_ADMINISTRATIVE_GENDER = "hl7v2fhir-patient-administrativeGender";
    private final RegistryConfiguration registryConfig;
    private final BidiMappingService hl7v2FhirMapping;

    @Override
    public DocumentReference apply(final DocumentEntry xdsDoc) {
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
                xdsDoc.getConfidentialityCodes().stream().map(this::fromCode).collect(Collectors.toList()));
        if (xdsDoc.getComments() != null)
            fhirDoc.setDescription(xdsDoc.getComments().getValue());
        fhirDoc.getContext().setEvent(xdsDoc.getEventCodeList().stream().map(this::fromCode).collect(Collectors.toList()));
        var attachment = fhirDoc.getContentFirstRep().getAttachment();
        attachment.setContentType(xdsDoc.getMimeType());
        if (xdsDoc.getTitle() != null)
            attachment.setTitle(xdsDoc.getTitle().getValue());
        attachment.setSize(xdsDoc.getSize().intValue());
        attachment.setHashElement(new Base64BinaryType(xdsDoc.getHash()));
        attachment.setCreationElement(fromTimestamp(xdsDoc.getCreationTime()));
        attachment.setLanguage(xdsDoc.getLanguageCode());
        attachment.setUrl(registryConfig.urlFrom(xdsDoc.getRepositoryUniqueId(), xdsDoc.getUniqueId())); // TODO: does not make too much sense.
        fhirDoc.setDate(Date.from(xdsDoc.getCreationTime().getDateTime().toInstant()));
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
                        .map(fhirRef -> mapReferenceId(fhirRef)).collect(Collectors.toList()));
        fhirDoc.getContext()
                .setRelated(xdsDoc.getReferenceIdList().stream()
                        .filter(refId -> !ReferenceId.ID_TYPE_ENCOUNTER_ID.equals(refId.getIdTypeCode()))
                        .map(fhirRef -> mapReferenceId(fhirRef)).collect(Collectors.toList()));
        fhirDoc.setAuthor(xdsDoc.getAuthors().stream().map(this::fromAuthor).collect(Collectors.toList()));
        if (xdsDoc.getLegalAuthenticator() != null && !xdsDoc.getLegalAuthenticator().isEmpty()) {
            fhirDoc.setAuthenticator(fromAuthenticator(xdsDoc.getLegalAuthenticator()));
        }
        mapServicePeriod(xdsDoc, fhirDoc);
        return fhirDoc;
    }

    private Reference fromAuthenticator(Person xdsAuthenticator) {
        var authenticator = new Reference();
        authenticator.setType(Practitioner.class.getSimpleName());
        var practitoner = new Practitioner();
        practitoner.setName(singletonList(fromName(xdsAuthenticator.getName())));
        practitoner.addIdentifier(fromIdentifier(xdsAuthenticator.getId()));
        authenticator.setResource(practitoner);
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
