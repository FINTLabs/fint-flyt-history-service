package no.fintlabs;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UuidService {

    public UUID generateUuid() {
        return UUID.randomUUID();
    }

}
