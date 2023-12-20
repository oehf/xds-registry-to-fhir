package org.openehealth.app.xdstofhir.registry.query;

import java.util.function.Function;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.DocumentReference;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdFolder;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdSubmissionSet;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.DocumentEntry;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Folder;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.SubmissionSet;
import org.openehealth.ipf.commons.ihe.xds.core.requests.QueryRegistry;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.QueryReturnType;
import org.openehealth.ipf.commons.ihe.xds.core.responses.QueryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoredQueryProcessor implements Iti18Service {
    @Value("${xds.query.max.results:1000}")
    @Getter
    private int maxResultCount;
    private final IGenericClient client;
    private final Function<DocumentReference, DocumentEntry> documentMapper;
    private final Function<MhdSubmissionSet, SubmissionSet> submissionMapper;
    private final Function<MhdFolder, Folder> folderMapper;

    @Override
    public QueryResponse processQuery(QueryRegistry query) {
        var visitor = new StoredQueryVistorImpl(client, this, query.getReturnType().equals(QueryReturnType.OBJECT_REF));
        query.getQuery().accept(visitor);

        return visitor.getResponse();
    }

    public DocumentEntry apply(DocumentReference t) {
        return documentMapper.apply(t);
    }

    public SubmissionSet apply(MhdSubmissionSet t) {
        return submissionMapper.apply(t);
    }

    public Folder apply(MhdFolder t) {
        return folderMapper.apply(t);
    }


}
