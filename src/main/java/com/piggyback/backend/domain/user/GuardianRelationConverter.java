package com.piggyback.backend.domain.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// schema.sql v1.2의 guardian.relation은 한글 값('아들' 등)을 저장하므로 label로 변환한다.
@Converter(autoApply = true)
public class GuardianRelationConverter implements AttributeConverter<GuardianRelation, String> {

    @Override
    public String convertToDatabaseColumn(GuardianRelation attribute) {
        return attribute == null ? null : attribute.getLabel();
    }

    @Override
    public GuardianRelation convertToEntityAttribute(String dbData) {
        return dbData == null ? null : GuardianRelation.fromLabel(dbData);
    }
}
