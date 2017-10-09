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
package io.atlasmap.java.module;

import io.atlasmap.java.v2.JavaField;
import io.atlasmap.api.AtlasConversionService;
import io.atlasmap.api.AtlasConverter;
import io.atlasmap.core.BaseModuleValidationService;
import io.atlasmap.spi.AtlasConversionInfo;
import io.atlasmap.spi.AtlasModuleDetail;
import io.atlasmap.spi.AtlasValidator;
import io.atlasmap.v2.Field;
import io.atlasmap.v2.FieldType;
import io.atlasmap.v2.Validation;
import io.atlasmap.v2.ValidationStatus;
import io.atlasmap.validators.NonNullValidator;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaValidationService extends BaseModuleValidationService<JavaField> {

    private static final Logger logger = LoggerFactory.getLogger(JavaValidationService.class);
    private AtlasModuleDetail moduleDetail = JavaModule.class.getAnnotation(AtlasModuleDetail.class);
    private static Map<String, AtlasValidator> validatorMap = new HashMap<>();
    private static Map<String, Integer> versionMap = new HashMap<>();

    public JavaValidationService() {
        super();
        init();
    }

    public JavaValidationService(AtlasConversionService conversionService) {
        super(conversionService);
        init();
    }

    public void init() {
        NonNullValidator javaFilePathNonNullValidator = new NonNullValidator("JavaField.Path",
                "Field path must not be null nor empty");
        NonNullValidator sourceFieldTypeNonNullValidator = new NonNullValidator("Source.Field.Type",
                "FieldType should not be null nor empty");
        NonNullValidator targetFieldTypeNonNullValidator = new NonNullValidator("Target.Field.Type",
                "FieldType should not be null nor empty");
        NonNullValidator fieldTypeNonNullValidator = new NonNullValidator("Field.Type",
                "Filed type should not be null nor empty");

        validatorMap.put("java.field.type.not.null", fieldTypeNonNullValidator);
        validatorMap.put("java.field.path.not.null", javaFilePathNonNullValidator);
        validatorMap.put("source.field.type.not.null", sourceFieldTypeNonNullValidator);
        validatorMap.put("target.field.type.not.null", targetFieldTypeNonNullValidator);

        versionMap.put("1.9", 53);
        versionMap.put("1.8", 52);
        versionMap.put("1.7", 51);
        versionMap.put("1.6", 50);
        versionMap.put("1.5", 49);
        versionMap.put("1.4", 48);
        versionMap.put("1.3", 47);
        versionMap.put("1.2", 46);
        versionMap.put("1.1", 45);
    }

    public void destroy() {
        validatorMap.clear();
        versionMap.clear();
    }

    @Override
    protected AtlasModuleDetail getModuleDetail() {
        return moduleDetail;
    }

    @Override
    protected Class<JavaField> getFieldType() {
        return JavaField.class;
    }

    @Override
    protected String getModuleFieldName(JavaField field) {
        StringBuilder buf = new StringBuilder();
        if (field.getName() != null) {
            buf.append(field.getName());
        }
        if (field.getFieldType() == FieldType.COMPLEX) {
            if (field.getClassName() != null) {
                buf.append("(").append(field.getClassName()).append(")");
            }
        } else {
            if (field.getFieldType() != null) {
                buf.append("(").append(field.getFieldType().name()).append(")");
            }
        }
        return buf.toString();
    }

    protected void validateSourceAndTargetTypes(Field sourceField, Field targetField, List<Validation> validations) {
        if ((sourceField instanceof JavaField && targetField instanceof JavaField)
                && ((sourceField.getFieldType() == null || targetField.getFieldType() == null)
                    || (sourceField.getFieldType().compareTo(FieldType.COMPLEX) == 0
                    || targetField.getFieldType().compareTo(FieldType.COMPLEX) == 0))) {
            // making an assumption that anything marked as COMPLEX would require the use of
            // the class name to find a validator.
            validateClassConversion((JavaField)sourceField, (JavaField)targetField, validations);
            return;
        }

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

    private void validateClassConversion(JavaField sourceField, JavaField targetField, List<Validation> validations) {
        Optional<AtlasConverter> atlasConverter = getConversionService().findMatchingConverter(
                sourceField.getClassName(), targetField.getClassName());
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
                    .filter(atlasConversionInfo -> atlasConversionInfo.sourceClassName()
                            .equals(sourceField.getClassName())
                            && atlasConversionInfo.targetClassName().equals(targetField.getClassName()))
                    .findFirst().orElse(null);
            if (conversionInfo != null){
                populateConversionConcerns(conversionInfo.concerns(), rejectedValue, validations);
            }
        }
    }

    @Override
    protected void validateModuleField(JavaField field, FieldDirection direction, List<Validation> validations) {
        validatorMap.get("java.field.type.not.null").validate(field, validations, ValidationStatus.WARN);
        if (direction == FieldDirection.SOURCE) {
            validatorMap.get("source.field.type.not.null").validate(field.getFieldType(), validations,
                    ValidationStatus.WARN);
        } else {
            validatorMap.get("target.field.type.not.null").validate(field.getFieldType(), validations,
                    ValidationStatus.WARN);
        }
        if (field.getPath() == null) {
            validatorMap.get("java.field.path.not.null").validate(field.getPath(), validations);
        }

        validateClass(field, validations);
    }

    private void validateClass(JavaField field, List<Validation> validations) {
        String clazzName = field.getClassName();
        if (clazzName != null && !clazzName.isEmpty()) {
            Integer major = detectClassVersion(clazzName);
            if (major != null) {
                if (major > versionMap.get(System.getProperty("java.vm.specification.version"))) {
                    Validation validation = new Validation();
                    validation.setField("Field.Classname");
                    validation.setMessage(String.format(
                            "Class for field is compiled against unsupported JDK version: %d current JDK: %d", major,
                            versionMap.get(System.getProperty("java.vm.specification.version"))));
                    validation.setStatus(ValidationStatus.ERROR);
                    validation.setValue(clazzName);
                    validations.add(validation);
                }
            } else {
                Validation validation = new Validation();
                validation.setField("Field.Classname");
                validation.setMessage("Class for field is not found on the classpath");
                validation.setStatus(ValidationStatus.ERROR);
                validation.setValue(clazzName);
                validations.add(validation);
            }
        }
    }

    protected Integer detectClassVersion(String className) {
        Integer major = null;
        InputStream in = null;
        try {
            in = getClass().getClassLoader().getResourceAsStream(className.replace('.', '/') + ".class");
            if (in == null) {
                in = ClassLoader.getSystemResourceAsStream(className.replace('.', '/') + ".class");
                if (in == null) {
                    return null;
                }
            }
            DataInputStream data = new DataInputStream(in);
            int magic = data.readInt();
            if (magic != 0xCAFEBABE) {
                logger.error(String.format("Invalid Java class: %s magic value: %s", className, magic));
            }

            int minor = 0xFFFF & data.readShort();
            major = new Integer(0xFFFF & data.readShort());

            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Detected class: %s version major: %s minor: %s", className, magic, minor));
            }
        } catch (IOException e) {
            logger.error(String.format("Error detected version for class: %s msg: %s", className, e.getMessage()), e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ie) {
                    logger.error(String.format("Error closing input stream msg: %s", ie.getMessage()), ie);
                }
            }
        }
        return major;
    }

}
