package org.openehealth.app.xdstofhir.registry.query;

import org.openehealth.ipf.commons.ihe.xds.core.requests.QueryRegistry;
import org.openehealth.ipf.commons.ihe.xds.core.responses.QueryResponse;

@FunctionalInterface
public interface Iti18Service {
    public QueryResponse processQuery(QueryRegistry query);

}