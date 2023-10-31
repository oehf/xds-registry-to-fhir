package org.openehealth.app.xdstofhir.registry.register;

import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.OID_URN;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.URI_URN;

import java.util.function.Consumer;
import java.util.function.Function;

import ca.uhn.fhir.rest.api.CacheControlDirective;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.util.BundleBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Enumerations.DocumentReferenceStatus;
import org.hl7.fhir.r4.model.Patient;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.app.xdstofhir.registry.common.RegistryConfiguration;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssociationType;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AvailabilityStatus;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.DocumentEntry;
import org.openehealth.ipf.commons.ihe.xds.core.requests.RegisterDocumentSet;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Response;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Status;
import org.openehealth.ipf.commons.ihe.xds.core.validate.ValidationMessage;
import org.openehealth.ipf.commons.ihe.xds.core.validate.XDSMetaDataException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RegisterDocumentsProcessor implements Iti42Service {
    private final IGenericClient client;
    private final Function<DocumentEntry, DocumentReference> documentMapper;
    private final RegistryConfiguration registryConfig;

    @Override
    public Response processRegister(RegisterDocumentSet register) {
        BundleBuilder builder = new BundleBuilder(client.getFhirContext());

        validateKnownRepository(register);
        register.getDocumentEntries().forEach(this::assignRegistryValues);
        register.getDocumentEntries().forEach(assignPatientId());
        register.getAssociations().stream().filter(assoc -> assoc.getAssociationType() == AssociationType.REPLACE)
                .forEach(assoc -> builder.addTransactionUpdateEntry(replacePreviousDocument(assoc.getTargetUuid(),
                        register.getDocumentEntries().stream()
                                .filter(doc -> doc.getEntryUuid().equals(assoc.getSourceUuid())).findFirst().map(documentMapper)
                                .orElseThrow(() -> new XDSMetaDataException(ValidationMessage.UNRESOLVED_REFERENCE,
                                        assoc.getSourceUuid())))));
        register.getDocumentEntries().forEach(doc -> builder.addTransactionCreateEntry(documentMapper.apply(doc)));

        // Execute the transaction
        client.transaction().withBundle(builder.getBundle()).execute();

        return new Response(Status.SUCCESS);
    }

    /**
     * Perform replace according to https://profiles.ihe.net/ITI/TF/Volume2/ITI-42.html#3.42.4.1.3.5
     *
     * @param entryUuid
     * @param replacingDocument
     * @return Replaced document with status set to superseded
     */
    private DocumentReference replacePreviousDocument(String entryUuid, DocumentReference replacingDocument) {
        var result = client.search().forResource(DocumentReference.class).count(1)
                .where(DocumentReference.IDENTIFIER.exactly().systemAndValues(URI_URN, entryUuid))
                .returnBundle(Bundle.class).execute();
        if (result.getEntry().isEmpty()) {
            throw new XDSMetaDataException(ValidationMessage.UNRESOLVED_REFERENCE, entryUuid);
        }
        var replacedDocument = (DocumentReference)result.getEntryFirstRep().getResource();
        if (replacedDocument.getStatus() != DocumentReferenceStatus.CURRENT) {
            throw new XDSMetaDataException(ValidationMessage.DEPRECATED_OBJ_CANNOT_BE_TRANSFORMED);
        }
        if (!replacedDocument.getSubject().getReference().equals(replacingDocument.getSubject().getReference())) {
            log.debug("Replacing and replaced document do not have the same patientid {} and {}",
                    replacedDocument.getSubject().getReference(), replacingDocument.getSubject().getReference());
            throw new XDSMetaDataException(ValidationMessage.DOC_ENTRY_PATIENT_ID_WRONG);
        }
        replacedDocument.setStatus(DocumentReferenceStatus.SUPERSEDED);
        return replacedDocument;
    }

    private void validateKnownRepository(RegisterDocumentSet register) {
        register.getDocumentEntries().forEach(doc -> {
            if (!registryConfig.getRepositoryEndpoint().containsKey(doc.getRepositoryUniqueId())) {
                throw new XDSMetaDataException(ValidationMessage.UNKNOWN_REPOSITORY_ID, doc.getRepositoryUniqueId());
            }
        });
    }

    private void assignRegistryValues(DocumentEntry doc) {
        if (!doc.getEntryUuid().startsWith(MappingSupport.UUID_URN)) {
            doc.assignEntryUuid();
        }
        doc.setAvailabilityStatus(AvailabilityStatus.APPROVED);
    }

    private Consumer<DocumentEntry> assignPatientId() {
        return doc -> {
            var result = client.search().forResource(Patient.class).count(1)
                    .where(Patient.IDENTIFIER.exactly().systemAndIdentifier(
                            OID_URN + doc.getPatientId().getAssigningAuthority().getUniversalId(),
                            doc.getPatientId().getId()))
                    .returnBundle(Bundle.class)
                    .cacheControl(new CacheControlDirective().setNoCache(true).setNoStore(true))
                    .execute();
            if (result.getEntry().isEmpty()) {
                throw new XDSMetaDataException(ValidationMessage.UNKNOWN_PATIENT_ID);
            }
            doc.getPatientId().setId(result.getEntryFirstRep().getResource().getIdPart());
        };
    }

}
