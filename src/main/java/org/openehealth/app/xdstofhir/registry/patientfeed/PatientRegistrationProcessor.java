package org.openehealth.app.xdstofhir.registry.patientfeed;

import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.OID_URN;

import java.util.function.Function;

import ca.uhn.fhir.rest.api.CacheControlDirective;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PatientRegistrationProcessor implements Iti8Service {

    private final IGenericClient client;
    private final Function<Terser, Patient> patientMapper;

    @Override
    @SneakyThrows
    public Message registerPatient(Message patientFeed) {
        var terser = new Terser(patientFeed);
        var patientSystemId = OID_URN + terser.get("/.PID-3-4-2");
        var patientId = terser.get("/.PID-3-1");
        var patientSearch = client.search().forResource(Patient.class).count(1)
                .where(Patient.IDENTIFIER.exactly().systemAndIdentifier(
                        patientSystemId,patientId))
                .cacheControl(new CacheControlDirective().setNoCache(true).setNoStore(true))
                .returnBundle(Bundle.class).execute();
        if (patientSearch.getEntry().isEmpty()) {
            var patientCreated = client.create().resource(patientMapper.apply(terser)).execute();
            log.info("Create FHIR patient {}", patientCreated.getId());
        } else {
            log.info("FHIR patient {} already present", patientSearch.getEntryFirstRep().getResource().getId());
        }

        return patientFeed.generateACK();
    }

}
