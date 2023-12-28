package org.openehealth.app.xdstofhir.registry.common;

import java.net.URISyntaxException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import lombok.experimental.UtilityClass;
import org.hl7.fhir.r4.model.DocumentReference.DocumentRelationshipType;
import org.hl7.fhir.r4.model.codesystems.DocumentReferenceStatus;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.Oid;
import org.openehealth.ipf.commons.core.URN;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AssociationType;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.AvailabilityStatus;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Timestamp.Precision;

@UtilityClass
public class MappingSupport {
    public static final String OID_URN = "urn:oid:";
    public static final String UUID_URN = "urn:uuid:";
    public static final String XDS_URN = "urn:ihe:xds:";
    public static final String URI_URN = "urn:ietf:rfc:3986";
    public static final String MHD_COMPREHENSIVE_PROFILE = "https://profiles.ihe.net/ITI/MHD/StructureDefinition/IHE.MHD.Comprehensive.DocumentReference";
    public static final String MHD_COMPREHENSIVE_SUBMISSIONSET_PROFILE = "https://profiles.ihe.net/ITI/MHD/StructureDefinition/IHE.MHD.Comprehensive.SubmissionSet";
    public static final String MHD_COMPREHENSIVE_FOLDER_PROFILE = "https://profiles.ihe.net/ITI/MHD/StructureDefinition/IHE.MHD.Comprehensive.Folder";

    public static final Map<Precision, TemporalPrecisionEnum> PRECISION_MAP_FROM_XDS = new EnumMap<>(
            Map.of(Precision.DAY, TemporalPrecisionEnum.DAY,
                    Precision.HOUR, TemporalPrecisionEnum.SECOND,
                    Precision.MINUTE, TemporalPrecisionEnum.SECOND,
                    Precision.MONTH, TemporalPrecisionEnum.MONTH,
                    Precision.SECOND, TemporalPrecisionEnum.SECOND,
                    Precision.YEAR, TemporalPrecisionEnum.YEAR));
    public static final Map<TemporalPrecisionEnum, Precision> PRECISION_MAP_FROM_FHIR = PRECISION_MAP_FROM_XDS
            .entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (x, y) -> y, LinkedHashMap::new));

    public static final Map<AssociationType, DocumentRelationshipType> DOC_DOC_FHIR_ASSOCIATIONS = new EnumMap<>(
            Map.of(AssociationType.APPEND, DocumentRelationshipType.APPENDS,
                    AssociationType.REPLACE, DocumentRelationshipType.REPLACES,
                    AssociationType.SIGNS, DocumentRelationshipType.SIGNS,
                    AssociationType.TRANSFORM, DocumentRelationshipType.TRANSFORMS));

    public static final Map<DocumentRelationshipType, AssociationType> DOC_DOC_XDS_ASSOCIATIONS = DOC_DOC_FHIR_ASSOCIATIONS
            .entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (x, y) -> y, LinkedHashMap::new));


    public static final Map<AvailabilityStatus, DocumentReferenceStatus> STATUS_MAPPING_FROM_XDS = new EnumMap<>(
            Map.of(AvailabilityStatus.APPROVED, DocumentReferenceStatus.CURRENT,
                    AvailabilityStatus.DEPRECATED, DocumentReferenceStatus.SUPERSEDED));
    public static final Map<DocumentReferenceStatus, AvailabilityStatus> STATUS_MAPPING_FROM_FHIR = STATUS_MAPPING_FROM_XDS
            .entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (x, y) -> y, LinkedHashMap::new));


    public static String toUrnCoded(String value) {
        String adaptedValue = value;
        try {
            URN.create(adaptedValue);
        } catch (URISyntaxException invalidUrn) {
            try {
                new Oid(value);
                adaptedValue = OID_URN + value;
            } catch (GSSException invalidOid) {
                try {
                    UUID.fromString(value);
                    adaptedValue = OID_URN + value;
                } catch (IllegalArgumentException invalidUuid) {
                    adaptedValue = XDS_URN + value;
                }
            }
        }
        return adaptedValue;
    }

    public static String urnDecodedScheme(String urnCodedSystem) {
        return urnCodedSystem
                .replace(OID_URN, "")
                .replace(UUID_URN, "")
                .replace(XDS_URN, "");
    }

}
