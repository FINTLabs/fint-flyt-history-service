package no.fintlabs.model;

import java.util.Collection;

public interface SourceApplicationIdFilter<T extends SourceApplicationIdFilter<T>> {

    SourceApplicationIdFilterBuilder<T> toBuilder();

    Collection<Long> getSourceApplicationIds();
}
