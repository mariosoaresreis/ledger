package com.marioreis.ledger.service;

import com.marioreis.ledger.api.CommandResponse;

public record StoredCommandResponse(
        int httpStatus,
        CommandResponse response
) {
}

