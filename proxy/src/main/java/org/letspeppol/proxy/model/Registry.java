package org.letspeppol.proxy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@NoArgsConstructor
@Getter
@Setter
@Entity
@DynamicUpdate
public class Registry {

    @Id
    private String peppolId;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private AccessPoint accessPoint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> variables;

    /** Null until reconciled, including after a provider write with an uncertain outcome. */
    private Boolean receiveDocuments;

    public Registry(String peppolId, AccessPoint accessPoint, Map<String, Object> variables) {
        this.peppolId = peppolId;
        this.accessPoint = accessPoint;
        this.variables = variables;
    }

}
