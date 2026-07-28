package com.AccountReceivableManagement.cdc.mapping;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnMapping {

    /**
     * Source column name (from RMS database)
     */
    private String sourceColumn;

    /**
     * Target field name (in AR entity)
     */
    private String targetField;

    /**
     * Field type for conversion
     */
    private FieldType fieldType;

    /**
     * Enum class if fieldType is ENUM
     */
    private Class<?> enumClass;
}
