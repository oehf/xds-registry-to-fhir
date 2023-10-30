package org.openehealth.app.xdstofhir.registry.register;

import org.openehealth.ipf.commons.ihe.xds.core.requests.RegisterDocumentSet;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Response;

@FunctionalInterface
public interface Iti42Service {
    public Response processRegister(RegisterDocumentSet register);
}
