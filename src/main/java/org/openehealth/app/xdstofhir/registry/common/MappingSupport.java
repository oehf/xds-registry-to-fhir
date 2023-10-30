package org.openehealth.app.xdstofhir.registry.common;

import java.net.URISyntaxException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import lombok.experimental.UtilityClass;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.Oid;
import org.openehealth.ipf.commons.core.URN;
import org.openehealth.ipf.commons.ihe.xds.core.metadata.Timestamp.Precision;

@UtilityClass
public class MappingSupport {
    public static String OID_URN = "urn:oid:";
    public static String UUID_URN = "urn:uuid:";
    public static String XDS_URN = "urn:ihe:xds:";
    public static String URI_URN = "urn:ietf:rfc:3986";
    public static final String MHD_COMPREHENSIVE_PROFILE = "https://profiles.ihe.net/ITI/MHD/StructureDefinition/IHE.MHD.Comprehensive.DocumentReference";

    public static final Map<Precision, TemporalPrecisionEnum> PRECISION_MAP_FROM_XDS = new EnumMap<>(
            Map.of(Precision.DAY, TemporalPrecisionEnum.DAY,
                    Precision.HOUR, TemporalPrecisionEnum.MINUTE,
                    Precision.MINUTE, TemporalPrecisionEnum.MINUTE,
                    Precision.MONTH, TemporalPrecisionEnum.MONTH,
                    Precision.SECOND, TemporalPrecisionEnum.SECOND,
                    Precision.YEAR, TemporalPrecisionEnum.YEAR));
    public static final Map<TemporalPrecisionEnum, Precision> PRECISION_MAP_FROM_FHIR = PRECISION_MAP_FROM_XDS
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
