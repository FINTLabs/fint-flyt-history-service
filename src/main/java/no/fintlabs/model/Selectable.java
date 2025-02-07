package no.fintlabs.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Selectable<T> {
    private final T value;
    private final String label;
}
