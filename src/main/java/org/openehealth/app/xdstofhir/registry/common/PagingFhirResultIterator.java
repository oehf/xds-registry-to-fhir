package org.openehealth.app.xdstofhir.registry.common;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.util.BundleUtil;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;

/**
 * Lazy Fhir Page Iterator. Fetches the next result page when the iterator has loaded the last element.
 *
 * @param <T>
 */
@RequiredArgsConstructor
public class PagingFhirResultIterator<T extends DomainResource> implements Iterator<T> {

    @NonNull
    private Bundle resultBundle;
    private final Class<T> resultTypeClass;
    private int currentIteratorIndex = 0;
    private final IGenericClient client;

    @Override
    public boolean hasNext() {
        if (currentIteratorIndex == getResourcesFromBundle().size()) {
            nextPageIfAvailable();
        }
        return currentIteratorIndex < getResourcesFromBundle().size();
    }

    private void nextPageIfAvailable() {
        if (resultBundle.getLink(IBaseBundle.LINK_NEXT) != null) {
            resultBundle = client.loadPage().next(resultBundle).execute();
            currentIteratorIndex = 0;
        }
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No more elements present.");
        }
        T result = getResourcesFromBundle().get(currentIteratorIndex);
        currentIteratorIndex++;
        return result;
    }

    private List<T> getResourcesFromBundle(){
        return BundleUtil.toListOfResourcesOfType(client.getFhirContext(),
                resultBundle, resultTypeClass);
    }
}

