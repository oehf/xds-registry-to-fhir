package org.openehealth.app.xdstofhir.registry.register;

import static java.util.Collections.emptyList;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.DOC_DOC_FHIR_ASSOCIATIONS;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.OID_URN;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.URI_URN;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.UUID_URN;
import static org.openehealth.ipf.commons.ihe.xds.core.validate.ValidatorAssertions.metaDataAssert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import ca.uhn.fhir.rest.api.CacheControlDirective;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.util.BundleBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.DocumentReference.DocumentReferenceRelatesToComponent;
import org.hl7.fhir.r4.model.Enumerations.DocumentReferenceStatus;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Identifier.IdentifierUse;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.ListResource.ListEntryComponent;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.app.xdstofhir.registry.common.RegistryConfiguration;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdFolder;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdSubmissionSet;
import org.openehealth.ipf.commons.core.URN;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Association;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssociationType;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AvailabilityStatus;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.DocumentEntry;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Folder;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.SubmissionSet;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.XDSMetaClass;
import org.openehealth.ipf.commons.ihe.xds.core.requests.RegisterDocumentSet;
import org.openehealth.ipf.commons.ihe.xds.core.responses.ErrorCode;
import org.openehealth.ipf.commons.ihe.xds.core.responses.ErrorInfo;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Response;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Severity;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Status;
import org.openehealth.ipf.commons.ihe.xds.core.validate.ValidationMessage;
import org.openehealth.ipf.commons.ihe.xds.core.validate.XDSMetaDataException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RegisterDocumentsProcessor implements Iti42Service, Iti61Service {
    private final IGenericClient client;
    private final BiFunction<DocumentEntry, List<DocumentReferenceRelatesToComponent>, DocumentReference> documentMapper;
    private final BiFunction<SubmissionSet, List<ListEntryComponent>, MhdSubmissionSet> submissionSetMapper;
    private final BiFunction<Folder, List<ListEntryComponent>, MhdFolder> folderMapper;
    private final RegistryConfiguration registryConfig;


    @Override
    public Response processRegister(RegisterDocumentSet register) {
        validateKnownRepository(register);
        register.getDocumentEntries().forEach(this::validateResubmission);
        register.getDocumentEntries().forEach(doc -> assignRegistryValues(doc, register.getAssociations()));
        register.getDocumentEntries().forEach(this::assignPatientId);
        register.getFolders().forEach(folder -> assignRegistryValues(folder, register.getAssociations()));
        register.getFolders().forEach(this::assignPatientId);
        assignPatientId(register.getSubmissionSet());
        assignRegistryValues(register.getSubmissionSet(), register.getAssociations());
        assignRegistryValues(register.getAssociations());
        var builder = new BundleBuilder(client.getFhirContext());
        evaluateDocumentReplacement(register, builder);
        builder.setMetaField("profile", new CanonicalType(MappingSupport.MHD_COMPREHENSIVE_PROVIDE_PROFILE));

        var docReferences = createDocToDocReferences(register.getAssociations());

        register.getDocumentEntries().forEach(doc -> builder.addTransactionCreateEntry(documentMapper.apply(doc, docReferences)));

        var documentMap = register.getDocumentEntries().stream()
                .collect(Collectors.toMap(DocumentEntry::getEntryUuid, Function.identity()));

        var folderAssociations = register.getAssociations().stream()
                .filter(assoc -> !assoc.getSourceUuid().equals(register.getSubmissionSet().getEntryUuid()))
                .filter(assoc -> AssociationType.HAS_MEMBER.equals(assoc.getAssociationType()))
                .toList();

        var folderUuids = register.getFolders().stream().map(XDSMetaClass::getEntryUuid).toList();
        var externalFolderUuid = folderAssociations.stream().map(Association::getSourceUuid).filter(folderId -> !folderUuids.contains(folderId)).toList();
        createUpdateOfExistingFolders(externalFolderUuid, folderAssociations, documentMap).forEach(builder::addTransactionUpdateEntry);

        var folderReferences = createReferences(folderAssociations, documentMap, folderUuids);
        register.getFolders().forEach(folder -> builder.addTransactionCreateEntry(folderMapper.apply(folder, folderReferences)));

        var submissionReferences = new ArrayList<ListEntryComponent>(createReferences(register.getAssociations(), documentMap,
                Collections.singletonList(register.getSubmissionSet().getEntryUuid())));
        var folderMap = register.getFolders().stream()
                .collect(Collectors.toMap(Folder::getEntryUuid, Function.identity()));
        submissionReferences.addAll(createReferences(register.getAssociations(), folderMap,
                Collections.singletonList(register.getSubmissionSet().getEntryUuid())));
        submissionReferences.addAll(createReferences(register.getAssociations(),
                Collections.singletonList(register.getSubmissionSet().getEntryUuid())));
        builder.addTransactionCreateEntry(submissionSetMapper.apply(register.getSubmissionSet(), submissionReferences));

        // Execute the transaction
        client.transaction().withBundle(builder.getBundle()).execute();

        var response = new Response(Status.SUCCESS);

        addWarningForExtraMetadataIfPresent(register, response);

        return response;
    }

    /**
     * This registry implementation currently do not store extra-metadata. Notify with a client warning that no
     * extra metadata is being stored.
     *
     * @param register
     * @param response
     */
    private void addWarningForExtraMetadataIfPresent(RegisterDocumentSet register, Response response) {
        if (register.getDocumentEntries().stream()
                .anyMatch(doc -> doc.getExtraMetadata() != null && !doc.getExtraMetadata().isEmpty())) {
            response.getErrors().add(new ErrorInfo(ErrorCode.EXTRA_METADATA_NOT_SAVED,
                    "Register do not yet support storing extra metadata", Severity.WARNING, null, null));
        }
    }

    /**
     * Validate the resubmission preconditions:
     * - entryUUID shall not be used before (in case client use a UUID based id)
     * - same uniqueid is only allowed if hash and size is the same as the existing document.
     *
     * @param doc
     */
    private void validateResubmission(DocumentEntry doc) {
        DocumentReference existingDoc;
        try {
            if (doc.getEntryUuid().startsWith(UUID_URN)) {
                existingDoc = lookupExistingDocument(doc.getEntryUuid(), MappingSupport.toUrnCoded(doc.getUniqueId()));
            } else {
                existingDoc = lookupExistingDocument(MappingSupport.toUrnCoded(doc.getUniqueId()));
            }
        } catch (XDSMetaDataException notPresent) {
            return;
        }
        metaDataAssert(existingDoc.getIdentifier().stream().filter(id -> id.getValue().equals(doc.getEntryUuid()))
                .findAny().isEmpty(), ValidationMessage.UUID_NOT_UNIQUE);
        metaDataAssert(
                doc.getHash().equals(existingDoc.getContentFirstRep().getAttachment().getHashElement().asStringValue()),
                ValidationMessage.DIFFERENT_HASH_CODE_IN_RESUBMISSION);
        metaDataAssert(doc.getSize() == existingDoc.getContentFirstRep().getAttachment().getSize(),
                ValidationMessage.DIFFERENT_SIZE_IN_RESUBMISSION);
    }

    /**
     * Update an existing folder and add a link to a document.
     *
     * @param externalFolderUuid
     * @param folderAssociations
     * @param documentMap
     * @return a list of folder objects that need to be updated.
     */
    private List<MhdFolder> createUpdateOfExistingFolders(List<String> externalFolderUuid,
            List<Association> folderAssociations, Map<String, DocumentEntry> documentMap) {
        return folderAssociations.stream().filter(assoc -> externalFolderUuid.contains(assoc.getSourceUuid()))
                .map(assoc -> {
                    var folder = lookupExistingFolder(assoc.getSourceUuid());
                    folder.setDate(new Date());
                    var documentEntry = documentMap.get(assoc.getTargetUuid());
                    if (documentEntry != null) {
                        metaDataAssert(
                                folder.getSubject().getIdentifier().getValue()
                                        .equals(documentEntry.getPatientId().getId()),
                                ValidationMessage.FOLDER_PATIENT_ID_WRONG);
                        folder.addEntry(createReference(assoc, DocumentReference.class.getSimpleName()));
                    } else {
                        var existingDoc = lookupExistingDocument(assoc.getTargetUuid());
                        metaDataAssert(
                                folder.getSubject().getIdentifier().getValue()
                                        .equals(existingDoc.getSubject().getIdentifier().getValue()),
                                ValidationMessage.FOLDER_PATIENT_ID_WRONG);
                        var ref = new ListEntryComponent(new Reference(existingDoc));
                        ref.setId(assoc.getEntryUuid());
                        folder.addEntry(ref);
                        folder.setDate(new Date());
                    }
                    return folder;
                }).toList();
    }

    /**
     * lookup an existing folder in the FHIR server. In case this folder do not exists, throw a XDS metadata exception to
     * reject the transaction.
     *
     * @param entryUuid
     * @return The folder associated with the given uuid.
     */
    private MhdFolder lookupExistingFolder(String entryUuid) {
        var result = client.search().forResource(MhdFolder.class).count(1)
                .where(ListResource.IDENTIFIER.exactly().systemAndValues(URI_URN, entryUuid))
                .returnBundle(Bundle.class).execute();
        metaDataAssert(!result.getEntry().isEmpty(), ValidationMessage.UNRESOLVED_REFERENCE, entryUuid);
        return (MhdFolder)result.getEntryFirstRep().getResource();
    }


    private List<DocumentReferenceRelatesToComponent> createDocToDocReferences(List<Association> associations) {
        return associations.stream()
                .filter(assoc -> DOC_DOC_FHIR_ASSOCIATIONS.containsKey(assoc.getAssociationType()))
                .map(assoc -> {
                    var result = lookupExistingDocument(assoc.getTargetUuid());
                    metaDataAssert(result.getStatus().equals(DocumentReferenceStatus.CURRENT),
                            ValidationMessage.UNRESOLVED_REFERENCE, assoc.getTargetUuid());
                    var ref = new DocumentReferenceRelatesToComponent();
                    ref.setCode(DOC_DOC_FHIR_ASSOCIATIONS.get(assoc.getAssociationType()));
                    ref.setTarget(new Reference(result));
                    ref.setId(assoc.getEntryUuid());
                    return ref;
                })
                .toList();
    }


    private void evaluateDocumentReplacement(RegisterDocumentSet register, BundleBuilder builder) {
        register.getAssociations().stream().filter(assoc -> assoc.getAssociationType() == AssociationType.REPLACE)
                .forEach(assoc -> {
                    var replacingDoc = register.getDocumentEntries().stream()
                            .filter(doc -> doc.getEntryUuid().equals(assoc.getSourceUuid()))
                            .findFirst().map(doc -> documentMapper.apply(doc, emptyList()))
                            .orElseThrow(() -> new XDSMetaDataException(ValidationMessage.UNRESOLVED_REFERENCE,
                                    assoc.getSourceUuid()));
                    var replacePreviousDocument = replacePreviousDocument(assoc.getTargetUuid(),
                            replacingDoc);
                    replaceFolderAssocations(replacePreviousDocument, replacingDoc).forEach(builder::addTransactionUpdateEntry);
                    builder.addTransactionUpdateEntry(replacePreviousDocument);
                });
    }

    /**
     * Takeover folder relationsship from replaced document to the new document.
     *
     * @param replacePreviousDocument
     * @param replacingDoc
     * @return Folders to update.
     */
    private List<MhdFolder> replaceFolderAssocations(DocumentReference replacePreviousDocument,
            DocumentReference replacingDoc) {
        var folderResult = client.search().forResource(MhdFolder.class)
                .withProfile(MappingSupport.MHD_COMPREHENSIVE_FOLDER_PROFILE)
                .where(ListResource.CODE.exactly().codings(MhdFolder.FOLDER_CODEING.getCodingFirstRep()))
                .where(ListResource.ITEM.hasId(replacePreviousDocument.getId())).returnBundle(Bundle.class).execute();
        return folderResult.getEntry().stream().map(BundleEntryComponent::getResource).map(MhdFolder.class::cast)
                .map(folder -> {
                    var ref = new ListEntryComponent(new Reference(replacingDoc));
                    ref.setId(new URN(UUID.randomUUID()).toString());
                    folder.setDate(new Date());
                    folder.addEntry(ref);
                    return folder;
                }).toList();
    }

    /**
     * Build the references for the given assocations, where the sourceId is ony of sourceId and the target is one of the docs from the
     * documentMap.
     *
     * @param associations
     * @param xdsObjectMap
     * @param sourceId
     * @return the List of FHIR references.
     */
    private List<ListEntryComponent> createReferences(List<Association> associations, Map<String, ? extends XDSMetaClass> xdsObjectMap,
            List<String> sourceId) {
        return associations.stream()
                .filter(assoc -> AssociationType.HAS_MEMBER.equals(assoc.getAssociationType()))
                .filter(assoc -> sourceId.contains(assoc.getSourceUuid()))
                .filter(assoc -> xdsObjectMap.containsKey(assoc.getTargetUuid()))
                .map(assoc -> {
                    var metaClass = xdsObjectMap.get(assoc.getTargetUuid());
                    var refType = metaClass instanceof DocumentEntry ? DocumentReference.class.getSimpleName() : ListResource.class.getSimpleName();
                    return createReference(assoc, refType);
                })
                .toList();
    }

    private ListEntryComponent createReference(Association assoc, String refType) {
        var item = new Reference(
                new IdType(refType, assoc.getTargetUuid()));
        var id = new Identifier();
        id.setSystem(MappingSupport.URI_URN);
        id.setValue(assoc.getEntryUuid());
        id.setUse(IdentifierUse.SECONDARY);
        item.setIdentifier(id);
        var ref = new ListEntryComponent(item);
        ref.setId(assoc.getEntryUuid());
        return ref;
    }

    private List<ListEntryComponent> createReferences(List<Association> associations,
            List<String> sourceId) {
        var associationMap = associations.stream()
                .collect(Collectors.toMap(Association::getEntryUuid, Function.identity()));
        return associations.stream()
                .filter(assoc -> sourceId.contains(assoc.getSourceUuid()))
                .filter(assoc -> associationMap.containsKey(assoc.getTargetUuid()))
                .map(this::createReference)
                .toList();
    }

    private ListEntryComponent createReference(Association assoc) {
        var ref = new Reference();
        var id = new Identifier();
        id.setSystem(MappingSupport.URI_URN);
        id.setValue(assoc.getTargetUuid());
        ref.setIdentifier(id);
        var refEntry = new ListEntryComponent(ref);
        refEntry.setId(assoc.getEntryUuid());
        return refEntry;
    }



    /**
     * Perform replace according to https://profiles.ihe.net/ITI/TF/Volume2/ITI-42.html#3.42.4.1.3.5
     *
     * @param entryUuid
     * @param replacingDocument
     * @return Replaced document with status set to superseded
     */
    private DocumentReference replacePreviousDocument(String entryUuid, DocumentReference replacingDocument) {
        var replacedDocument = lookupExistingDocument(entryUuid);
        metaDataAssert(replacedDocument.getStatus() == DocumentReferenceStatus.CURRENT,
                ValidationMessage.DEPRECATED_OBJ_CANNOT_BE_TRANSFORMED);
        if (!replacedDocument.getSubject().getReference().endsWith(replacingDocument.getSubject().getReference())) {
            log.debug("Replacing and replaced document do not have the same patientid {} and {}",
                    replacedDocument.getSubject().getReference(), replacingDocument.getSubject().getReference());
            throw new XDSMetaDataException(ValidationMessage.DOC_ENTRY_PATIENT_ID_WRONG);
        }
        replacedDocument.setStatus(DocumentReferenceStatus.SUPERSEDED);
        return replacedDocument;
    }


    private DocumentReference lookupExistingDocument(String... ids) {
        var result = client.search().forResource(DocumentReference.class).count(1)
                .where(DocumentReference.IDENTIFIER.exactly().systemAndValues(URI_URN, ids))
                .cacheControl(new CacheControlDirective().setNoCache(true).setNoStore(true))
                .returnBundle(Bundle.class).execute();
        metaDataAssert(!result.getEntry().isEmpty(), ValidationMessage.UNRESOLVED_REFERENCE, Arrays.toString(ids));
        return (DocumentReference)result.getEntryFirstRep().getResource();
    }

    private void validateKnownRepository(RegisterDocumentSet register) {
        register.getDocumentEntries()
                .forEach(doc -> metaDataAssert(
                        registryConfig.getRepositoryEndpoint().containsKey(doc.getRepositoryUniqueId()),
                        ValidationMessage.UNKNOWN_REPOSITORY_ID, doc.getRepositoryUniqueId()));
    }

    /**
     * Set entryUUID and availability Status.
     *
     * @param xdsObject
     * @param associations
     */
    private void assignRegistryValues(XDSMetaClass xdsObject, List<Association> associations) {
        if (!xdsObject.getEntryUuid().startsWith(MappingSupport.UUID_URN)) {
            var previousIdentifier = xdsObject.getEntryUuid();
            xdsObject.assignEntryUuid();
            associations.stream().forEach(assoc -> {
                assoc.setSourceUuid(assoc.getSourceUuid().replace(previousIdentifier, xdsObject.getEntryUuid()));
                assoc.setTargetUuid(assoc.getTargetUuid().replace(previousIdentifier, xdsObject.getEntryUuid()));
            });
        }
        xdsObject.setAvailabilityStatus(AvailabilityStatus.APPROVED);
    }

    /**
     * Assign generated entryUuid for Associations.
     *
     * @param associations
     */
    private void assignRegistryValues(List<Association> associations) {
        for (var assoc : associations) {
        	if (assoc.getEntryUuid() == null) {
        		assoc.assignEntryUuid();
        	}
            if (!assoc.getEntryUuid().startsWith(MappingSupport.UUID_URN)) {
            	var previousIdentifier = assoc.getEntryUuid();
            	assoc.assignEntryUuid();
                associations.stream().forEach(as -> {
                    as.setSourceUuid(as.getSourceUuid().replace(previousIdentifier, assoc.getEntryUuid()));
                    as.setTargetUuid(as.getTargetUuid().replace(previousIdentifier, assoc.getEntryUuid()));
                });
            }
        }
    }

    /**
     * Assign the ID of the fhir patient resource to the xds object.
     *
     * @param xdsObject
     */
    private void assignPatientId(XDSMetaClass xdsObject) {
        var result = client.search().forResource(Patient.class).count(1)
                .where(Patient.IDENTIFIER.exactly().systemAndIdentifier(
                        OID_URN + xdsObject.getPatientId().getAssigningAuthority().getUniversalId(),
                        xdsObject.getPatientId().getId()))
                .returnBundle(Bundle.class).cacheControl(new CacheControlDirective().setNoCache(true).setNoStore(true))
                .execute();
        metaDataAssert(!result.getEntry().isEmpty(), ValidationMessage.UNKNOWN_PATIENT_ID);
        xdsObject.getPatientId().setId(result.getEntryFirstRep().getResource().getIdPart());
    }

}
