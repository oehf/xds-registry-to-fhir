package org.openehealth.app.xdstofhir.registry.common;

import java.util.Map;

import org.apache.cxf.rt.security.SecurityConstants;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.wss4j.common.ConfigurationConstants;
import org.apache.wss4j.common.crypto.CertificateStore;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Wss4jConfigurator {
    public static Map<String, Object> createWss4jProperties(RegistryConfiguration.XuaConfiguration xua) {
        return Map.of(SecurityConstants.AUDIENCE_RESTRICTIONS, xua.getAudienceRestriction(), SecurityConstants.SIGNATURE_CRYPTO,
                new CertificateStore(xua.getTrustedIdentityProviderCertificates()));
    }

    public static WSS4JInInterceptor createWss4jInterceptor(RegistryConfiguration serviceConfig) {
        return new WSS4JInInterceptor(Map.of(ConfigurationConstants.ACTION, ConfigurationConstants.SAML_TOKEN_SIGNED));
    }
}

