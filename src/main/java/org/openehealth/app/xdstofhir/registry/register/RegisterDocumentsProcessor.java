package org.openehealth.app.xdstofhir.registry.register;

import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.OID_URN;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.URI_URN;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import ca.uhn.fhir.rest.api.CacheControlDirective;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.util.BundleBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Enumerations.DocumentReferenceStatus;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.app.xdstofhir.registry.common.RegistryConfiguration;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdFolder;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdSubmissionSet;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Association;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssociationType;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AvailabilityStatus;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.DocumentEntry;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Folder;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.SubmissionSet;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.XDSMetaClass;
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
    private final BiFunction<SubmissionSet, List<Reference>, MhdSubmissionSet> submissionSetMapper;
    private final BiFunction<Folder, List<Reference>, MhdFolder> folderMapper;
    private final RegistryConfiguration registryConfig;

    @Override
    public Response processRegister(RegisterDocumentSet register) {
        validateKnownRepository(register);
        register.getDocumentEntries().forEach(doc -> assignRegistryValues(doc, register.getAssociations()));
        register.getDocumentEntries().forEach(this::assignPatientId);
        register.getFolders().forEach(folder -> assignRegistryValues(folder, register.getAssociations()));
        register.getFolders().forEach(this::assignPatientId);
        assignPatientId(register.getSubmissionSet());
        assignRegistryValues(register.getSubmissionSet(), register.getAssociations());
        var builder = new BundleBuilder(client.getFhirContext());
        register.getAssociations().stream().filter(assoc -> assoc.getAssociationType() == AssociationType.REPLACE)
                .forEach(assoc -> builder.addTransactionUpdateEntry(replacePreviousDocument(assoc.getTargetUuid(),
                        register.getDocumentEntries().stream()
                                .filter(doc -> doc.getEntryUuid().equals(assoc.getSourceUuid())).findFirst().map(documentMapper)
                                .orElseThrow(() -> new XDSMetaDataException(ValidationMessage.UNRESOLVED_REFERENCE,
                                        assoc.getSourceUuid())))));
        register.getDocumentEntries().forEach(doc -> builder.addTransactionCreateEntry(documentMapper.apply(doc)));

        var documentMap = register.getDocumentEntries().stream()
                .collect(Collectors.toMap(DocumentEntry::getEntryUuid, Function.identity()));

        var folderReferences = createReferences(register.getAssociations(), documentMap,
                register.getFolders().stream().map(XDSMetaClass::getEntryUuid).collect(Collectors.toList()));
        register.getFolders().forEach(folder -> builder.addTransactionCreateEntry(folderMapper.apply(folder, folderReferences)));

        var submissionReferences = createReferences(register.getAssociations(), documentMap,
                Collections.singletonList(register.getSubmissionSet().getEntryUuid()));
        builder.addTransactionCreateEntry(submissionSetMapper.apply(register.getSubmissionSet(), submissionReferences));

        // Execute the transaction
        client.transaction().withBundle(builder.getBundle()).execute();

        return new Response(Status.SUCCESS);
    }

    /**
     * Build the references for the given assocations, where the sourceId is ony of sourceId and the target is one of the docs from the
     * documentMap.
     *
     * @param associations
     * @param documentMap
     * @param sourceId
     * @return the List of FHIR references.
     */
    private List<Reference> createReferences(List<Association> associations, Map<String, DocumentEntry> documentMap,
            List<String> sourceId) {
        return associations.stream()
                .filter(assoc -> sourceId.contains(assoc.getSourceUuid()))
                .map(assoc -> documentMap.get(assoc.getTargetUuid())).filter(Objects::nonNull)
                .map(document -> new Reference(
                        new IdType(DocumentReference.class.getSimpleName(), document.getEntryUuid())))
                .collect(Collectors.toList());
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

    private void assignRegistryValues(XDSMetaClass xdsObject, List<Association> associations) {
        if (!xdsObject.getEntryUuid().startsWith(MappingSupport.UUID_URN)) {
            var previousIdentifier = xdsObject.getEntryUuid();
            xdsObject.assignEntryUuid();
            associations.stream().forEach(assoc -> {
                assoc.setSourceUuid(assoc.getSourceUuid().replace(previousIdentifier, xdsObject.getEntryUuid()));
                assoc.setTargetUuid(assoc.getTargetUuid().replace(previousIdentifier, xdsObject.getEntryUuid()));
            });;
        }
        xdsObject.setAvailabilityStatus(AvailabilityStatus.APPROVED);
    }

    private void assignPatientId(XDSMetaClass xdsObject) {
        var result = client.search().forResource(Patient.class).count(1)
                .where(Patient.IDENTIFIER.exactly().systemAndIdentifier(
                        OID_URN + xdsObject.getPatientId().getAssigningAuthority().getUniversalId(),
                        xdsObject.getPatientId().getId()))
                .returnBundle(Bundle.class).cacheControl(new CacheControlDirective().setNoCache(true).setNoStore(true))
                .execute();
        if (result.getEntry().isEmpty()) {
            throw new XDSMetaDataException(ValidationMessage.UNKNOWN_PATIENT_ID);
        }
        xdsObject.getPatientId().setId(result.getEntryFirstRep().getResource().getIdPart());
    }

}
