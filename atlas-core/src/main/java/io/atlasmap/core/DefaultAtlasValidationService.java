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

import io.atlasmap.api.AtlasValidationService;
import io.atlasmap.spi.AtlasValidator;
import io.atlasmap.v2.AtlasMapping;
import io.atlasmap.v2.BaseMapping;
import io.atlasmap.v2.DataSource;
import io.atlasmap.v2.Field;
import io.atlasmap.v2.LookupTable;
import io.atlasmap.v2.LookupTables;
import io.atlasmap.v2.Mapping;
import io.atlasmap.v2.MappingType;
import io.atlasmap.v2.Mappings;
import io.atlasmap.v2.Validation;
import io.atlasmap.v2.ValidationStatus;
import io.atlasmap.validators.CompositeValidator;
import io.atlasmap.validators.LookupTableNameValidator;
import io.atlasmap.validators.NonNullValidator;
import io.atlasmap.validators.NotEmptyValidator;
import io.atlasmap.validators.PositiveIntegerValidator;
import io.atlasmap.validators.StringPatternValidator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefaultAtlasValidationService implements AtlasValidationService {

    private static Map<String, AtlasValidator> validatorMap = new HashMap<>();

    public DefaultAtlasValidationService() {
        init();
    }

    public void init() {
        NonNullValidator sourceURINotNullOrEmptyValidator = new NonNullValidator("DataSource.source.uri",
                "DataSource source uri must not be null nor empty");
        NonNullValidator targetURINotNullOrEmptyValidator = new NonNullValidator("DataSource.target.uri",
                "DataSource target uri must not be null nor empty");
        StringPatternValidator namePatternValidator = new StringPatternValidator("Mapping.Name",
                "Mapping name must not contain spaces nor special characters other than period (.) and underscore (_)",
                "[^A-Za-z0-9_.]");
        NonNullValidator nameNotNullOrEmptyValidator = new NonNullValidator("Mapping.Name",
                "Mapping name must not be null nor empty");
        CompositeValidator nameValidator = new CompositeValidator(nameNotNullOrEmptyValidator, namePatternValidator);
        NonNullValidator fieldNamesNotNullValidator = new NonNullValidator("Field.Mappings",
                "Field mappings must not be null");
        NotEmptyValidator fieldNamesNotEmptyValidator = new NotEmptyValidator("Field.Mappings",
                "Field mappings should not be empty");
        NonNullValidator sourceNonNullValidator = new NonNullValidator("MapFieldMapping.Source",
                "Source element must not be null");
        NonNullValidator sourceFieldNonNullValidator = new NonNullValidator("MapFieldMapping.Source.Field",
                "Source field element must not be null");
        NonNullValidator targetNonNullValidator = new NonNullValidator("MapFieldMapping.Target",
                "Target element should not be null");
        NonNullValidator targetFieldNonNullValidator = new NonNullValidator("MapFieldMapping.Target.Field",
                "Target field element should not be null");
        NonNullValidator separateSourceNonNullValidator = new NonNullValidator("SeparateFieldMapping.Source",
                "Source element must not be null");
        NonNullValidator separateSourceFieldNonNullValidator = new NonNullValidator("SeparateFieldMapping.Source.Field",
                "Source field element must not be null");
        NonNullValidator separateTargetNonNullValidator = new NonNullValidator("SeparateFieldMapping.Target",
                "Target element should not be null");
        NotEmptyValidator separateTargetNotEmptyValidator = new NotEmptyValidator("SeparateFieldMapping.Target.Fields",
                "Target elements should not be empty");
        NonNullValidator separateTargetFieldNonNullValidator = new NonNullValidator(
                "SeparateFieldMapping.Target.FieldActions", "Target field actions cannot not be null");
        NotEmptyValidator separateTargetNotEmptyFieldActionValidator = new NotEmptyValidator(
                "SeparateFieldMapping.Target.Fields.FieldActions", "Field actions cannot be null or empty");
        PositiveIntegerValidator separateTargetMapActionPositiveIntegerValidator = new PositiveIntegerValidator(
                "SeparateFieldMapping.Target.Fields.FieldActions.MapAction.Index",
                "MapAction index must exists and be greater than or equal to zero (0)");

        LookupTableNameValidator lookupTableNameValidator = new LookupTableNameValidator(
                "lookuptables.lookuptable.name", "LookupTables contain duplicated LookupTable names.");

        validatorMap.put("datasource.source.uri", sourceURINotNullOrEmptyValidator);
        validatorMap.put("datasource.target.uri", targetURINotNullOrEmptyValidator);
        validatorMap.put("mapping.name", nameValidator);
        validatorMap.put("field.names.not.null", fieldNamesNotNullValidator);
        validatorMap.put("field.names.not.empty", fieldNamesNotEmptyValidator);
        validatorMap.put("source.not.null", sourceNonNullValidator);
        validatorMap.put("source.field.not.null", sourceFieldNonNullValidator);
        validatorMap.put("target.not.null", targetNonNullValidator);
        validatorMap.put("target.field.not.null", targetFieldNonNullValidator);

        validatorMap.put("separate.source.not.null", separateSourceNonNullValidator);
        validatorMap.put("separate.source.field.not.null", separateSourceFieldNonNullValidator);
        validatorMap.put("separate.target.not.null", separateTargetNonNullValidator);
        validatorMap.put("separate.target.not.empty", separateTargetNotEmptyValidator);
        validatorMap.put("separate.target.field.not.null", separateTargetFieldNonNullValidator);
        validatorMap.put("separate.target.field.field.action.not.empty", separateTargetNotEmptyFieldActionValidator);
        validatorMap.put("separate.target.field.field.action.index.positive",
                separateTargetMapActionPositiveIntegerValidator);
        validatorMap.put("lookuptable.name.check.for.duplicate", lookupTableNameValidator);
    }

    public void destroy() {
        validatorMap.clear();
    }

    @Override
    public List<Validation> validateMapping(AtlasMapping mapping) {
        List<Validation> validations = new ArrayList<Validation>();
        validatorMap.get("mapping.name").validate(mapping.getName(), validations);

        List<DataSource> dataSources = mapping.getDataSource();
        for (DataSource ds : dataSources) {
            switch (ds.getDataSourceType()) {
            case SOURCE:
                validatorMap.get("datasource.source.uri").validate(ds.getUri(), validations);
                break;
            case TARGET:
                validatorMap.get("datasource.target.uri").validate(ds.getUri(), validations);
                break;
            }
        }
        validateFieldMappings(mapping.getMappings(), mapping.getLookupTables(), validations);
        return validations;
    }

    private void validateFieldMappings(Mappings mappings, LookupTables lookupTables, List<Validation> validations) {
        validatorMap.get("field.names.not.null").validate(mappings, validations);
        if (mappings != null) {
            validatorMap.get("field.names.not.empty").validate(mappings, validations, ValidationStatus.WARN);

            List<BaseMapping> fieldMappings = mappings.getMapping();
            if (fieldMappings != null && !fieldMappings.isEmpty()) {
                List<Mapping> mapFieldMappings = fieldMappings.stream()
                        .filter(p -> p.getMappingType() == MappingType.MAP).map(p -> (Mapping) p)
                        .collect(Collectors.toList());
                List<Mapping> combineFieldMappings = fieldMappings.stream()
                        .filter(p -> p.getMappingType() == MappingType.COMBINE).map(p -> (Mapping) p)
                        .collect(Collectors.toList());
                List<Mapping> separateFieldMappings = fieldMappings.stream()
                        .filter(p -> p.getMappingType() == MappingType.SEPARATE).map(p -> (Mapping) p)
                        .collect(Collectors.toList());
                List<Mapping> lookupFieldMappings = fieldMappings.stream()
                        .filter(p -> p.getMappingType() == MappingType.LOOKUP).map(p -> (Mapping) p)
                        .collect(Collectors.toList());
                validateMapMapping(mapFieldMappings, validations);
                validateCombineMapping(combineFieldMappings, validations);
                validateSeparateMapping(separateFieldMappings, validations);
                validateLookupTables(lookupFieldMappings, lookupTables, validations);
            }
        }
    }

    private void validateLookupTables(List<Mapping> lookupFieldMappings, LookupTables lookupTables,
            List<Validation> validations) {
        if (lookupTables != null && lookupTables.getLookupTable() != null && !lookupTables.getLookupTable().isEmpty()) {
            // check for duplicate names
            validatorMap.get("lookuptable.name.check.for.duplicate").validate(lookupTables, validations);
            if (lookupFieldMappings.isEmpty()) {
                Validation validation = new Validation();
                validation.setField("lookup.fields.missing");
                validation.setMessage("LookupTables are defined but no LookupFields are utilized.");
                validation.setStatus(ValidationStatus.WARN);
                validations.add(validation);
            } else {
                validateLookupFieldMapping(lookupFieldMappings, lookupTables, validations);
            }
        }
    }

    // mapping field validations
    private void validateLookupFieldMapping(List<Mapping> fieldMappings, LookupTables lookupTables,
            List<Validation> validations) {
        Set<String> lookupFieldMappingTableNameRefs = fieldMappings.stream().map(Mapping::getLookupTableName)
                .collect(Collectors.toSet());

        Set<String> tableNames = lookupTables.getLookupTable().stream().map(LookupTable::getName)
                .collect(Collectors.toSet());

        if (!lookupFieldMappingTableNameRefs.isEmpty() && !tableNames.isEmpty()) {
            Set<String> disjoint = Stream.concat(lookupFieldMappingTableNameRefs.stream(), tableNames.stream())
                    .collect(Collectors.toMap(Function.identity(), t -> true, (a, b) -> null)).keySet();
            if (!disjoint.isEmpty()) {

                boolean isInFieldList = !lookupFieldMappingTableNameRefs.stream().filter(disjoint::contains)
                        .collect(Collectors.toList()).isEmpty();
                boolean isInTableNameList = !tableNames.stream().filter(disjoint::contains).collect(Collectors.toList())
                        .isEmpty();
                // which list has the disjoin.... if its the lookup fields then ERROR
                if (isInFieldList) {
                    Validation validation = new Validation();
                    validation.setField("lookupfield.tablename");
                    validation.setMessage(
                            "One ore more LookupFieldMapping references a non existent LookupTable name in the mapping");
                    validation.setStatus(ValidationStatus.ERROR);
                    validation.setValue(disjoint.toString());
                    validations.add(validation);
                }

                // check that if a name exists in table names that at least one field mapping
                // uses it, else WARN
                if (isInTableNameList) {
                    Validation validation = new Validation();
                    validation.setField("lookupfield.tablename");
                    validation.setMessage("A LookupTable is defined but not used by any LookupField");
                    validation.setStatus(ValidationStatus.WARN);
                    validation.setValue(disjoint.toString());
                    validations.add(validation);
                }
            }
        }

        for (Mapping fieldMapping : fieldMappings) {
            if (fieldMapping.getSourceField() != null) {
                validatorMap.get("source.field.not.null").validate(fieldMapping.getSourceField(), validations);
            }
            validatorMap.get("target.not.null").validate(fieldMapping.getTargetField(), validations,
                    ValidationStatus.WARN);
            if (fieldMapping.getTargetField() != null) {
                validatorMap.get("target.field.not.null").validate(fieldMapping.getTargetField(), validations,
                        ValidationStatus.WARN);
            }
        }

    }

    private void validateMapMapping(List<Mapping> fieldMappings, List<Validation> validations) {
        for (Mapping fieldMapping : fieldMappings) {
            validatorMap.get("source.not.null").validate(fieldMapping.getSourceField(), validations);
            if (fieldMapping.getSourceField() != null) {
                validatorMap.get("source.field.not.null").validate(fieldMapping.getSourceField(), validations);
            }
            validatorMap.get("target.not.null").validate(fieldMapping.getTargetField(), validations,
                    ValidationStatus.WARN);
            if (fieldMapping.getTargetField() != null) {
                validatorMap.get("target.field.not.null").validate(fieldMapping.getTargetField(), validations,
                        ValidationStatus.WARN);
            }
        }
    }

    private void validateSeparateMapping(List<Mapping> fieldMappings, List<Validation> validations) {
        for (Mapping fieldMapping : fieldMappings) {
            validatorMap.get("separate.source.not.null").validate(fieldMapping.getSourceField(), validations);
            if (fieldMapping.getSourceField() != null) {
                validatorMap.get("separate.source.field.not.null").validate(fieldMapping.getSourceField(), validations);
                // source must be a String type
            }

            validatorMap.get("separate.target.not.null").validate(fieldMapping.getTargetField(), validations,
                    ValidationStatus.WARN);
            validatorMap.get("separate.target.not.empty").validate(fieldMapping.getTargetField(), validations,
                    ValidationStatus.WARN);

            if (fieldMapping.getTargetField() != null) {
                for (Field field : fieldMapping.getTargetField()) {
                    validatorMap.get("separate.target.field.not.null").validate(field, validations);
                    if (field.getIndex() == null || field.getIndex() < 0) {
                        validatorMap.get("separate.target.field.field.action.index.positive").validate(field.getIndex(),
                                validations);
                    }
                }
            }
        }
    }

    private void validateCombineMapping(List<Mapping> fieldMappings, List<Validation> validations) {
        for (Mapping fieldMapping : fieldMappings) {
            validatorMap.get("combine.target.not.null").validate(fieldMapping.getTargetField(), validations);
            if (fieldMapping.getTargetField() != null) {
                validatorMap.get("combine.target.field.not.null").validate(fieldMapping.getTargetField(), validations);
                // source must be a String type
            }

            validatorMap.get("combine.source.not.null").validate(fieldMapping.getSourceField(), validations,
                    ValidationStatus.WARN);
            validatorMap.get("combine.source.not.empty").validate(fieldMapping.getSourceField(), validations,
                    ValidationStatus.WARN);

            if (fieldMapping.getSourceField() != null) {
                for (Field field : fieldMapping.getSourceField()) {
                    validatorMap.get("combine.target.field.not.null").validate(field, validations);
                    if (field.getIndex() == null || field.getIndex() < 0) {
                        validatorMap.get("combine.target.field.field.action.index.positive").validate(field.getIndex(),
                                validations);
                    }
                }
            }
        }
    }
}
