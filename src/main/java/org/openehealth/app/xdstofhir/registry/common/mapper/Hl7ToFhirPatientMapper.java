package org.openehealth.app.xdstofhir.registry.common.mapper;

import static org.openehealth.app.xdstofhir.registry.common.MappingSupport.OID_URN;

import java.util.function.Function;

import ca.uhn.hl7v2.util.Terser;
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

@Component
public class Hl7ToFhirPatientMapper  implements Function<Terser, Patient> {

    @Override
    @SneakyThrows
    public Patient apply(Terser terser) {
        var patientId = terser.get("/.PID-3-1");
        var assigningAuthority = terser.get("/.PID-3-4-2");
        var patient = new Patient();
        patient.setActive(true);
        var identifier = new Identifier();
        identifier.setSystem(OID_URN + assigningAuthority);
        identifier.setValue(patientId);
        patient.addIdentifier(identifier);
        var patientFirstName = terser.get("/.PID-5-1");
        var patientFamiliyName = terser.get("/.PID-5-2");
        var humanName = new HumanName();
        humanName.setFamily(patientFamiliyName);
        humanName.addGiven(patientFirstName);
        patient.addName(humanName);
        return patient;
    }

}
