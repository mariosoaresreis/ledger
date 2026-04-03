package com.marioreis.ledger.api;

import java.util.UUID;

public record EventReceipt(
        UUID aggregateId,
        String eventType,
        long version
) {
}

