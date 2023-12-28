package org.openehealth.app.xdstofhir.registry.common.mapper;

import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.urnDecodedScheme;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssigningAuthority;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Author;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Code;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Identifiable;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.LocalizedString;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Organization;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Person;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Telecom;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Timestamp;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.XcnName;

public abstract class AbstractFhirToXdsMapper {

    protected Identifiable obtainIndexPatientId(Reference patientRef) {
        var ids = new ArrayList<Identifier>();
        var patientSubject = (Patient) patientRef.getResource();

        if (patientSubject != null && !patientSubject.getIdentifier().isEmpty())
            ids.addAll(patientSubject.getIdentifier());
        if (!patientRef.getIdentifier().isEmpty()) {
            ids.add(patientRef.getIdentifier());
        }

        return ids.isEmpty() ? null : bestQualifiedIdentified(ids);
    }

    protected Identifiable bestQualifiedIdentified(List<Identifier> ids) {
        var qualifiedId = ids.stream().filter(id -> Identifier.IdentifierUse.OFFICIAL.equals(id.getUse()))
                .findFirst().orElse(ids.stream().findFirst().orElseThrow());

        return new Identifiable(qualifiedId.getValue(),
                new AssigningAuthority(urnDecodedScheme(qualifiedId.getSystem())));
    }

    protected Timestamp fromDateTime(BaseDateTimeType dateTime) {
        return new Timestamp(dateTime.getValue().toInstant().atZone(ZoneId.systemDefault()),
                MappingSupport.PRECISION_MAP_FROM_FHIR.get(dateTime.getPrecision()));
    }

    protected Code fromCode(Coding code) {
        return new Code(code.getCode(), new LocalizedString(code.getDisplay()), urnDecodedScheme(code.getSystem()));
    }

    protected Author fromAuthor(Reference author) {
        var resource = author.getResource();
        var xdsAuthor = new Author();
        Practitioner doc = null;

        if (resource instanceof PractitionerRole fhirPractRole) {
            doc = (Practitioner) fhirPractRole.getPractitioner().getResource();
            xdsAuthor.getAuthorRole().addAll(fhirPractRole.getCode().stream().map(this::fromCodeableConcept).toList());
            xdsAuthor.getAuthorSpecialty().addAll(fhirPractRole.getSpecialty().stream().map(this::fromCodeableConcept).toList());
            fromOrganization((org.hl7.fhir.r4.model.Organization)fhirPractRole.getOrganization().getResource()).ifPresent(org -> xdsAuthor.getAuthorInstitution().add(org));
        } else if (resource instanceof Practitioner docResource) {
            doc = docResource;
        }

        if (doc != null) {
            xdsAuthor.setAuthorPerson(fromPractitioner(doc));
            xdsAuthor.getAuthorTelecom().addAll(doc.getTelecom().stream().map(this::fromContact).toList());
        }
        return xdsAuthor;
    }

    protected Person fromPractitioner(Practitioner doc) {
        var name = doc.getNameFirstRep();
        var xdsPerson = new Person();
        if (!doc.getIdentifier().isEmpty())
            xdsPerson.setId(bestQualifiedIdentified(doc.getIdentifier()));
        var xdsName = mapName(name);
        xdsPerson.setName(xdsName);
        return xdsPerson;
    }

    protected XcnName mapName(HumanName name) {
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

}
