package org.openehealth.app.xdstofhir.registry.common;

import org.apache.cxf.rt.security.SecurityConstants;
import org.apache.cxf.ws.security.wss4j.WSS4JInInterceptor;
import org.apache.wss4j.common.ConfigurationConstants;
import org.apache.wss4j.common.crypto.CertificateStore;

import java.util.HashMap;
import java.util.Map;

public class Wss4jConfigurator {
    public static Map<String, Object> createWss4jProperties(RegistryConfiguration.XuaConfiguration xua) {
        return Map.of(SecurityConstants.AUDIENCE_RESTRICTIONS, xua.getAudienceRestriction(), SecurityConstants.SIGNATURE_CRYPTO,
                new CertificateStore(xua.getTrustedIdentityProviderCertificates()));
    }

    public static WSS4JInInterceptor createWss4jInterceptor(RegistryConfiguration serviceConfig) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConfigurationConstants.ACTION, ConfigurationConstants.SAML_TOKEN_SIGNED);
        WSS4JInInterceptor interceptor = new WSS4JInInterceptor(config);
        interceptor.setIgnoreActions(!serviceConfig.getXua().isEnabled());
        return interceptor;
    }

    private Wss4jConfigurator() {
        // prevent instantiation.
    }
}

