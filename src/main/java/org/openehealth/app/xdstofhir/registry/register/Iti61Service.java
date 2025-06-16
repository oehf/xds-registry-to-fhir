package org.openehealth.app.xdstofhir.registry.register;

import org.openehealth.ipf.commons.ihe.xds.core.requests.RegisterDocumentSet;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Response;

@FunctionalInterface
public interface Iti61Service {
    /**
     * Perform the ITI-61 processing as described by IHE for https://profiles.ihe.net/ITI/TF/Volume2/ITI-61.html
     *
     * @param register - the Register message containing documents, submissionssets and folder metadata.
     * @return Response with Success or Failure, depending on the operation outcome.
     */
    public Response processRegister(RegisterDocumentSet register);
}
