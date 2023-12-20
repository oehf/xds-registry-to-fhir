package org.openehealth.app.xdstofhir.registry.query;

import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.OID_URN;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.URI_URN;
import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.toUrnCoded;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import ca.uhn.fhir.rest.gclient.DateClientParam;
import ca.uhn.fhir.rest.gclient.ICriterion;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import lombok.experimental.UtilityClass;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.ListResource;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.codesystems.DocumentReferenceStatus;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AvailabilityStatus;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Code;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.ReferenceId;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.TimeRange;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Version;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.XDSMetaClass;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetByIdQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.GetFromDocumentQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.PatientIdBasedStoredQuery;
import org.openehealth.ipf.commons.ihe.xds.core.requests.query.QueryList;

@UtilityClass
public class StoredQueryMapper {
    private static final Version DEFAULT_VERSION = new Version("1");

    public static void map(TimeRange dateRange, DateClientParam date, IQuery<Bundle> fhirQuery) {
        if (dateRange != null) {
            if (dateRange.getFrom() != null) {
                fhirQuery.where(date.afterOrEquals().millis(Date.from(dateRange.getFrom().getDateTime().toInstant())));
            }
            if (dateRange.getTo() != null) {
                fhirQuery.where(date.before().millis(Date.from(dateRange.getTo().getDateTime().toInstant())));
            }
        }
    }

    public static void map(List<AvailabilityStatus> status, IQuery<Bundle> fhirQuery) {
        List<String> fhirStatus = status.stream()
                .map(MappingSupport.STATUS_MAPPING_FROM_XDS::get)
                .filter(Objects::nonNull)
                .map(DocumentReferenceStatus::toCode)
                .collect(Collectors.toList());
        if (!fhirStatus.isEmpty())
            fhirQuery.where(DocumentReference.STATUS.exactly().codes(fhirStatus));
    }

    public static void map (QueryList<Code> codes, TokenClientParam param, IQuery<Bundle> fhirQuery) {
        if (codes != null)
            codes.getOuterList().forEach(eventList -> map(eventList, param, fhirQuery));
    }

    public static void map(List<Code> codes, TokenClientParam param, IQuery<Bundle> fhirQuery) {
        if (codes != null && !codes.isEmpty()) {
            fhirQuery.where(param.exactly()
                    .codings(codes.stream()
                            .map(xdsCode -> new Coding(toUrnCoded(xdsCode.getSchemeName()), xdsCode.getCode(), null))
                            .collect(Collectors.toList()).toArray(new Coding[0])));
        }
    }

    public static String entryUuidFrom(IBaseResource resource) {
        List<Identifier> identifier;
        if (resource instanceof DocumentReference) {
            identifier = ((DocumentReference) resource).getIdentifier();
        } else if (resource instanceof ListResource) {
            identifier = ((ListResource) resource).getIdentifier();
        } else {
            return null;
        }
        return identifier.stream().filter(id -> Identifier.IdentifierUse.OFFICIAL.equals(id.getUse())).findFirst()
                .orElse(identifier.stream().findFirst().orElse(new Identifier())).getValue();
    }


    public static List<String> urnIdentifierList(GetFromDocumentQuery query) {
        List<String> searchIdentifiers = new ArrayList<String>();
        if (query.getUniqueId() != null) {
            searchIdentifiers.add(query.getUniqueId());
        }
        if (query.getUuid() != null) {
            searchIdentifiers.add(query.getUuid());
        }
        searchIdentifiers = searchIdentifiers.stream().map(MappingSupport::toUrnCoded).collect(Collectors.toList());
        return searchIdentifiers;
    }

    public static ICriterion<?> buildIdentifierQuery(GetByIdQuery query, TokenClientParam param) {
        var searchIdentifiers = new ArrayList<String>();
        if (query.getUniqueIds() != null) {
            searchIdentifiers.addAll(query.getUniqueIds());
        }
        if (query.getUuids() != null) {
            searchIdentifiers.addAll(query.getUuids());
        }
        var identifier = param.exactly().systemAndValues(URI_URN,
                searchIdentifiers.stream().map(MappingSupport::toUrnCoded).collect(Collectors.toList()));
        return identifier;
    }


    public static void mapPatientIdToQuery(PatientIdBasedStoredQuery query, IQuery<Bundle> fhirQuery) {
        var patientId = query.getPatientId();

        var identifier = DocumentReference.PATIENT
                .hasChainedProperty(Patient.IDENTIFIER.exactly().systemAndIdentifier(
                        OID_URN + patientId.getAssigningAuthority().getUniversalId(), patientId.getId()));
        fhirQuery.where(identifier);
    }

    public static String asSearchToken(ReferenceId id) {
        if (id.getAssigningAuthority() != null) {
            return OID_URN + id.getAssigningAuthority().getUniversalId() + "|" + id.getId();
        } else {
            return id.getId();
        }
    }

    /**
     * ebRIM chapter 2.5.1 requires versionInfo and lid to be set.
     *
     * @return consumer setting proper defaults for lid and versionInfo
     */
    public static Consumer<? super XDSMetaClass> assignDefaultVersioning() {
        return meta -> {
            meta.setLogicalUuid(meta.getEntryUuid());
            meta.setVersion(DEFAULT_VERSION);
        };
    }
}
