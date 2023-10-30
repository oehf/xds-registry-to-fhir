package org.openehealth.app.xdstofhir.registry;

import static org.openehealth.ipf.platform.camel.ihe.xds.XdsCamelValidators.iti18RequestValidator;
import static org.openehealth.ipf.platform.camel.ihe.xds.XdsCamelValidators.iti18ResponseValidator;
import static org.openehealth.ipf.platform.camel.ihe.xds.XdsCamelValidators.iti42RequestValidator;
import static org.openehealth.ipf.platform.camel.ihe.xds.XdsCamelValidators.iti42ResponseValidator;

import org.apache.camel.builder.RouteBuilder;
import org.openehealth.app.xdstofhir.registry.patientfeed.Iti8Service;
import org.openehealth.app.xdstofhir.registry.query.Iti18Service;
import org.openehealth.app.xdstofhir.registry.register.Iti42Service;
import org.openehealth.ipf.platform.camel.ihe.mllp.PixPdqCamelValidators;
import org.springframework.stereotype.Component;

@Component
public class XdsRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {
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

        from("{{xds.endpoint.iti8}}")
            .routeId("iti8Route")
            .process(PixPdqCamelValidators.iti8RequestValidator())
            .bean(Iti8Service.class)
            .process(PixPdqCamelValidators.iti8ResponseValidator());

    }

}
