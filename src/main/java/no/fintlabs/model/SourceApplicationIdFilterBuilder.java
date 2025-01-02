package no.fintlabs.model;

import java.util.Collection;

public interface SourceApplicationIdFilterBuilder<T extends SourceApplicationIdFilter<T>> {

    SourceApplicationIdFilterBuilder<T> sourceApplicationId(Collection<Long> sourceApplicationId);

    T build();

}
