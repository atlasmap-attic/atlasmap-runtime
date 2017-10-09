package io.atlasmap.java.module;

import java.lang.reflect.Method;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.atlasmap.api.AtlasConversionException;
import io.atlasmap.api.AtlasConversionService;
import io.atlasmap.api.AtlasException;
import io.atlasmap.api.AtlasFieldActionService;
import io.atlasmap.api.AtlasSession;
import io.atlasmap.core.PathUtil;
import io.atlasmap.java.inspect.ClassHelper;
import io.atlasmap.java.module.DocumentJavaFieldWriter.JavaFieldWriterValueConverter;
import io.atlasmap.java.v2.JavaEnumField;
import io.atlasmap.java.v2.JavaField;
import io.atlasmap.v2.Field;
import io.atlasmap.v2.FieldType;
import io.atlasmap.v2.LookupEntry;
import io.atlasmap.v2.LookupTable;
import io.atlasmap.v2.Mapping;

public class TargetValueConverter implements JavaFieldWriterValueConverter {
    private static final Logger logger = LoggerFactory.getLogger(TargetValueConverter.class);

    private Field sourceField = null;
    private AtlasSession session = null;
    private AtlasConversionService conversionService = null;
    private Mapping mapping = null;

    public TargetValueConverter(Field sourceField, AtlasSession session, Mapping mapping,
            AtlasConversionService conversionService) {
        super();
        this.sourceField = sourceField;
        this.session = session;
        this.mapping = mapping;
        this.conversionService = conversionService;
    }

    @Override
    public Object convertValue(Object parentObject, Field targetField) throws AtlasException {
        // FIXME: this javafield cast is going to break for enum values.
        return populateTargetValue(parentObject, sourceField, targetField);
    }

    protected Object populateTargetValue(Object parentObject, Field sourceField, Field targetField)
            throws AtlasException {
        FieldType sourceType = sourceField.getFieldType();
        Object sourceValue = sourceField.getValue();

        Object targetValue = null;
        FieldType targetType = targetField.getFieldType();

        if (logger.isDebugEnabled()) {
            logger.debug("processTargetMapping iPath=" + sourceField.getPath() + " iV=" + sourceValue + " iT=" + sourceType
                    + " oPath=" + targetField.getPath() + " docId: " + targetField.getDocId());
        }

        if (sourceValue == null) {
            // TODO: Finish targetValue = null processing
            logger.warn("Null sourceValue for field: " + targetField.getPath() + " docId: " + targetField.getDocId());
            return null;
        }

        String targetClassName = (targetField instanceof JavaField) ? ((JavaField) targetField).getClassName() : null;
        targetClassName = (targetField instanceof JavaEnumField) ? ((JavaEnumField) targetField).getClassName()
                : targetClassName;
        if (targetType == null || targetClassName == null) {
            try {
                Method setter = resolveTargetSetMethod(parentObject, targetField, null);
                if (setter != null && setter.getParameterCount() == 1) {
                    if (targetField instanceof JavaField) {
                        ((JavaField) targetField).setClassName(setter.getParameterTypes()[0].getName());
                    } else if (targetField instanceof JavaEnumField) {
                        ((JavaEnumField) targetField).setClassName(setter.getParameterTypes()[0].getName());
                    }

                    targetType = conversionService.fieldTypeFromClass(setter.getParameterTypes()[0]);
                    targetField.setFieldType(targetType);
                    if (logger.isTraceEnabled()) {
                        logger.trace("Auto-detected targetType as {} for class={} path={}", targetType,
                                parentObject.toString(), targetField.getPath());
                    }
                }
            } catch (Exception e) {
                logger.debug("Unable to auto-detect targetType for class={} path={}", parentObject.toString(),
                        targetField.getPath());
            }
        }

        if (sourceField instanceof JavaEnumField || targetField instanceof JavaEnumField) {
            if (!(sourceField instanceof JavaEnumField) || !(targetField instanceof JavaEnumField)) {
                throw new AtlasException(
                        "Value conversion between enum fields and non-enum fields is not yet supported.");
            }
            return populateEnumValue((JavaEnumField) sourceField, (JavaEnumField) targetField);
        }

        AtlasFieldActionService fieldActionService = session.getAtlasContext().getContextFactory()
                .getFieldActionService();
        try {
            targetValue = fieldActionService.processActions(targetField.getActions(), sourceValue, targetType);
            if (targetValue != null) {
                FieldType conversionSourceType = conversionService.fieldTypeFromClass(targetValue.getClass());
                targetValue = conversionService.convertType(targetValue, conversionSourceType, targetType);
            }
        } catch (AtlasConversionException e) {
            logger.error(String.format("Unable to auto-convert for sT=%s tT=%s tF=%s msg=%s", sourceType, targetType,
                    targetField.getPath(), e.getMessage()), e);
            return null;
        }

        return targetValue;
    }

