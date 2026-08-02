package org.letspeppol.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "welcome_notification")
@Getter
@Setter
@NoArgsConstructor
public class WelcomeNotification extends GenericEntity {

    @Column(nullable = false, columnDefinition = "text")
    private String htmlCodeEn;

    @Column(nullable = false, columnDefinition = "text")
    private String htmlCodeNl;

    @Column(nullable = false, columnDefinition = "text")
    private String htmlCodeFr;

    @Column(nullable = false, columnDefinition = "text")
    private String htmlCodeDe;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false)
    private NotificationGroup notificationGroup = NotificationGroup.USER;
}
