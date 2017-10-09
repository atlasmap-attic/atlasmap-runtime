/**
 * Copyright (C) 2017 Red Hat, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atlasmap.core;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.atlasmap.api.AtlasConversionService;
import io.atlasmap.api.AtlasConverter;
import io.atlasmap.api.AtlasValidationService;
import io.atlasmap.spi.AtlasConversionConcern;
import io.atlasmap.spi.AtlasConversionInfo;
import io.atlasmap.spi.AtlasModuleDetail;
import io.atlasmap.spi.AtlasModuleMode;
import io.atlasmap.v2.AtlasMapping;
import io.atlasmap.v2.BaseMapping;
import io.atlasmap.v2.DataSource;
import io.atlasmap.v2.Field;
import io.atlasmap.v2.FieldType;
import io.atlasmap.v2.Mapping;
import io.atlasmap.v2.MappingType;
import io.atlasmap.v2.Validation;
import io.atlasmap.v2.ValidationStatus;

public abstract class BaseModuleValidationService<T extends Field> implements AtlasValidationService {

    private AtlasConversionService conversionService;
    private AtlasModuleMode mode;

    public BaseModuleValidationService() {
        this.conversionService = DefaultAtlasConversionService.getInstance();
    }

    public BaseModuleValidationService(AtlasConversionService conversionService) {
        this.conversionService = conversionService;
    }

    public void setMode(AtlasModuleMode mode) {
        this.mode = mode;
    }

    public AtlasModuleMode getMode() {
        return mode;
    }

    public enum FieldDirection {
        SOURCE("Source"),
        TARGET("Target");

        private String value;

        FieldDirection(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    protected abstract AtlasModuleDetail getModuleDetail();

    @Override
    public List<Validation> validateMapping(AtlasMapping mapping) {
        List<Validation> validations = new ArrayList<Validation>();
        if (getMode() == AtlasModuleMode.UNSET) {
            Validation validation = new Validation();
            validation.setMessage(String.format(
                    "No mode specified for %s/%s, skipping module validations",
                    this.getModuleDetail().name(), this.getClass().getSimpleName()));
        }

        if (mapping != null && mapping.getMappings() != null && mapping.getMappings().getMapping() != null
                && !mapping.getMappings().getMapping().isEmpty()) {
            validateMappingEntries(mapping.getMappings().getMapping(), validations);
        }

        boolean found = false;
        for (DataSource ds : mapping.getDataSource()) {
            if (ds.getUri() != null && ds.getUri().startsWith(getModuleDetail().uri())) {
                found = true;
            }
        }

        if (!found) {
            Validation validation = new Validation();
            validation.setField(String.format("DataSource.uri"));
            validation.setMessage(String.format("No DataSource with '%s' uri specified", getModuleDetail().uri()));
            validation.setStatus(ValidationStatus.ERROR);
            validations.add(validation);
        }

        return validations;
    }

    protected void validateMappingEntries(List<BaseMapping> mappings, List<Validation> validations) {
        for (BaseMapping fieldMapping : mappings) {
            if (fieldMapping.getClass().isAssignableFrom(Mapping.class)
                    && MappingType.MAP.equals(((Mapping) fieldMapping).getMappingType())) {
                validateMapMapping((Mapping) fieldMapping, validations);
            } else if (fieldMapping.getClass().isAssignableFrom(Mapping.class)
                    && MappingType.SEPARATE.equals(((Mapping) fieldMapping).getMappingType())) {
                validateSeparateMapping((Mapping) fieldMapping, validations);
            }
        }
    }

    protected void validateMapMapping(Mapping mapping, List<Validation> validations) {
        Field sourceField = null;
        Field targetField = null;

        if (mapping != null && mapping.getSourceField() != null && mapping.getSourceField().size() > 0) {
            sourceField = mapping.getSourceField().get(0);
            if (getMode() == AtlasModuleMode.SOURCE) {
                validateField(sourceField, FieldDirection.SOURCE, validations);
            }
        }

        if (mapping != null && mapping.getTargetField() != null && mapping.getTargetField().size() > 0) {
            targetField = mapping.getTargetField().get(0);
            if (getMode() == AtlasModuleMode.TARGET) {
                validateField(targetField, FieldDirection.TARGET, validations);
            }
        }

        if (sourceField != null && targetField != null && getMode() == AtlasModuleMode.SOURCE) {
            // FIXME Run only for SOURCE to avoid duplicate validation...
            // we should convert per module validations to plugin style
            validateSourceAndTargetTypes(sourceField, targetField, validations);
        }
    }

    protected void validateSeparateMapping(Mapping mapping, List<Validation> validations) {
        if (mapping == null) {
            return;
        }

        final Field sourceField = mapping.getSourceField() != null ? mapping.getSourceField().get(0) : null;
        List<Field> targetFields =mapping.getTargetField();

        if (getMode() == AtlasModuleMode.SOURCE) {
            // check that the source field is of type String else error
            if (sourceField.getFieldType() != FieldType.STRING) {
                Validation validation = new Validation();
                validation.setField("Source.Field");
                validation.setMessage("Source field must be of type " + FieldType.STRING + " for a Separate Mapping");
                validation.setStatus(ValidationStatus.ERROR);
                validation.setValue(getFieldName(sourceField));
                validations.add(validation);
            }
            validateField(sourceField, FieldDirection.SOURCE, validations);

            if (targetFields != null) {
                // FIXME Run only for SOURCE to avoid duplicate validation...
                // we should convert per module validations to plugin style
                for (Field targetField : targetFields) {
                    validateSourceAndTargetTypes(sourceField, targetField, validations);
                }
            }
        } else if (targetFields != null) { // TARGET
            targetFields.forEach(targetField -> validateField(targetField, FieldDirection.TARGET, validations));
        }
    }

    protected void validateField(Field field, FieldDirection direction, List<Validation> validations) {
        if (field == null || !getFieldType().isAssignableFrom(field.getClass())) {
            Validation validation = new Validation();
            validation.setField(String.format("%s.Field", direction.value()));
            validation.setMessage(String.format("%s field %s is not supported by the %s",
                    direction.value(), getFieldName(field), getModuleDetail().name()));
            validation.setStatus(ValidationStatus.ERROR);
            validations.add(validation);
        } else {
            validateModuleField((T)field, direction, validations);
        }
    }

    protected abstract Class<T> getFieldType();

    protected abstract void validateModuleField(T field, FieldDirection direction, List<Validation> validation);

    protected void validateSourceAndTargetTypes(Field sourceField, Field targetField, List<Validation> validations) {
        if (sourceField.getFieldType() != targetField.getFieldType()) {
            // is this check superseded by the further checks using the AtlasConversionInfo
            // annotations?

            // errors.getAllErrors().add(new AtlasMappingError("Field.Source/Target",
            // sourceField.getType().value() + " --> " + targetField.getType().value(), "Target
            // field type does not match source field type, may require a converter.",
            // AtlasMappingError.Level.WARN));
            validateFieldTypeConversion(sourceField, targetField, validations);
        }
    }

    protected void populateConversionConcerns(AtlasConversionConcern[] atlasConversionConcerns, String value, List<Validation> validations) {
        if (atlasConversionConcerns == null) {
            return;
        }

        for (AtlasConversionConcern atlasConversionConcern : atlasConversionConcerns) {
            if (AtlasConversionConcern.NONE.equals(atlasConversionConcern)) {
                Validation validation = new Validation();
                validation.setField("Field.Source/Target.conversion");
                validation.setMessage(atlasConversionConcern.getMessage());
                validation.setStatus(ValidationStatus.INFO);
                validation.setValue(value);
                validations.add(validation);
            }
            if (atlasConversionConcern.equals(AtlasConversionConcern.RANGE)
                    || atlasConversionConcern.equals(AtlasConversionConcern.FORMAT)) {
                Validation validation = new Validation();
                validation.setField("Field.Source/Target.conversion");
                validation.setMessage(atlasConversionConcern.getMessage());
                validation.setStatus(ValidationStatus.WARN);
                validation.setValue(value);
                validations.add(validation);
            }
            if (atlasConversionConcern.equals(AtlasConversionConcern.UNSUPPORTED)) {
                Validation validation = new Validation();
                validation.setField("Field.Source/Target.conversion");
                validation.setMessage(atlasConversionConcern.getMessage());
                validation.setStatus(ValidationStatus.ERROR);
                validation.setValue(value);
                validations.add(validation);
            }
        }
    }

    protected void validateFieldTypeConversion(Field sourceField, Field targetField, List<Validation> validations) {
        FieldType sourceFieldType = sourceField.getFieldType();
        FieldType targetFieldType = targetField.getFieldType();
        Optional<AtlasConverter> atlasConverter = conversionService.findMatchingConverter(sourceFieldType, targetFieldType);
        String rejectedValue = getFieldName(sourceField) + " --> " + getFieldName(targetField);
        if (!atlasConverter.isPresent()) {
            Validation validation = new Validation();
            validation.setField("Field.Source/Target.conversion");
            validation.setMessage(
                    "A conversion between the source and target fields is required but no converter is available");
            validation.setStatus(ValidationStatus.WARN);
            validation.setValue(rejectedValue);
            validations.add(validation);
        } else {
            AtlasConversionInfo conversionInfo;
            // find the method that does the conversion
            Method[] methods = atlasConverter.get().getClass().getMethods();
            conversionInfo = Arrays.stream(methods).map(method -> method.getAnnotation(AtlasConversionInfo.class))
                    .filter(atlasConversionInfo -> atlasConversionInfo != null)
                    .filter(atlasConversionInfo -> (atlasConversionInfo.sourceType().compareTo(sourceFieldType) == 0
                    && atlasConversionInfo.targetType().compareTo(targetFieldType) == 0))
                    .findFirst().orElse(null);
            if (conversionInfo != null) {
                populateConversionConcerns(conversionInfo.concerns(), rejectedValue, validations);
            }
        }
    }

    protected String getFieldName(Field field) {
        if (field == null) {
            return "null";
        }
        if (field.getClass().isAssignableFrom(getFieldType())) {
            return getModuleFieldName((T)field);
        }
        if (field.getFieldType() != null) {
            return field.getFieldType().name();
        }
        return field.getClass().getName();
    }

    protected abstract String getModuleFieldName(T field);

    protected AtlasConversionService getConversionService() {
        return conversionService;
    }

    protected void setConversionService(AtlasConversionService conversionService) {
        this.conversionService = conversionService;
    }

}
