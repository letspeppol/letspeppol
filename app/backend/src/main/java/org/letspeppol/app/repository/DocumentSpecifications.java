package org.letspeppol.app.repository;

import org.letspeppol.app.dto.DocumentFilter;
import org.letspeppol.app.model.Document;
import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.model.DocumentType;
import org.springframework.data.jpa.domain.Specification;

public final class DocumentSpecifications {

    private DocumentSpecifications() {
    }

    public static Specification<Document> build(DocumentFilter filter) {
        Specification<Document> spec = Specification.anyOf();

        if (filter == null) {
            return spec;
        }

        if (filter.getOwnerPeppolId() != null && !filter.getOwnerPeppolId().isBlank()) {
            spec = spec.and(hasOwnerPeppolId(filter.getOwnerPeppolId()));
        }
        if (filter.getType() != null) {
            spec = spec.and(hasType(filter.getType()));
        }
        if (filter.getDirection() != null) {
            spec = spec.and(hasDirection(filter.getDirection()));
        }
        if (filter.getPartnerName() != null && !filter.getPartnerName().isBlank()) {
            spec = spec.and(hasPartnerNameLike(filter.getPartnerName()));
        }
        if (filter.getInvoiceReference() != null && !filter.getInvoiceReference().isBlank()) {
            spec = spec.and(hasInvoiceReferenceLike(filter.getInvoiceReference()));
        }
        if (filter.getPaid() != null) {
            spec = spec.and(isPaid(filter.getPaid()));
        }
        if (filter.getRead() != null) {
            spec = spec.and(isRead(filter.getRead()));
        }
        if (filter.getDraft() != null) {
            spec = spec.and(isDraft(filter.getDraft()));
        }

        return spec;
    }

    public static Specification<Document> hasOwnerPeppolId(String ownerPeppolId) {
        return (root, query, cb) -> cb.equal(root.get("ownerPeppolId"), ownerPeppolId);
    }

    public static Specification<Document> hasType(DocumentType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Document> hasDirection(DocumentDirection direction) {
        return (root, query, cb) -> cb.equal(root.get("direction"), direction);
    }

    public static Specification<Document> hasPartnerNameLike(String partnerName) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("partnerName")), "%" + partnerName.toLowerCase() + "%");
    }

    public static Specification<Document> hasInvoiceReferenceLike(String invoiceReference) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("invoiceReference")), "%" + invoiceReference.toLowerCase() + "%");
    }

    public static Specification<Document> isPaid(boolean paid) {
        return (root, query, cb) -> paid ? cb.isNotNull(root.get("paidOn")) : cb.isNull(root.get("paidOn"));
    }

    public static Specification<Document> isRead(boolean read) {
        return (root, query, cb) -> read ? cb.isNotNull(root.get("readOn")) : cb.isNull(root.get("readOn"));
    }

    public static Specification<Document> isDraft(boolean draft) {
        return (root, query, cb) -> draft ? cb.isNotNull(root.get("draftedOn")) : cb.isNull(root.get("draftedOn"));
    }
}
