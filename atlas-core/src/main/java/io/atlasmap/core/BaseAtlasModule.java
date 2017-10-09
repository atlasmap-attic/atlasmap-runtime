/**
 * Copyright (C) 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atlasmap.core;

import java.util.HashMap;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.atlasmap.api.AtlasConversionService;
import io.atlasmap.api.AtlasException;
import io.atlasmap.api.AtlasFieldActionService;
import io.atlasmap.api.AtlasSession;
import io.atlasmap.spi.AtlasModule;
import io.atlasmap.spi.AtlasModuleMode;
import io.atlasmap.spi.AtlasPropertyStrategy;
import io.atlasmap.v2.AtlasModelFactory;
import io.atlasmap.v2.Audit;
import io.atlasmap.v2.AuditStatus;
import io.atlasmap.v2.BaseMapping;
import io.atlasmap.v2.Collection;
import io.atlasmap.v2.ConstantField;
import io.atlasmap.v2.Field;
import io.atlasmap.v2.FieldType;
import io.atlasmap.v2.LookupEntry;
import io.atlasmap.v2.LookupTable;
import io.atlasmap.v2.Mapping;
import io.atlasmap.v2.MappingType;
import io.atlasmap.v2.PropertyField;
import io.atlasmap.v2.SimpleField;

public abstract class BaseAtlasModule implements AtlasModule {
    private static final Logger logger = LoggerFactory.getLogger(BaseAtlasModule.class);

    private AtlasConversionService atlasConversionService = null;
    private AtlasModuleMode atlasModuleMode = AtlasModuleMode.UNSET;
    protected boolean automaticallyProcessTargetFieldActions = true;

    @Override
    public void init() {
    }

    @Override
    public void destroy() {
    }

    @Override
    public void processSourceActions(AtlasSession atlasSession, BaseMapping baseMapping) throws AtlasException {
        if (baseMapping.getMappingType().equals(MappingType.COLLECTION)) {
            return;
        }
        AtlasFieldActionService fieldActionService = atlasSession.getAtlasContext().getContextFactory()
                .getFieldActionService();
        Mapping mapping = (Mapping) baseMapping;
        for (Field field : mapping.getSourceField()) {
            processFieldActions(fieldActionService, field);
        }
    }

    @Override
    public void processTargetActions(AtlasSession atlasSession, BaseMapping baseMapping) throws AtlasException {
        if (!automaticallyProcessTargetFieldActions) {
            return;
        }
        if (baseMapping.getMappingType().equals(MappingType.COLLECTION)) {
            return;
        }
        AtlasFieldActionService fieldActionService = atlasSession.getAtlasContext().getContextFactory()
                .getFieldActionService();
        Mapping mapping = (Mapping) baseMapping;
        for (Field field : mapping.getTargetField()) {
            processFieldActions(fieldActionService, field);
        }
    }

    public abstract int getCollectionSize(AtlasSession session, Field field) throws AtlasException;

    public abstract Field cloneField(Field field) throws AtlasException;

    public List<Mapping> generateSourceMappings(AtlasSession session, BaseMapping baseMapping) throws AtlasException {
        if (logger.isDebugEnabled()) {
            logger.debug("Generating source mappings from mapping: " + baseMapping);
        }
        if (!baseMapping.getMappingType().equals(MappingType.COLLECTION)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Mapping is not a collection mapping, not cloning: " + baseMapping);
            }
            return Arrays.asList((Mapping) baseMapping);
        }
        List<Mapping> mappings = new LinkedList<>();
        for (BaseMapping m : ((Collection) baseMapping).getMappings().getMapping()) {
            Mapping mapping = (Mapping) m;
            Field sourceField = mapping.getSourceField().get(0);
            boolean sourceIsCollection = PathUtil.isCollection(sourceField.getPath());
            if (!sourceIsCollection) {
                // this is a source non-collection to target collection, ie: contact.firstName -> contact[].firstName
                // this will be expanded later by generateTargetMappings, for source processing, just copy it over
                if (logger.isDebugEnabled()) {
                    logger.debug("Internal mapping's source field is not a collection, not cloning: " + mapping);
                }

                // this is a target collection such as contact<>.firstName, but source is non
                // collection such as contact.firstName
                // so just set the target collection field path to be contact<0>.firstName,
                // which will cause at least one
                // target object to be created for our copied firstName value
                for (Field f : mapping.getTargetField()) {
                    f.setPath(PathUtil.overwriteCollectionIndex(f.getPath(), 0));
                }
                mappings.add(mapping);
                continue;
            }

            int sourceCollectionSize = this.getCollectionSize(session, sourceField);
            if (logger.isDebugEnabled()) {
                logger.debug("Internal mapping's source field is a collection. Cloning it for each item ("
                        + sourceCollectionSize + " clones): " + mapping);
            }
            for (int i = 0; i < sourceCollectionSize; i++) {
                Mapping cloneMapping = (Mapping) AtlasModelFactory.cloneMapping(mapping, false);
                for (Field f : mapping.getSourceField()) {
                    Field clonedField = cloneField(f);
                    clonedField.setPath(PathUtil.overwriteCollectionIndex(clonedField.getPath(), i));
                    cloneMapping.getSourceField().add(clonedField);
                }
                for (Field f : mapping.getTargetField()) {
                    Field clonedField = cloneField(f);
                    if (PathUtil.isCollection(clonedField.getPath())) {
                        clonedField.setPath(PathUtil.overwriteCollectionIndex(clonedField.getPath(), i));
                    }
                    cloneMapping.getTargetField().add(clonedField);
                }
                mappings.add(cloneMapping);
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Generated " + mappings.size() + " mappings from mapping: " + baseMapping);
        }
        ((Collection) baseMapping).getMappings().getMapping().clear();
        ((Collection) baseMapping).getMappings().getMapping().addAll(mappings);

        return mappings;
    }

    public List<Mapping> getTargetMappings(AtlasSession session, BaseMapping baseMapping) throws AtlasException {
        if (!baseMapping.getMappingType().equals(MappingType.COLLECTION)) {
            return Arrays.asList((Mapping) baseMapping);
        }
        List<Mapping> mappings = new LinkedList<>();
        for (BaseMapping m : ((Collection) baseMapping).getMappings().getMapping()) {
            mappings.add((Mapping) m);
        }
        return mappings;
    }

    @Override
    public void processPreSourceExecution(AtlasSession session) throws AtlasException {
        if (logger.isDebugEnabled()) {
            logger.debug("processPreSourceExcution completed");
        }
    }

    @Override
    public void processPostSourceExecution(AtlasSession session) throws AtlasException {
        if (logger.isDebugEnabled()) {
            logger.debug("processPostSourceExecution completed");
        }
    }

    @Override
    public void processPostValidation(AtlasSession session) throws AtlasException {
        if (logger.isDebugEnabled()) {
            logger.debug("processPostValidation completed");
        }
    }

    protected void processConstantField(AtlasSession atlasSession, Mapping mapping) throws AtlasException {
        for (Field f : mapping.getSourceField()) {
            if (f instanceof ConstantField) {
                if (f.getFieldType() == null && f.getValue() != null) {
                    f.setFieldType(getConversionService().fieldTypeFromClass(f.getValue().getClass()));
                }
            }
        }
    }

    protected void processPropertyField(AtlasSession atlasSession, Mapping mapping,
            AtlasPropertyStrategy atlasPropertyStrategy) throws AtlasException {
        for (Field f : mapping.getSourceField()) {
            if (f instanceof PropertyField) {
                atlasPropertyStrategy.processPropertyField(atlasSession.getMapping(), (PropertyField) f,
                        atlasSession.getProperties());
            }
        }

        for (Field f : mapping.getTargetField()) {
            if (f instanceof PropertyField) {
                atlasPropertyStrategy.processPropertyField(atlasSession.getMapping(), (PropertyField) f,
                        atlasSession.getProperties());
            }
        }
    }

    protected void processLookupField(AtlasSession session, Mapping mapping) throws AtlasException {

        if (mapping == null || mapping.getMappingType() == null || MappingType.LOOKUP.equals(mapping.getMappingType())
                || mapping.getLookupTableName() == null || mapping.getLookupTableName().trim().length() == 0) {
            throw new AtlasException("Lookup mapping must have lookupTableName specified");
        }

        if (session == null || session.getMapping() == null) {
            throw new AtlasException("AtlasSession must be initialized");
        }

        if (session.getMapping().getLookupTables() == null
                || session.getMapping().getLookupTables().getLookupTable() == null
                || session.getMapping().getLookupTables().getLookupTable().size() == 0) {
            logger.warn(String.format("No lookup table found for specified lookupTableName=%s",
                    mapping.getLookupTableName()));
            return;
        }

        LookupTable currentTable = null;
        for (LookupTable lookupTable : session.getMapping().getLookupTables().getLookupTable()) {
            if (lookupTable.getName() != null && lookupTable.getName().equals(mapping.getLookupTableName())) {
                currentTable = lookupTable;
            }
        }

        if (currentTable.getLookupEntry() == null || currentTable.getLookupEntry().isEmpty()) {
            logger.warn(String.format("Lookup table lookupTableName=%s does not contain any entries",
                    mapping.getLookupTableName()));
            return;
        }

        for (LookupEntry entry : currentTable.getLookupEntry()) {
            for (Field sourceField : mapping.getSourceField()) {
                if (entry.getSourceValue().equals(sourceField.getValue())) {
                    sourceField.setValue(entry.getTargetValue());
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                                String.format("Processing lookup value for iP=%s iV=%s lksV=%s lksT=%s lktV=%s lktT=%s",
                                        sourceField.getPath(), sourceField.getValue(), entry.getSourceValue(),
                                        entry.getSourceType(), entry.getTargetValue(), entry.getTargetType()));
                    }
                }
            }
        }

    }

    protected Field processSeparateField(AtlasSession session, Mapping mapping, Field sourceField, Field targetField)
            throws AtlasException {
        if (targetField.getIndex() == null || targetField.getIndex() < 0) {
            logger.warn(String.format("Separate requires Index value to be set on targetField.path=%s", targetField.getPath()));
            addAudit(session, targetField.getDocId(),
                    String.format("Separate requires Index value to be set on targetField.path=%s",
                            targetField.getPath()),
                    targetField.getPath(), AuditStatus.ERROR, null);
            return null;
        }

        Field sourceFieldSep = mapping.getSourceField().get(0);
        if ((sourceFieldSep.getFieldType() != null && !FieldType.STRING.equals(sourceFieldSep.getFieldType())
                || (sourceFieldSep.getValue() == null
                        || !sourceFieldSep.getValue().getClass().isAssignableFrom(String.class)))) {
            logger.warn(String.format("Separate requires String field type for sourceField.path=%s", sourceFieldSep.getPath()));
            addAudit(session, targetField.getDocId(), String.format("Separate requires String field type for sourceField.path=%s", sourceFieldSep.getPath()),
                    targetField.getPath(), AuditStatus.WARN, null);
            return null;
        }

        String sourceValue = (String) sourceFieldSep.getValue();
        List<String> separatedValues = null;
        if (mapping.getDelimiter() != null) {
            separatedValues = session.getAtlasContext().getContextFactory().getSeparateStrategy()
                    .separateValue(sourceValue, mapping.getDelimiter());
        } else {
            separatedValues = session.getAtlasContext().getContextFactory().getSeparateStrategy()
                    .separateValue(sourceValue);
        }

        if (separatedValues == null || separatedValues.isEmpty()) {
            logger.debug(
                    String.format("Empty string for Separate mapping sourceField.path=%s", sourceFieldSep.getPath()));
            return null;
        }

        if (separatedValues.size() < targetField.getIndex()) {
            logger.error(String.format(
                    "Separate returned fewer segements count=%s when targetField.path=%s requested index=%s",
                    separatedValues.size(), targetField.getPath(), targetField.getIndex()));
            addAudit(session, targetField.getDocId(),
                    String.format(
                            "Separate returned fewer segements count=%s when targetField.path=%s requested index=%s",
                            separatedValues.size(), targetField.getPath(), targetField.getIndex()),
                    targetField.getPath(), AuditStatus.ERROR, null);
            return null;
        }

        SimpleField simpleField = AtlasModelFactory.cloneFieldToSimpleField(sourceFieldSep);
        simpleField.setValue(separatedValues.get(targetField.getIndex()));
        return simpleField;
    }

    protected void processCombineField(AtlasSession session, Mapping mapping, List<Field> sourceFields,
            Field targetField) throws AtlasException {
        Map<Integer, String> combineValues = null;
        for (Field sourceField : sourceFields) {
            if (sourceField.getIndex() == null || sourceField.getIndex() < 0) {
                logger.error(
                        String.format("Combine requires Index value to be set on all sourceFields sourceField.path=%s",
                                sourceField.getPath()));
                addAudit(session, targetField.getDocId(),
                        String.format("Combine requires Index value to be set on all sourceFields sourceField.path=%s",
                                sourceField.getPath()),
                        targetField.getPath(), AuditStatus.ERROR, null);
                return;
            }
            if ((sourceField.getFieldType() != null && !FieldType.STRING.equals(sourceField.getFieldType())
                    || (sourceField.getValue() != null
                            && !sourceField.getValue().getClass().isAssignableFrom(String.class)))) {
                logger.error(String.format("Combine requires String field type for sourceField.path=%s",
                        sourceField.getPath()));
                addAudit(session, targetField.getDocId(), String
                        .format("Combine requires String field type for sourceField.path=%s", sourceField.getPath()),
                        targetField.getPath(), AuditStatus.WARN, null);
                continue;
            }

            if (combineValues == null) {
                // We need to support a sorted map w/ null values
                combineValues = new HashMap<Integer, String>();
            }

            combineValues.put(sourceField.getIndex(), (String) sourceField.getValue());
        }

        String combinedValue = null;
        if (mapping.getDelimiter() != null) {
            combinedValue = session.getAtlasContext().getContextFactory().getCombineStrategy()
                    .combineValues(combineValues, mapping.getDelimiter());
        } else {
            combinedValue = session.getAtlasContext().getContextFactory().getCombineStrategy()
                    .combineValues(combineValues);
        }

        if (combinedValue == null || combinedValue.trim().isEmpty()) {
            logger.debug(String.format("Empty combined string for Combine mapping targetField.path=%s",
                    targetField.getPath()));
            return;
        }

        targetField.setValue(combinedValue);
    }

    protected void processLookupField(AtlasSession session, String lookupTableName, String sourceValue,
            Field targetField) throws AtlasException {
        LookupTable table = null;
        for (LookupTable t : session.getMapping().getLookupTables().getLookupTable()) {
            if (t.getName().equals(lookupTableName)) {
                table = t;
                break;
            }
        }
        if (table == null) {
            throw new AtlasException("Could not find lookup table with name '" + lookupTableName + "' for targetField: "
                    + targetField.getPath());
        }

        String lookupValue = null;
        FieldType lookupType = null;
        for (LookupEntry lkp : table.getLookupEntry()) {
            if (lkp.getSourceValue().equals(sourceValue)) {
                lookupValue = lkp.getTargetValue();
                lookupType = lkp.getTargetType();
                break;
            }
        }

        Object targetValue = null;
        if (lookupType == null || FieldType.STRING.equals(lookupType)) {
            targetValue = lookupValue;
        } else {
            targetValue = getConversionService().convertType(lookupValue, FieldType.STRING, lookupType);
        }

        if (targetField.getFieldType() != null && !targetField.getFieldType().equals(lookupType)) {
            targetValue = getConversionService().convertType(targetValue, lookupType, targetField.getFieldType());
        }

        targetField.setValue(targetValue);
    }

    protected void addAudit(AtlasSession session, String docId, String message, String path, AuditStatus status,
            String value) {
        Audit audit = new Audit();
        audit.setDocId(docId);
        audit.setMessage(message);
        audit.setPath(path);
        audit.setStatus(status);
        audit.setValue(value);
        session.getAudits().getAudit().add(audit);
    }

    protected void processFieldActions(AtlasFieldActionService fieldActionService, Field field) throws AtlasException {
        field.setValue(fieldActionService.processActions(field.getActions(), field.getValue(), field.getFieldType()));
    }

    @Override
    public AtlasModuleMode getMode() {
        return this.atlasModuleMode;
    }

    @Override
    public void setMode(AtlasModuleMode atlasModuleMode) {
        this.atlasModuleMode = atlasModuleMode;
    }

    @Override
    public Boolean isStatisticsSupported() {
        return false;
    }

    @Override
    public Boolean isStatisticsEnabled() {
        return false;
    }

    @Override
    public List<AtlasModuleMode> listSupportedModes() {
        return Arrays.asList(AtlasModuleMode.SOURCE, AtlasModuleMode.TARGET);
    }

    @Override
    public AtlasConversionService getConversionService() {
        return atlasConversionService;
    }

    @Override
    public void setConversionService(AtlasConversionService atlasConversionService) {
        this.atlasConversionService = atlasConversionService;
    }

}
