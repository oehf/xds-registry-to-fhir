package org.openehealth.app.xdstofhir.registry.remove;

import org.openehealth.ipf.commons.ihe.xds.core.requests.RemoveMetadata;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Response;

@FunctionalInterface
public interface Iti62Service {
    Response remove(RemoveMetadata metadataToRemove);
}
