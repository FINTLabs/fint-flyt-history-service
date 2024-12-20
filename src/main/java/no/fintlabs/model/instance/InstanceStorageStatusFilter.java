package no.fintlabs.model.instance;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collection;

@Getter
@AllArgsConstructor
public class InstanceStorageStatusFilter {
    private final Collection<String> instanceStorageStatusNames;
    private final Boolean neverStored;
}
