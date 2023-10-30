package org.openehealth.app.xdstofhir.registry.patientfeed;

import ca.uhn.hl7v2.model.Message;

@FunctionalInterface
public interface Iti8Service {
    public Message registerPatient(Message patientFeed);
}
