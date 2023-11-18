package org.openehealth.app.xdstofhir.registry.query;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.util.BundleUtil;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.openehealth.app.xdstofhir.registry.common.fhir.MhdSubmissionSet;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.DocumentEntry;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.ObjectReference;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.SubmissionSet;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Version;
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

        var resultBundle = visitor.getFhirQuery().execute();

        var response = new QueryResponse(Status.SUCCESS);

        var xdsDocuments = getDocumentsFrom(resultBundle);
        var submissionSets = getSubmissionSetsFrom(resultBundle);

        while (resultBundle.getLink(IBaseBundle.LINK_NEXT) != null) {
            resultBundle = client.loadPage().next(resultBundle).execute();
            xdsDocuments.addAll(getDocumentsFrom(resultBundle));
            submissionSets.addAll(getSubmissionSetsFrom(resultBundle));
            if (xdsDocuments.size() > maxResultCount) {
                response.setStatus(Status.PARTIAL_SUCCESS);
                response.setErrors(Collections.singletonList(new ErrorInfo(ErrorCode.TOO_MANY_RESULTS,
                        "Result exceed maximum of " + maxResultCount, Severity.WARNING, null, null)));
                break;
            }
        }

        if (query.getReturnType().equals(QueryReturnType.LEAF_CLASS)) {
            response.setDocumentEntries(xdsDocuments);
            response.setSubmissionSets(submissionSets);
        } else {
            response.setReferences(Stream.concat(xdsDocuments.stream(), submissionSets.stream())
                    .map(xdsObject -> new ObjectReference(xdsObject.getEntryUuid()))
                    .collect(Collectors.toList()));
        }

        return response;
    }


    private List<DocumentEntry> getDocumentsFrom(Bundle resultBundle) {
        var listOfResources = BundleUtil.toListOfResourcesOfType(client.getFhirContext(),
                resultBundle, DocumentReference.class);
        var xdsDocuments = listOfResources.stream()
                .map(documentMapper)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        xdsDocuments.forEach(assignDefaultVersioning());
        return xdsDocuments;
    }

    private List<SubmissionSet> getSubmissionSetsFrom(Bundle resultBundle) {
        var listOfResources = BundleUtil.toListOfResourcesOfType(client.getFhirContext(),
                resultBundle, MhdSubmissionSet.class);
        var xdsSubmissions = listOfResources.stream()
                .map(submissionMapper)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return xdsSubmissions;
    }

    /**
     * ebRIM chapter 2.5.1 requires versionInfo and lid to be set.
     *
     * @return consumer setting proper defaults for lid and versionInfo
     */
    private Consumer<? super DocumentEntry> assignDefaultVersioning() {
        return doc -> {
          doc.setLogicalUuid(doc.getEntryUuid());
          doc.setVersion(DEFAULT_VERSION);
        };
    }

}
