package no.fintlabs.model;

import java.util.Collection;
import java.util.Optional;

public interface SourceApplicationIdFilter<T extends SourceApplicationIdFilter<T>> {

    SourceApplicationIdFilterBuilder<T> toBuilder();

    Optional<Collection<Long>> getSourceApplicationIds();
}
