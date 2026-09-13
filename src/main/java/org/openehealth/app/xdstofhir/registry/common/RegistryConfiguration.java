package org.openehealth.app.xdstofhir.registry.common;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "xds")
@Component
@Getter
@Setter
public class RegistryConfiguration {
    private static final String DOCUMENT_UNIQUE_ID_PLACEHOLDER = "$documentUniqueId";
    private Map<String, String> repositoryEndpoint;
    private String unknownRepositoryId;
    private String defaultHash;
    private XuaConfiguration xua;


    /**
     * Take a repositoryUniqueId and document uniqueid to build download URL where a client
     * can retrieve the document from.
     *
     * @param repositoryUniqueId
     * @param uniqueId
     * @return String containing the http download endpoint for the given document.
     */
    public String urlFrom(String repositoryUniqueId, String uniqueId) {
        return repositoryEndpoint.get(repositoryUniqueId).replace(DOCUMENT_UNIQUE_ID_PLACEHOLDER, uniqueId);
    }

    /**
     * Take a Input from a attachment URL of a FHIR DocumentReference and lookup
     * the corresponding repositoryUniqueId.
     *
     * @param url - Attachment URL
     * @return The repositoryUniqueId
     */
    public String repositoryFromUrl(String url) {
        return repositoryEndpoint.entrySet().stream()
                .filter(configItem -> url.matches(configItem.getValue().replace(DOCUMENT_UNIQUE_ID_PLACEHOLDER, ".*")))
                .map(Map.Entry::getKey).findFirst().orElse(unknownRepositoryId);
    }

    @Getter
    @Setter
    public static class XuaConfiguration {
        private boolean enabled;
        @NotBlank
        private String audienceRestriction = "https://xua.audien.ce";
        private String[] trustedIdentityProviderCertificates = new String[0];

        public X509Certificate[] getTrustedIdentityProviderCertificates() {
            try {
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                return Arrays.stream(trustedIdentityProviderCertificates)
                        .map(path -> readCertificate(factory, path))
                        .toArray(X509Certificate[]::new);
            } catch (Exception e) {
                throw new IllegalStateException("Error while processing the certificate.", e);
            }
        }

        private X509Certificate readCertificate(CertificateFactory factory, String path) {
            try (InputStream is = Files.newInputStream(Path.of(path))) {
                return (X509Certificate) factory.generateCertificate(is);
            } catch (Exception e) {
                throw new IllegalArgumentException("Certificate can't be loaded under the specified path:" + path, e);
            }
        }
    }
}
