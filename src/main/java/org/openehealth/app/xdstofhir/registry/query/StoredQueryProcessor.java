package org.openehealth.app.xdstofhir.registry.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.DocumentReference;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdSubmissionSet;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.DocumentEntry;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.ObjectReference;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.SubmissionSet;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Version;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.XDSMetaClass;
import org.openehealth.ipf.commons.ihe.xds.core.requests.QueryRegistry;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.QueryReturnType;
import org.openehealth.ipf.commons.ihe.xds.core.responses.ErrorCode;
import org.openehealth.ipf.commons.ihe.xds.core.responses.ErrorInfo;
import org.openehealth.ipf.commons.ihe.xds.core.responses.QueryResponse;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Severity;
import org.openehealth.ipf.commons.ihe.xds.core.responses.Status;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoredQueryProcessor implements Iti18Service {
    @Value("${xds.query.max.results:1000}")
    private int maxResultCount;
    private final IGenericClient client;
    private final Function<DocumentReference, DocumentEntry> documentMapper;
    private final Function<MhdSubmissionSet, SubmissionSet> submissionMapper;
    private static final Version DEFAULT_VERSION = new Version("1");

    @Override
    public QueryResponse processQuery(QueryRegistry query) {
        var visitor = new StoredQueryVistorImpl(client);
        query.getQuery().accept(visitor);

        var response = new QueryResponse(Status.SUCCESS);

        var fhirDocuments = visitor.getDocumentsFromResult();
        var fhirSubmissions = visitor.getSubmissionSetsFrom();
        var xdsDocuments = new ArrayList<DocumentEntry>();
        var xdsSubmissionSets = new ArrayList<SubmissionSet>();

        for (DocumentReference document : fhirDocuments) {
            if (xdsDocuments.size() > maxResultCount) {
                response.setStatus(Status.PARTIAL_SUCCESS);
                response.setErrors(Collections.singletonList(new ErrorInfo(ErrorCode.TOO_MANY_RESULTS,
                        "Result exceed maximum of " + maxResultCount, Severity.WARNING, null, null)));
                break;
            }
            var xdsDoc = documentMapper.apply(document);
            if (xdsDoc != null) {
                assignDefaultVersioning().accept(xdsDoc);
                xdsDocuments.add(xdsDoc);
            }

        }

        for (MhdSubmissionSet submissionset : fhirSubmissions) {
            if (xdsDocuments.size() + xdsSubmissionSets.size() > maxResultCount) {
                response.setStatus(Status.PARTIAL_SUCCESS);
                response.setErrors(Collections.singletonList(new ErrorInfo(ErrorCode.TOO_MANY_RESULTS,
                        "Result exceed maximum of " + maxResultCount, Severity.WARNING, null, null)));
                break;
            }
            var xdsSubmission = submissionMapper.apply(submissionset);
            if (xdsSubmission != null) {
                assignDefaultVersioning().accept(xdsSubmission);
                xdsSubmissionSets.add(xdsSubmission);
            }
        }

        if (query.getReturnType().equals(QueryReturnType.LEAF_CLASS)) {
            response.setDocumentEntries(xdsDocuments);
            response.setSubmissionSets(xdsSubmissionSets);
        } else {
            response.setReferences(Stream.concat(xdsDocuments.stream(), xdsSubmissionSets.stream())
                    .map(xdsObject -> new ObjectReference(xdsObject.getEntryUuid())).collect(Collectors.toList()));
        }

        return response;
    }

    /**
     * ebRIM chapter 2.5.1 requires versionInfo and lid to be set.
     *
     * @return consumer setting proper defaults for lid and versionInfo
     */
    private Consumer<? super XDSMetaClass> assignDefaultVersioning() {
        return meta -> {
            meta.setLogicalUuid(meta.getEntryUuid());
            meta.setVersion(DEFAULT_VERSION);
        };
    }

}
