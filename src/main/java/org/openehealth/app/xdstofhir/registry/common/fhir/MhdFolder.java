package org.openehealth.app.xdstofhir.registry.common.fhir;

import java.util.List;

import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.model.api.annotation.Extension;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import lombok.Getter;
import lombok.Setter;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ListResource;
import org.openehealth.app.xdstofhir.registry.common.MappingSupport;

@ResourceDef(name = "List", profile = MappingSupport.MHD_COMPREHENSIVE_FOLDER_PROFILE)
public class MhdFolder extends ListResource {
    private static final long serialVersionUID = 6730967324453182475L;

    public static final CodeableConcept FOLDER_CODEING = new CodeableConcept(
            new Coding("https://profiles.ihe.net/ITI/MHD/CodeSystem/MHDlistTypes", "folder",
                    "Folder as a FHIR List"));

    public MhdFolder() {
        super();
        setCode(FOLDER_CODEING);
        setStatus(ListStatus.CURRENT);
        setMode(ListMode.WORKING);
    }

    @Child(name = "designationType", type = {CodeableConcept.class}, order=1, min=1, max=Child.MAX_UNLIMITED, modifier=false, summary=false)
    @Extension(url = "https://profiles.ihe.net/ITI/MHD/StructureDefinition/ihe-designationType", definedLocally = false, isModifier = false)
    @Description(shortDefinition = "Clinical code of the List")
    @Getter @Setter
    private List<CodeableConcept> designationType;

}
