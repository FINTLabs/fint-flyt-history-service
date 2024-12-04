package no.fintlabs.repositories;

import io.hypersistence.utils.hibernate.type.array.internal.AbstractArrayType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.hibernate.jpa.TypedParameterValue;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class HibernateTypeUtils {

    public static <T, T2 extends AbstractArrayType<?>> TypedParameterValue mapToArrayType(
            T2 hibernateType,
            Collection<T> values,
            IntFunction<T[]> arrayGenerator
    ) {
        return new TypedParameterValue(
                hibernateType,
                Optional.ofNullable(values)
                        .map(iids -> iids
                                .stream()
                                .filter(Objects::nonNull)
                                .toArray(arrayGenerator)
                        )
                        .orElse(null)
        );
    }
}