    @SuppressWarnings("unchecked")
    private Object populateEnumValue(JavaEnumField sourceField, JavaEnumField targetField) throws AtlasException {
        if (sourceField == null || sourceField.getValue() == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Source enum field or value is null, field: " + sourceField);
            }
            return null;
        }

        String lookupTableName = mapping.getLookupTableName();
        LookupTable table = null;
        for (LookupTable t : session.getMapping().getLookupTables().getLookupTable()) {
            if (t.getName().equals(lookupTableName)) {
                table = t;
                break;
            }
        }
        if (table == null) {
            throw new AtlasException(
                    "Could not find lookup table with name '" + lookupTableName + "' for mapping: " + mapping);
        }

        String sourceValue = ((Enum<?>) sourceField.getValue()).name();
        String targetValue = null;
        for (LookupEntry e : table.getLookupEntry()) {
            if (e.getSourceValue().equals(sourceValue)) {
                targetValue = e.getTargetValue();
                break;
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Mapped source enum value '" + sourceValue + "' to target enum value '" + targetValue + "'.");
        }

        if (targetValue == null) {
            return null;
        }

        @SuppressWarnings("rawtypes")
        Class enumClass = null;
        try {
            enumClass = Class.forName(targetField.getClassName());
        } catch (Exception e) {
            throw new AtlasException(
                    "Could not find class for target field class '" + targetField.getClassName() + "'.", e);
        }

        return Enum.valueOf(enumClass, targetValue);

    }

    protected Method resolveTargetSetMethod(Object sourceObject, Field field, Class<?> targetType)
            throws AtlasException {

        PathUtil pathUtil = new PathUtil(field.getPath());
        Object parentObject = sourceObject;

        List<Class<?>> classTree = JavaModule.resolveMappableClasses(parentObject.getClass());

        if (field instanceof JavaField) {
            JavaField javaField = (JavaField) field;
            for (Class<?> clazz : classTree) {
                try {
                    String setterMethodName = javaField.getSetMethod();
                    if (setterMethodName == null) {
                        setterMethodName = "set" + JavaModule.capitalizeFirstLetter(pathUtil.getLastSegment());
                    }
                    return ClassHelper.detectSetterMethod(clazz, setterMethodName, targetType);
                } catch (NoSuchMethodException e) {
                    // method does not exist
                }

                // Try the boxUnboxed version
                if (conversionService.isPrimitive(targetType) || conversionService.isBoxedPrimitive(targetType)) {
                    try {
                        String setterMethodName = javaField.getSetMethod();
                        if (setterMethodName == null) {
                            setterMethodName = "set" + JavaModule.capitalizeFirstLetter(pathUtil.getLastSegment());
                        }
                        return ClassHelper.detectSetterMethod(clazz, setterMethodName,
                                conversionService.boxOrUnboxPrimitive(targetType));
                    } catch (NoSuchMethodException e) {
                        // method does not exist
                    }
                }
            }
        } else if (field instanceof JavaEnumField) {
            for (Class<?> clazz : classTree) {
                try {
                    String setterMethodName = "set" + JavaModule.capitalizeFirstLetter(pathUtil.getLastSegment());
                    return ClassHelper.detectSetterMethod(clazz, setterMethodName, targetType);
                } catch (NoSuchMethodException e) {
                    // method does not exist
                }
            }
        }

        throw new AtlasException(String.format("Unable to resolve setter for path=%s", field.getPath()));
    }

}
