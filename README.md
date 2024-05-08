# IHE XDS.b to FHIR adapter

## Introduction
This project illustrate the usage of certain [IPF](https://github.com/oehf/ipf) components to build an [IHE document registry](https://profiles.ihe.net/ITI/TF/Volume1/ch-10.html#10.1) using a [FHIR R4](https://hl7.org/fhir/R4/index.html) server as back-end for data persistence.

Target goal is a blueprint project to see how [IPF](https://github.com/oehf/ipf) components can be used to build a spring-boot application.

![XDS-to-fhir](src/doc/xds-to-fhir-registry_integration.png)

## Design principals
* [Spring-boot](https://spring.io/projects/spring-boot) is used as base framework with IPF's spring-boot starter's
* The usage of [Apache Camel](https://camel.apache.org/) API's was reduced to a minimum. Message Exchange's use the [pojo processing feature of camel](https://camel.apache.org/manual/pojo-producing.html#_hiding_the_camel_apis_from_your_code)
* Implementation try to stay as simple as possible to allow a blueprint (Design Principles DRY and KISS)
* Mapping of XDS to FHIR R4 and vice versa is aligned on [IHE MHD](https://profiles.ihe.net/ITI/MHD/)

## Features
* [ITI-42](https://profiles.ihe.net/ITI/TF/Volume2/ITI-42.html) to register documents to the registry  
(default endpoint: http://localhost:8081/services/registry/iti42)
* [ITI-18](https://profiles.ihe.net/ITI/TF/Volume2/ITI-18.html) to query documents from the registry  
(default endpoint: http://localhost:8081/services/registry/iti18)
* [ITI-62](https://profiles.ihe.net/ITI/TF/Volume2/ITI-62.html) to remove document metadata from the registry  
(default endpoint: http://localhost:8081/services/registry/iti62)
* [ITI-8](https://profiles.ihe.net/ITI/TF/Volume2/ITI-8.html) to receive a patient-identity-feed and make sure the patient exists  
(default endpoint: MLLP Port 2575)

## FHIR Server Compatibility

This XDS registry requires a full featured FHIR R4 server. 
The following were tested so far:

* [Firely Server](https://fire.ly/products/firely-server/)
* [HAPI FHIR](https://github.com/hapifhir/hapi-fhir) (Smile CDR) *
* [Microsoft FHIR Server](https://github.com/microsoft/fhir-server) *

\* *Because ":identifier" modifier search is not yet supported by these (HAPI + MS) FHIR Server, ITI-62 and FindDocumentsByReferenceId are known to be not working*

## Build

Build (requires Maven 3.9 and Java 21)

```
mvn clean install
```



## Tests
A small integration test illustrate a XDS roundtrip with the official [HAPI test server](https://hapi.fhir.org/).

Run integration tests against hapi fhir server:

```
mvn failsafe:integration-test -Pit-tests
```

## Run
The CI build push the container to [dockerhub](https://hub.docker.com/r/thopap/xds-registry-to-fhir). To pull the latest image an e.g. configure the public [firely](https://fire.ly/) FHIR server, run:

```
docker run -it -p8081:8081 registry.hub.docker.com/thopap/xds-registry-to-fhir -e FHIR_SERVER_BASE=https://server.fire.ly
```

Start application with maven runner:

```
mvn clean spring-boot:run -Pboot
```

## Not yet implemented
The application is not yet intended as a production ready application.

* Security concerns are not yet covered (e.g. https, mllps, SAML, audit, ...)
