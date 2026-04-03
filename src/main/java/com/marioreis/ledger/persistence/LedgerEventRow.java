package com.marioreis.ledger.persistence;

import com.marioreis.ledger.domain.LedgerEventType;

public record LedgerEventRow(
        LedgerEventType eventType,
        String payload,
        long version
) {
}

