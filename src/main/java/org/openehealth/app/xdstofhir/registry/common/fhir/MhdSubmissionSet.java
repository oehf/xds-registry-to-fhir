package org.openehealth.app.xdstofhir.registry.common.fhir;

import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.model.api.annotation.Extension;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.ListResource;

@ResourceDef(name = "List", profile = "https://profiles.ihe.net/ITI/MHD/StructureDefinition/IHE.MHD.Comprehensive.SubmissionSet")
public class MhdSubmissionSet extends ListResource {
    private static final long serialVersionUID = 6730967324453182475L;

    private static final CodeableConcept SUBMISSIONSET_CODEING = new CodeableConcept(
            new Coding("https://profiles.ihe.net/ITI/MHD/CodeSystem/MHDlistTypes", "submissionset",
                    "SubmissionSet as a FHIR List"));

    public MhdSubmissionSet() {
        super();
        setCode(SUBMISSIONSET_CODEING);
        setStatus(ListStatus.CURRENT);
        setMode(ListMode.WORKING);
    }

    @Child(name = "designationType", type = {CodeableConcept.class}, order=1, min=1, max=1, modifier=false, summary=false)
    @Extension(url = "https://profiles.ihe.net/ITI/MHD/StructureDefinition/ihe-designationType", definedLocally = false, isModifier = false)
    @Description(shortDefinition = "Clinical code of the List")
    @Getter @Setter
    private CodeableConcept designationType;

    @Child(name = "sourceId", type = {Identifier.class}, order=2, min=1, max=1, modifier=false, summary=false)
    @Extension(url = "https://profiles.ihe.net/ITI/MHD/StructureDefinition/ihe-sourceId", definedLocally = false, isModifier = false)
    @Description(shortDefinition="Publisher organization identity of the SubmissionSet" )
    @Getter @Setter
    protected Identifier sourceId;


}
