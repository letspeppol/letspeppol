package org.letspeppol.proxy.mapper;

import org.letspeppol.proxy.dto.DeliveryDetails;
import org.letspeppol.proxy.dto.recommand.PeppolReceipt;
import org.letspeppol.proxy.model.AccessPoint;

import java.time.Instant;
import java.util.Map;

public class DeliveryDetailsMapper {

    // Cached details use the same provider payloads as fresh API responses.
    public static DeliveryDetails toDto(AccessPoint accessPoint, Map<String, Object> details) {
        return switch (accessPoint) {
            case RECOMMAND -> {
                PeppolReceipt receipt = PeppolReceipt.parse(
                        valueAsString(details, "receivedPeppolSignalMessage"),
                        valueAsString(details, "peppolMessageId")
                );
                yield new DeliveryDetails(null, receipt.messageId(), receipt.timestamp());
            }
            case SCRADA -> new DeliveryDetails(
                    valueAsString(details, "peppolC3SeatID"),
                    valueAsString(details, "peppolC3MessageID"),
                    valueAsInstant(details, "peppolC3Timestamp")
            );
            case NONE, LOOPBACK, E_INVOICE -> new DeliveryDetails(null, null, null);
            case null -> new DeliveryDetails(null, null, null);
        };
    }

    private static String valueAsString(Map<String, Object> details, String key) {
        Object value = details.get(key);
        return value == null ? null : value.toString();
    }

    private static Instant valueAsInstant(Map<String, Object> details, String key) {
        String value = valueAsString(details, key);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}
