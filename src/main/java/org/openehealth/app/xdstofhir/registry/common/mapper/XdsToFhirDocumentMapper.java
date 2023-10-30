package org.openehealth.app.xdstofhir.registry.common.mapper;

import static java.util.Collections.singletonList;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.MHD_COMPREHENSIVE_PROFILE;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.OID_URN;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.URI_URN;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.toUrnCoded;
import static org.openehealth.ipf.commons.ihe.xds.core.metadata.AvailabilityStatus.APPROVED;

import java.util.Date;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Enumerations.DocumentReferenceStatus;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.app.xdstofhir.registry.common.RegistryConfiguration;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Author;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Code;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.DocumentEntry;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Identifiable;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Name;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.PatientInfo;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Person;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.ReferenceId;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Telecom;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Timestamp;
import org.openehealth.ipf.commons.map.BidiMappingService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class XdsToFhirDocumentMapper implements Function<DocumentEntry, DocumentReference> {

    private static final String HL7V2FHIR_PATIENT_ADMINISTRATIVE_GENDER = "hl7v2fhir-patient-administrativeGender";
    private final RegistryConfiguration registryConfig;
    private final BidiMappingService hl7v2FhirMapping;

    @Override
    public DocumentReference apply(final DocumentEntry xdsDoc) {
        var fhirDoc = new DocumentReference();
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
        var patientReference = new Reference(new IdType(Patient.class.getSimpleName(), xdsDoc.getPatientId().getId()));
        patientReference.setType(Patient.class.getSimpleName());
        var patientId = fromIdentifier(xdsDoc.getPatientId());
        patientId.setUse(Identifier.IdentifierUse.OFFICIAL);
        patientReference.setIdentifier(patientId);
        fhirDoc.setSubject(patientReference);
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

    private Reference fromAuthor(final Author author) {
        var role = new PractitionerRole();
        var doc = new Practitioner();
        if(!author.getAuthorPerson().isEmpty()) {
            doc.setName(singletonList(fromName(author.getAuthorPerson().getName())));
            doc.addIdentifier(fromIdentifier(author.getAuthorPerson().getId()));
        }
        doc.setTelecom(author.getAuthorTelecom().stream().map(this::fromTelecom).collect(Collectors.toList()));
        var reference = new Reference();
        role.getPractitioner().setResource(doc);
        role.setCode(author.getAuthorRole().stream().map(this::convertToCode).collect(Collectors.toList()));
        role.setSpecialty(author.getAuthorSpecialty().stream().map(this::convertToCode).collect(Collectors.toList()));
        if (!author.getAuthorInstitution().isEmpty()) {
            // TODO: currently only the first element is mapped, because FHIR only support cardinality 1
            var xdsAuthorOrg = author.getAuthorInstitution().get(0);
            var org = new Organization();
            org.setName(xdsAuthorOrg.getOrganizationName());
            if (xdsAuthorOrg.getIdNumber() != null) {
                var identifier = new Identifier();
                identifier.setSystem(OID_URN + xdsAuthorOrg.getAssigningAuthority().getUniversalId());
                identifier.setValue(xdsAuthorOrg.getIdNumber());
                org.addIdentifier(identifier);
            }
            role.getOrganization().setResource(org);
        }
        reference.setResource(role);
        return reference;
    }

    private CodeableConcept convertToCode(final Identifiable id) {
        var fhirConcept = new CodeableConcept();
        Coding codeing;
        if (id.getAssigningAuthority() == null || id.getAssigningAuthority().isEmpty()) {
            codeing = new Coding(null, id.getId(), null);
        } else {
            codeing = new Coding(toUrnCoded(id.getAssigningAuthority().getUniversalId()), id.getId(), null);
        }
        fhirConcept.setCoding(singletonList(codeing));
        return fhirConcept;
    }

    private ContactPoint fromTelecom (final Telecom xdsTelecom) {
        var cp = new ContactPoint();
        if (xdsTelecom.getEmail() != null) {
            cp.setSystem(ContactPointSystem.EMAIL);
            cp.setValue(xdsTelecom.getEmail());
        }
        return cp;
    }

    private HumanName fromName(final Name<?> xdsName) {
        var name = new HumanName();
        name.setFamily(xdsName.getFamilyName());
        if (xdsName.getGivenName() != null)
            name.setGiven(singletonList(new StringType(xdsName.getGivenName())));
        if (xdsName.getPrefix() != null)
            name.setPrefix(singletonList(new StringType(xdsName.getPrefix())));
        if (xdsName.getSuffix() != null)
            name.setSuffix(singletonList(new StringType(xdsName.getSuffix())));
        return name;
    }

    private DateTimeType fromTimestamp(final Timestamp timestamp) {
        return new DateTimeType(Date.from(timestamp.getDateTime().toInstant()),
                MappingSupport.PRECISION_MAP_FROM_XDS.get(timestamp.getPrecision()));
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

    private Identifier fromIdentifier(final Identifiable id) {
        var identifier = new Identifier();
        identifier.setSystem(OID_URN + id.getAssigningAuthority().getUniversalId());
        identifier.setValue(id.getId());
        return identifier;
    }

    private Identifier fromIdentifier(final String urnIdValue, final Identifier.IdentifierUse type) {
        var identifier = new Identifier();
        identifier.setUse(type);
        identifier.setSystem(URI_URN);
        identifier.setValue(urnIdValue);
        return identifier;
    }

    private CodeableConcept fromCode(final Code code) {
        var coding = map(code);
        var fhirConcept = new CodeableConcept();
        fhirConcept.setCoding(singletonList(coding));
        return fhirConcept;
    }

    private Coding map(final Code code) {
        return new Coding(toUrnCoded(code.getSchemeName()), code.getCode(), code.getDisplayName().getValue());
    }

}
