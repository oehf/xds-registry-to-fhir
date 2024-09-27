package org.openehealth.app.xdstofhir.registry;

import jakarta.xml.ws.Dispatch;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.soap.SOAPFaultException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.util.AssertionErrors.assertEquals;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,  properties = {
	    "xds.xua.enabled=true"
	  })
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext
class XdsToFhirApplicationWebServiceTestIT {
    @LocalServerPort
    private int port;

    private void callWebServiceExpectingSoapFault(String testSoapMessageResource) throws MalformedURLException, URISyntaxException {
        URL wsdlURL = new URI(String.format("http://localhost:%d/services/registry/iti42?wsdl", port)).toURL();
        Service service = Service.create(wsdlURL, new QName("urn:ihe:iti:xds-b:2007", "DocumentRegistry_Service"));
        Dispatch<Source> dispatch = service.createDispatch(new QName("urn:ihe:iti:xds-b:2007", "DocumentRegistry_Port_Soap12"), Source.class, Service.Mode.PAYLOAD);

        Source request = new StreamSource(getClass().getResourceAsStream(testSoapMessageResource));

        SOAPFaultException soapFaultException = Assertions.assertThrowsExactly(SOAPFaultException.class, () -> {
            assertNull(dispatch.invoke(request));
        });

        assertEquals("should raise security error", "A security error was encountered when verifying the message",
                soapFaultException.getMessage());
    }

    @Test
    void testShallRejectRequestsWithoutAssertion(CapturedOutput output) throws IOException, URISyntaxException {
        callWebServiceExpectingSoapFault("/messages/iti-42.xml");

        assertThat(output.getOut(), containsString("An error was discovered processing the <wsse:Security> header"));
    }

    @Test
    void testShallRejectRequestsWithAssertionContainingInvalidCertAndSignature(CapturedOutput output) throws IOException,
            URISyntaxException {
        callWebServiceExpectingSoapFault("/messages/iti-42-invalid-token.xml");

        assertThat(output.getOut(), containsString("Could not parse certificate: java.io.IOException: Empty input"));
    }
}
