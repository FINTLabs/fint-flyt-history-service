package no.novari.flyt.history.repository.filters;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.kafka.support.JavaUtils;

import java.util.Collection;
import java.util.StringJoiner;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class InstanceStorageStatusQueryFilter {

    public static final InstanceStorageStatusQueryFilter EMPTY =
            new InstanceStorageStatusQueryFilter(null, null);

    private final Collection<String> instanceStorageStatusNames;
    private final Boolean neverStored;

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(",", "(", ")");
        JavaUtils.INSTANCE.acceptIfNotNull(
                instanceStorageStatusNames, instanceStorageStatusNames -> joiner.add("instanceStorageStatusNames=" + instanceStorageStatusNames)
        );
        JavaUtils.INSTANCE.acceptIfNotNull(
                neverStored, neverStored -> joiner.add("neverStored=" + neverStored)
        );
        return joiner.toString();
    }

}
