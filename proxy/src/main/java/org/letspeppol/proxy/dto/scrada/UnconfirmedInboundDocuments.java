package org.letspeppol.proxy.dto.scrada;

import java.util.List;

public record UnconfirmedInboundDocuments(
        List<InboundDocument> results,
        int __count
) {}
