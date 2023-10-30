package org.openehealth.app.xdstofhir.registry.register;

import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.OID_URN;

import java.util.function.Consumer;
import java.util.function.Function;

import ca.uhn.fhir.rest.api.CacheControlDirective;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.util.BundleBuilder;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Patient;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.app.xdstofhir.registry.common.RegistryConfiguration;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.DocumentEntry;
import org.openehealth.ipf.commons.ihe.xds.core.requests.RegisterDocumentSet;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Response;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Status;
import org.openehealth.ipf.commons.ihe.xds.core.validate.ValidationMessage;
import org.openehealth.ipf.commons.ihe.xds.core.validate.XDSMetaDataException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegisterDocumentsProcessor implements Iti42Service {
    private final IGenericClient client;
    private final Function<DocumentEntry, DocumentReference> documentMapper;
    private final RegistryConfiguration registryConfig;

    @Override
    public Response processRegister(RegisterDocumentSet register) {
        BundleBuilder builder = new BundleBuilder(client.getFhirContext());

        validateKnownRepository(register);
        register.getDocumentEntries().forEach(this::assignEntryUuid);
        register.getDocumentEntries().forEach(assignPatientId());
        register.getDocumentEntries().forEach(doc -> builder.addTransactionCreateEntry(documentMapper.apply(doc)));

        // Execute the transaction
        client.transaction().withBundle(builder.getBundle()).execute();

        return new Response(Status.SUCCESS);
    }

    private void validateKnownRepository(RegisterDocumentSet register) {
        register.getDocumentEntries().forEach(doc -> {
            if (!registryConfig.getRepositoryEndpoint().containsKey(doc.getRepositoryUniqueId())) {
                throw new XDSMetaDataException(ValidationMessage.UNKNOWN_REPOSITORY_ID, doc.getRepositoryUniqueId());
            }
        });
    }

    private void assignEntryUuid(DocumentEntry doc) {
        if (!doc.getEntryUuid().startsWith(MappingSupport.UUID_URN)) {
            doc.assignEntryUuid();
        }
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
