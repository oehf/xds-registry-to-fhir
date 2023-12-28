package org.openehealth.app.xdstofhir.registry.common.mapper;

import static java.util.Collections.singletonList;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.OID_URN;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.URI_URN;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.toUrnCoded;

import java.util.Date;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Author;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Code;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Identifiable;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Name;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Telecom;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Timestamp;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.XDSMetaClass;

public abstract class AbstractXdsToFhirMapper {

    protected Reference patientReferenceFrom(XDSMetaClass xdsObject) {
        var patientReference = new Reference(new IdType(Patient.class.getSimpleName(), xdsObject.getPatientId().getId()));
        patientReference.setType(Patient.class.getSimpleName());
        var patientId = fromIdentifier(xdsObject.getPatientId());
        patientId.setUse(Identifier.IdentifierUse.OFFICIAL);
        patientReference.setIdentifier(patientId);
        return patientReference;
    }

    protected Identifier fromIdentifier(final String urnIdValue, final Identifier.IdentifierUse type) {
        var identifier = new Identifier();
        identifier.setUse(type);
        identifier.setSystem(URI_URN);
        identifier.setValue(urnIdValue);
        return identifier;
    }

    protected Identifier fromIdentifier(final Identifiable id) {
        var identifier = new Identifier();
        identifier.setSystem(OID_URN + id.getAssigningAuthority().getUniversalId());
        identifier.setValue(id.getId());
        return identifier;
    }

    protected DateTimeType fromTimestamp(final Timestamp timestamp) {
        if (timestamp == null)
            return null;
        return new DateTimeType(Date.from(timestamp.getDateTime().toInstant()),
                MappingSupport.PRECISION_MAP_FROM_XDS.get(timestamp.getPrecision()));
    }

    protected CodeableConcept fromCode(final Code code) {
        var coding = map(code);
        var fhirConcept = new CodeableConcept();
        fhirConcept.setCoding(singletonList(coding));
        return fhirConcept;
    }

    protected Coding map(final Code code) {
        return new Coding(toUrnCoded(code.getSchemeName()), code.getCode(), code.getDisplayName().getValue());
    }

    protected Reference fromAuthor(final Author author) {
        var role = new PractitionerRole();
        var doc = new Practitioner();
        if(!author.getAuthorPerson().isEmpty()) {
            if (!author.getAuthorPerson().getName().isEmpty())
                doc.setName(singletonList(fromName(author.getAuthorPerson().getName())));
            if (!author.getAuthorPerson().getId().isEmpty())
                doc.addIdentifier(fromIdentifier(author.getAuthorPerson().getId()));
        }
        doc.setTelecom(author.getAuthorTelecom().stream().map(this::fromTelecom).toList());
        var reference = new Reference();
        role.getPractitioner().setResource(doc);
        role.setCode(author.getAuthorRole().stream().map(this::convertToCode).toList());
        role.setSpecialty(author.getAuthorSpecialty().stream().map(this::convertToCode).toList());
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

    protected CodeableConcept convertToCode(final Identifiable id) {
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

    protected ContactPoint fromTelecom (final Telecom xdsTelecom) {
        var cp = new ContactPoint();
        if (xdsTelecom.getEmail() != null) {
            cp.setSystem(ContactPointSystem.EMAIL);
            cp.setValue(xdsTelecom.getEmail());
        }
        return cp;
    }

    protected HumanName fromName(final Name<?> xdsName) {
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
}
