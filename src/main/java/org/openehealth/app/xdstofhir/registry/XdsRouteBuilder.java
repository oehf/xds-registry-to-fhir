package org.openehealth.app.xdstofhir.registry;

import static org.openehealth.ipf.platform.camel.ihe.xds.XdsCamelValidators.iti18RequestValidator;
import static org.openehealth.ipf.platform.camel.ihe.xds.XdsCamelValidators.iti18ResponseValidator;
import static org.openehealth.ipf.platform.camel.ihe.xds.XdsCamelValidators.iti42RequestValidator;
import static org.openehealth.ipf.platform.camel.ihe.xds.XdsCamelValidators.iti42ResponseValidator;
import static org.openehealth.ipf.platform.camel.ihe.xds.XdsCamelValidators.iti61RequestValidator;
import static org.openehealth.ipf.platform.camel.ihe.xds.XdsCamelValidators.iti61ResponseValidator;
import static org.openehealth.ipf.platform.camel.ihe.xds.XdsCamelValidators.iti62RequestValidator;
import static org.openehealth.ipf.platform.camel.ihe.xds.XdsCamelValidators.iti62ResponseValidator;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.openehealth.app.xdstofhir.registry.patientfeed.Iti8Service;
import org.openehealth.app.xdstofhir.registry.query.Iti18Service;
import org.openehealth.app.xdstofhir.registry.register.Iti42Service;
import org.openehealth.app.xdstofhir.registry.register.Iti61Service;
import org.openehealth.app.xdstofhir.registry.remove.Iti62Service;
import org.openehealth.ipf.commons.ihe.xds.core.validate.XDSMetaDataException;
import org.openehealth.ipf.platform.camel.ihe.mllp.PixPdqCamelValidators;
import org.openehealth.ipf.platform.camel.ihe.xds.XdsCamelValidators;
import org.springframework.stereotype.Component;

@Component
public class XdsRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // Relax logging from Error to warn without stacktrace for validation failures
        onException(XDSMetaDataException.class)
            .handled(false)
            .log(LoggingLevel.WARN, "Reject invalid request due to: ${exception.message}")
            .logExhausted(false);

        from("{{xds.endpoint.iti18}}")
            .routeId("iti18Route")
            .process(iti18RequestValidator())
            .bean(Iti18Service.class)
            .process(iti18ResponseValidator());

        from("{{xds.endpoint.iti42}}")
            .routeId("iti42Route")
            .process(iti42RequestValidator())
            .bean(Iti42Service.class)
            .process(iti42ResponseValidator());
        
        from("{{xds.endpoint.iti61}}")
	        .routeId("iti61Route")
	        .process(iti61RequestValidator())
	        .bean(Iti61Service.class)
	        .process(iti61ResponseValidator());

        from("{{xds.endpoint.iti62}}")
            .routeId("iti62Route")
            .process(iti62RequestValidator())
            .bean(Iti62Service.class)
            .process(iti62ResponseValidator());

        from("{{xds.endpoint.iti8}}")
            .routeId("iti8Route")
            .process(PixPdqCamelValidators.iti8RequestValidator())
            .bean(Iti8Service.class)
            .process(PixPdqCamelValidators.iti8ResponseValidator());

    }

}
