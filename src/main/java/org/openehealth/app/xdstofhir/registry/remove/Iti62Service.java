package org.openehealth.app.xdstofhir.registry.remove;

import org.openehealth.ipf.commons.ihe.xds.core.requests.RemoveMetadata;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Response;

@FunctionalInterface
public interface Iti62Service {

    /**
     * Perform the ITI-62 processing as described by IHE for https://profiles.ihe.net/ITI/TF/Volume2/ITI-62.html
     *
     * System will collect the corresponding FHIR resources and then invoke a batch update to the fhir resource,
     * either deleting or partially updating (e.g. in case just a reference is removed) resource.
     *
     * @param metadataToRemove - the metadata remove request message.
     * @return Response with Success or Failure, depending on the operation outcome.
     */
    Response remove(RemoveMetadata metadataToRemove);
}
