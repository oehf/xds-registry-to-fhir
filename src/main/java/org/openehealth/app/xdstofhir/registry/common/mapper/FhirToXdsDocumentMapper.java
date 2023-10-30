package org.openehealth.app.xdstofhir.registry.common.mapper;

import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.urnDecodedScheme;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Enumerations.DocumentReferenceStatus;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.app.xdstofhir.registry.common.RegistryConfiguration;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Address;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssigningAuthority;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Author;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AvailabilityStatus;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.CXiAssigningAuthority;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Code;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.DocumentEntry;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Identifiable;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.LocalizedString;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Organization;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.PatientInfo;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Person;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.ReferenceId;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Telecom;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Timestamp;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.XcnName;
import org.openehealth.ipf.commons.map.BidiMappingService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FhirToXdsDocumentMapper implements Function<DocumentReference, DocumentEntry> {

    private static final String HL7V2FHIR_PATIENT_GENDER = "hl7v2fhir-patient-gender";
    private final RegistryConfiguration registryConfig;
    private final BidiMappingService hl7v2FhirMapping;

    @Override
    public DocumentEntry apply(final DocumentReference fhirDoc) {
        if (fhirDoc.getIdentifier().isEmpty())
            return null;
        var patientId = obtainIndexPatientId(fhirDoc);
        if (patientId == null)
            return null;
        var doc = new DocumentEntry();
        doc.setAvailabilityStatus(fhirDoc.getStatus() == DocumentReferenceStatus.CURRENT ? AvailabilityStatus.APPROVED
                : AvailabilityStatus.DEPRECATED);
        doc.setEntryUuid(bestQualifiedIdentified(fhirDoc.getIdentifier()).getId());
        doc.setUniqueId(urnDecodedScheme(fhirDoc.getMasterIdentifier().getValue()));
        doc.setTypeCode(fromCode(fhirDoc.getType().getCodingFirstRep()));
        doc.setClassCode(fromCode(fhirDoc.getCategory().stream().findFirst().orElseThrow().getCodingFirstRep()));
        doc.setFormatCode(fromCode(fhirDoc.getContentFirstRep().getFormat()));
        doc.setPracticeSettingCode(fromCode(fhirDoc.getContext().getPracticeSetting().getCodingFirstRep()));
        doc.setHealthcareFacilityTypeCode(fromCode(fhirDoc.getContext().getFacilityType().getCodingFirstRep()));
        doc.getConfidentialityCodes().addAll(mapCodeList(fhirDoc.getSecurityLabel()));
        var attachment = fhirDoc.getContentFirstRep().getAttachment();
        if (fhirDoc.getDescription() != null) {
            doc.setComments(new LocalizedString(fhirDoc.getDescription(), attachment.getLanguage(),
                    StandardCharsets.UTF_8.name()));
        }
        doc.getEventCodeList().addAll(mapCodeList(fhirDoc.getContext().getEvent()));
        doc.setPatientId(patientId);
        doc.setMimeType(attachment.getContentType());
        if (attachment.getTitle() != null)
            doc.setTitle(
                    new LocalizedString(attachment.getTitle(), attachment.getLanguage(), StandardCharsets.UTF_8.name()));
        doc.setSize(Long.valueOf(attachment.getSize()));
        var hash = attachment.getHashElement().asStringValue();
        doc.setHash(hash != null ? hash : registryConfig.getDefaultHash());
        var creation = attachment.getCreationElement() != null ? attachment.getCreationElement()
                : fhirDoc.getDateElement();
        doc.setCreationTime(fromDateTime(creation));
        doc.setLanguageCode(attachment.getLanguage());
        doc.setSourcePatientId(fromSourcePatient(fhirDoc.getContext().getSourcePatientInfo().getIdentifier()).orElse(doc.getPatientId()));
        doc.setSourcePatientInfo(fromSourcePatient(fhirDoc.getContext().getSourcePatientInfo()));
        doc.setRepositoryUniqueId(registryConfig.repositoryFromUrl(attachment.getUrl()));
        doc.setLegalAuthenticator(fromAuthenticator(fhirDoc.getAuthenticator()));
        fhirDoc.getContext().getRelated()
                .forEach(
                        id -> doc.getReferenceIdList()
                                .add(new ReferenceId(id.getIdentifier().getValue(),
                                        new CXiAssigningAuthority(id.getDisplay(),
                                                urnDecodedScheme(id.getIdentifier().getSystem()), "ISO"),
                                        id.getType())));
        doc.getAuthors().addAll(fhirDoc.getAuthor().stream().map(this::fromAuthor).collect(Collectors.toList()));
        mapServicePeriod(fhirDoc, doc);
        return doc;
    }

    private PatientInfo fromSourcePatient(Reference sourcePatientInfo) {
        if (sourcePatientInfo.getResource() instanceof Patient) {
            var sourcePatientFhirResource = (Patient) sourcePatientInfo.getResource();
            PatientInfo patientInfo = new PatientInfo();
            if (sourcePatientFhirResource.getGender() != null) {
                patientInfo.setGender((String) hl7v2FhirMapping.get(HL7V2FHIR_PATIENT_GENDER,
                        sourcePatientFhirResource.getGender().toString().toLowerCase()));
            }
            sourcePatientFhirResource.getName().stream().forEach(name -> patientInfo.getNames().add(mapName(name)));
            if (!sourcePatientFhirResource.getBirthDateElement().isEmpty())
                patientInfo.setDateOfBirth(fromDateTime(sourcePatientFhirResource.getBirthDateElement()));
            sourcePatientFhirResource.getAddress().stream().forEach(address -> patientInfo.getAddresses().add(fromAddress(address)));
            return patientInfo;
        }
        return null;
    }

    private Address fromAddress(org.hl7.fhir.r4.model.Address fhirAddress) {
        var address = new Address();
        address.setCity(fhirAddress.getCity());
        if (!fhirAddress.getLine().isEmpty()) {
            address.setStreetAddress(fhirAddress.getLine().get(0).asStringValue());
        }
        address.setZipOrPostalCode(fhirAddress.getPostalCode());
        address.setCountry(fhirAddress.getCountry());
        return address;
    }

    private Optional<Identifiable> fromSourcePatient(Identifier sourcePatientId) {
        if (!sourcePatientId.isEmpty())
            return Optional.of(bestQualifiedIdentified(Collections.singletonList(sourcePatientId)));
        else
            return Optional.empty();
    }

    private Person fromAuthenticator(Reference authenticator) {
        var resource = authenticator.getResource();
        Practitioner doc = null;
        if (resource instanceof PractitionerRole) {
            doc = (Practitioner) ((PractitionerRole)resource).getPractitioner().getResource();
        } else if (resource instanceof Practitioner) {
            doc = (Practitioner)resource;
        }
        if (doc != null) {
            return fromPractitioner(doc);
        }
        return null;
    }

    private Author fromAuthor(Reference author) {
        var resource = author.getResource();
        var xdsAuthor = new Author();
        Practitioner doc = null;

        if (resource instanceof PractitionerRole) {
            var fhirPractRole = (PractitionerRole)resource;
            doc = (Practitioner) fhirPractRole.getPractitioner().getResource();
            xdsAuthor.getAuthorRole().addAll(fhirPractRole.getCode().stream().map(this::fromCodeableConcept).collect(Collectors.toList()));
            xdsAuthor.getAuthorSpecialty().addAll(fhirPractRole.getSpecialty().stream().map(this::fromCodeableConcept).collect(Collectors.toList()));
            fromOrganization((org.hl7.fhir.r4.model.Organization)fhirPractRole.getOrganization().getResource()).ifPresent(org -> xdsAuthor.getAuthorInstitution().add(org));
        } else if (resource instanceof Practitioner) {
            doc = (Practitioner)resource;
        }

        if (doc != null) {
            xdsAuthor.setAuthorPerson(fromPractitioner(doc));
            xdsAuthor.getAuthorTelecom().addAll(doc.getTelecom().stream().map(this::fromContact).collect(Collectors.toList()));
        }
        return xdsAuthor;
    }

    private Person fromPractitioner(Practitioner doc) {
        var name = doc.getNameFirstRep();
        var xdsPerson = new Person();
        if (!doc.getIdentifier().isEmpty())
            xdsPerson.setId(bestQualifiedIdentified(doc.getIdentifier()));
        var xdsName = mapName(name);
        xdsPerson.setName(xdsName);
        return xdsPerson;
    }

    private XcnName mapName(HumanName name) {
        var xdsName = new XcnName();
        xdsName.setFamilyName(name.getFamily());
        if (!name.getGiven().isEmpty())
            xdsName.setGivenName(name.getGivenAsSingleString());
        if (!name.getPrefix().isEmpty())
            xdsName.setPrefix(name.getPrefixAsSingleString());
        if (!name.getSuffix().isEmpty())
            xdsName.setSuffix(name.getSuffixAsSingleString());
        return xdsName;
    }

    private Optional<Organization> fromOrganization(org.hl7.fhir.r4.model.Organization fhirAuthOrg){
        if (fhirAuthOrg != null) {
            var xdsOrg = new Organization();
            var identifierFirstRep = fhirAuthOrg.getIdentifierFirstRep();
            if (identifierFirstRep != null && !identifierFirstRep.isEmpty()) {
                xdsOrg.setIdNumber(identifierFirstRep.getValue());
                xdsOrg.setAssigningAuthority(new AssigningAuthority(urnDecodedScheme(identifierFirstRep.getSystem())));
            }
            xdsOrg.setOrganizationName(fhirAuthOrg.getName());
            return Optional.of(xdsOrg);
        } else {
            return Optional.empty();
        }
    }

    private Identifiable fromCodeableConcept(CodeableConcept codeConcept ) {
        var id = new Identifiable();
        var code = codeConcept.getCodingFirstRep();
        if (code.getSystem() != null) {
            id.setAssigningAuthority(new AssigningAuthority(urnDecodedScheme(code.getSystem())));
        }
        id.setId(code.getCode());
        return id;
    }

    private Telecom fromContact(ContactPoint contact) {
        var tel = new Telecom();
        if (contact.getSystem() == ContactPointSystem.EMAIL) {
            tel.setEmail(contact.getValue());
            tel.setType("Internet");
            tel.setUse("NET");
        }
        return tel;
    }

    private void mapServicePeriod(DocumentReference fhirDoc, DocumentEntry doc) {
        var period = fhirDoc.getContext().getPeriod();
        if (!period.isEmpty()) {
            if (period.getStart() != null) {
                doc.setServiceStartTime(fromDateTime(period.getStartElement()));
            }
            if (period.getEnd() != null) {
                doc.setServiceStopTime(fromDateTime(period.getEndElement()));
            }
        }
    }

    private Identifiable obtainIndexPatientId(DocumentReference fhirDoc) {
        var ids = new ArrayList<Identifier>();
        var patientSubject = (Patient) fhirDoc.getSubject().getResource();

        if (patientSubject != null && !patientSubject.getIdentifier().isEmpty())
            ids.addAll(patientSubject.getIdentifier());
        if (!fhirDoc.getSubject().getIdentifier().isEmpty()) {
            ids.add(fhirDoc.getSubject().getIdentifier());
        }

        return ids.isEmpty() ? null : bestQualifiedIdentified(ids);
    }

    private List<Code> mapCodeList(List<CodeableConcept> codes) {
        return codes.stream().map(CodeableConcept::getCodingFirstRep).map(this::fromCode).collect(Collectors.toList());
    }

    private Identifiable bestQualifiedIdentified(List<Identifier> ids) {
        var qualifiedId = ids.stream().filter(id -> Identifier.IdentifierUse.OFFICIAL.equals(id.getUse()))
                .findFirst().orElse(ids.stream().findFirst().orElseThrow());

        return new Identifiable(qualifiedId.getValue(),
                new AssigningAuthority(urnDecodedScheme(qualifiedId.getSystem())));
    }

    private Code fromCode(Coding code) {
        return new Code(code.getCode(), new LocalizedString(code.getDisplay()), urnDecodedScheme(code.getSystem()));
    }

    private Timestamp fromDateTime(BaseDateTimeType dateTime) {
        return new Timestamp(dateTime.getValue().toInstant().atZone(ZoneId.systemDefault()),
                MappingSupport.PRECISION_MAP_FROM_FHIR.get(dateTime.getPrecision()));
    }

}
