package no.fintlabs.repository.filters;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collection;

@Getter
@AllArgsConstructor
public class InstanceStorageStatusQueryFilter {

    public static final InstanceStorageStatusQueryFilter EMPTY =
            new InstanceStorageStatusQueryFilter(null, null);

    private final Collection<String> instanceStorageStatusNames;
    private final Boolean neverStored;
}
