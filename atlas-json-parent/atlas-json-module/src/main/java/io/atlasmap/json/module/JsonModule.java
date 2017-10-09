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
package io.atlasmap.json.module;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.atlasmap.api.AtlasConversionException;
import io.atlasmap.api.AtlasException;
import io.atlasmap.api.AtlasSession;
import io.atlasmap.api.AtlasValidationException;
import io.atlasmap.core.AtlasUtil;
import io.atlasmap.core.BaseAtlasModule;
import io.atlasmap.core.PathUtil;
import io.atlasmap.core.PathUtil.SegmentContext;
import io.atlasmap.json.core.JsonFieldReader;
import io.atlasmap.json.core.JsonFieldWriter;
import io.atlasmap.json.v2.AtlasJsonModelFactory;
import io.atlasmap.json.v2.JsonField;
import io.atlasmap.spi.AtlasModuleDetail;
import io.atlasmap.spi.AtlasModuleMode;
import io.atlasmap.v2.AuditStatus;
import io.atlasmap.v2.BaseMapping;
import io.atlasmap.v2.ConstantField;
import io.atlasmap.v2.DataSource;
import io.atlasmap.v2.DataSourceType;
import io.atlasmap.v2.Field;
import io.atlasmap.v2.FieldType;
import io.atlasmap.v2.Mapping;
import io.atlasmap.v2.PropertyField;
import io.atlasmap.v2.SimpleField;
import io.atlasmap.v2.Validation;
import io.atlasmap.v2.Validations;

@AtlasModuleDetail(name = "JsonModule", uri = "atlas:json", modes = { "SOURCE", "TARGET" }, dataFormats = {
        "json" }, configPackages = { "io.atlasmap.json.v2" })
public class JsonModule extends BaseAtlasModule {
    private static final Logger logger = LoggerFactory.getLogger(JsonModule.class);

    @Override
    public void processPreTargetExecution(AtlasSession session) throws AtlasException {
        JsonFieldWriter writer = new JsonFieldWriter();
        session.setTarget(writer);

        if (logger.isDebugEnabled()) {
            logger.debug("processPreTargetExcution completed");
        }
    }

    @Override
    public void processPreValidation(AtlasSession atlasSession) throws AtlasException {
        if (atlasSession == null || atlasSession.getMapping() == null) {
            logger.error("Invalid session: Session and AtlasMapping must be specified");
            throw new AtlasValidationException("Invalid session");
        }

        Validations validations = atlasSession.getValidations();
        JsonValidationService jsonValidationService = new JsonValidationService(getConversionService());
        List<Validation> jsonValidations = jsonValidationService.validateMapping(atlasSession.getMapping());
        if (jsonValidations != null && !jsonValidations.isEmpty()) {
            validations.getValidation().addAll(jsonValidations);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Detected " + jsonValidations.size() + " json validation notices");
        }

        if (logger.isDebugEnabled()) {
            logger.debug("processPreValidation completed");
        }
    }

    @Override
    public void processSourceMapping(AtlasSession session, BaseMapping baseMapping) throws AtlasException {
        for (Mapping mapping : this.generateSourceMappings(session, baseMapping)) {
            if (mapping.getSourceField() == null || mapping.getSourceField().isEmpty()
                    || mapping.getSourceField().size() != 1) {
                addAudit(session, null,
                        String.format("Mapping does not contain exactly one source field alias=%s desc=%s",
                                mapping.getAlias(), mapping.getDescription()),
                        null, AuditStatus.WARN, null);
                return;
            }

            Field field = mapping.getSourceField().get(0);

            if (!isSupportedField(field)) {
                addAudit(session, field.getDocId(),
                        String.format("Unsupported source field type=%s", field.getClass().getName()), field.getPath(),
                        AuditStatus.ERROR, null);
                return;
            }

            if (field instanceof ConstantField) {
                processConstantField(session, mapping);
                if (logger.isDebugEnabled()) {
                    logger.debug("Processed source constantField sPath=" + field.getPath() + " sV=" + field.getValue()
                            + " sT=" + field.getFieldType() + " docId: " + field.getDocId());
                }
                continue;
            }

            if (field instanceof PropertyField) {
                processPropertyField(session, mapping,
                        session.getAtlasContext().getContextFactory().getPropertyStrategy());
                if (logger.isDebugEnabled()) {
                    logger.debug("Processed source propertyField sPath=" + field.getPath() + " sV=" + field.getValue()
                            + " sT=" + field.getFieldType() + " docId: " + field.getDocId());
                }
                continue;
            }

            JsonField sourceField = (JsonField) field;

            Object sourceObject = null;
            if (field.getDocId() != null && session.hasSource(field.getDocId())) {
                // Use docId only when it exists, otherwise use default source
                sourceObject = session.getSource(field.getDocId());
            } else {
                sourceObject = session.getSource();
            }

            if (sourceObject == null || !(sourceObject instanceof String)) {
                addAudit(session, field.getDocId(),
                        String.format("Unsupported source object type=%s", field.getClass().getName()), field.getPath(),
                        AuditStatus.ERROR, null);
                return;
            }

            String document = (String) sourceObject;

//            Map<String, String> sourceUriParams = AtlasUtil
//                    .getUriParameters(session.getMapping().getDataSource().get(0).getUri());

            JsonFieldReader djfr = new JsonFieldReader();
            djfr.read(document, sourceField);

            // NOTE: This shouldn't happen
            if (sourceField.getFieldType() == null) {
                logger.warn(
                        String.format("FieldType detection was unsuccessful for p=%s falling back to type UNSUPPORTED",
                                sourceField.getPath()));
                sourceField.setFieldType(FieldType.UNSUPPORTED);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Processed source field sPath=" + field.getPath() + " sV=" + field.getValue() + " sT="
                        + field.getFieldType() + " docId: " + field.getDocId());
            }
        }
    }

    @Override
    public void processTargetMapping(AtlasSession session, BaseMapping baseMapping) throws AtlasException {

        JsonFieldWriter writer = null;
        if (session.getTarget() == null) {
            writer = new JsonFieldWriter();
            session.setTarget(writer);
        } else if (session.getTarget() != null && session.getTarget() instanceof JsonFieldWriter) {
            writer = (JsonFieldWriter) session.getTarget();
        } else {
            addAudit(session, null,
                    String.format("Unsupported target object type=%s", session.getTarget().getClass().getName()), null,
                    AuditStatus.ERROR, null);
            return;
        }

        for (Mapping mapping : this.getTargetMappings(session, baseMapping)) {
            if (mapping.getTargetField() == null || mapping.getTargetField().isEmpty()) {
                addAudit(session, null,
                        String.format("Mapping does not contain at least one target field alias=%s desc=%s",
                                mapping.getAlias(), mapping.getDescription()),
                        null, AuditStatus.ERROR, null);
                return;
            }

            Field targetField = mapping.getTargetField().get(0);
            if (!(targetField instanceof JsonField)) {
                addAudit(session, targetField.getDocId(),
                        String.format("Unsupported target field type=%s", targetField.getClass().getName()),
                        targetField.getPath(), AuditStatus.ERROR, null);
                logger.error(String.format("Unsupported field type %s", targetField.getClass().getName()));
                return;
            }

            switch (mapping.getMappingType()) {
            case MAP:
                Field sourceField = mapping.getSourceField().get(0);
                if (sourceField.getValue() == null) {
                    continue;
                }

                // Attempt to Auto-detect field type based on source value
                if (targetField.getFieldType() == null && sourceField.getValue() != null) {
                    targetField.setFieldType(getConversionService().fieldTypeFromClass(sourceField.getValue().getClass()));
                }

                Object targetValue = null;

                // Do auto-conversion
                if (sourceField.getFieldType() != null && sourceField.getFieldType().equals(targetField.getFieldType())) {
                    targetValue = sourceField.getValue();
                } else {
                    try {
                        targetValue = getConversionService().convertType(sourceField.getValue(), sourceField.getFieldType(),
                                targetField.getFieldType());
                    } catch (AtlasConversionException e) {
                        logger.error(String.format("Unable to auto-convert for iT=%s oT=%s oF=%s msg=%s",
                                sourceField.getFieldType(), targetField.getFieldType(), targetField.getPath(),
                                e.getMessage()), e);
                        continue;
                    }
                }

                targetField.setValue(targetValue);

                if (targetField.getActions() != null && targetField.getActions().getActions() != null
                        && !targetField.getActions().getActions().isEmpty()) {
                    processFieldActions(session.getAtlasContext().getContextFactory().getFieldActionService(),
                            targetField);
                }

                writer.write((JsonField) targetField);
                break;
            case COMBINE:
                processCombineField(session, mapping, mapping.getSourceField(), targetField);
                SimpleField combinedField = new SimpleField();
                combinedField.setFieldType(FieldType.STRING);
                combinedField.setPath(targetField.getPath());
                combinedField.setValue(targetField.getValue());

                if (combinedField.getActions() != null && combinedField.getActions().getActions() != null
                        && !combinedField.getActions().getActions().isEmpty()) {
                    processFieldActions(session.getAtlasContext().getContextFactory().getFieldActionService(),
                            combinedField);
                }

                writer.write(combinedField);
                break;
            case LOOKUP:
                Field sourceFieldLkp = mapping.getSourceField().get(0);
                if (sourceFieldLkp.getValue() != null
                        && sourceFieldLkp.getValue().getClass().isAssignableFrom(String.class)) {
                    processLookupField(session, mapping.getLookupTableName(), (String) sourceFieldLkp.getValue(),
                            targetField);
                } else {
                    processLookupField(session, mapping.getLookupTableName(), (String) getConversionService()
                            .convertType(sourceFieldLkp.getValue(), sourceFieldLkp.getFieldType(), FieldType.STRING),
                            targetField);
                }

                if (targetField.getActions() != null && targetField.getActions().getActions() != null
                        && !targetField.getActions().getActions().isEmpty()) {
                    processFieldActions(session.getAtlasContext().getContextFactory().getFieldActionService(),
                            targetField);
                }

                writer.write(targetField);
                break;
            case SEPARATE:
                Field sourceFieldSep = mapping.getSourceField().get(0);
                for (Field targetFieldSep : mapping.getTargetField()) {
                    Field separateField = processSeparateField(session, mapping, sourceFieldSep, targetFieldSep);
                    if (separateField == null) {
                        continue;
                    }

                    targetFieldSep.setValue(separateField.getValue());
                    if (targetFieldSep.getFieldType() == null) {
                        targetFieldSep.setFieldType(separateField.getFieldType());
                    }

                    if (targetFieldSep.getActions() != null && targetFieldSep.getActions().getActions() != null
                            && !targetFieldSep.getActions().getActions().isEmpty()) {
                        processFieldActions(session.getAtlasContext().getContextFactory().getFieldActionService(),
                                targetFieldSep);
                    }
                    writer.write(targetFieldSep);
                }
                break;
            default:
                logger.error("Unsupported mappingType=%s detected", mapping.getMappingType());
                return;
            }

            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Processed target field oP=%s oV=%s oT=%s docId: %s", targetField.getPath(),
                        targetField.getValue(), targetField.getFieldType(), targetField.getDocId()));
            }
        }
    }

    @Override
    public void processPostTargetExecution(AtlasSession session) throws AtlasException {

        List<String> docIds = new ArrayList<String>();
        for (DataSource ds : session.getMapping().getDataSource()) {
            if (DataSourceType.TARGET.equals(ds.getDataSourceType()) && ds.getUri().startsWith("atlas:json")) {
                docIds.add(ds.getId());
            }
        }

        // for(String docId : docIds) {
        // Object target = session.getTarget(docId);
        Object target = session.getTarget();
        if (target instanceof JsonFieldWriter) {
            if (((JsonFieldWriter) target).getRootNode() != null) {
                String targetBody = ((JsonFieldWriter) target).getRootNode().toString();
                session.setTarget(targetBody);
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format("processPostTargetExecution converting JsonNode to string size=%s",
                            targetBody.length()));
                }
            } else {
                // TODO: handle error where rootnode on DocumentJsonFieldWriter is set to null
                // (which should never happen).
            }
        } else {
            logger.error("DocumentJsonFieldWriter object expected for Json target data source");
        }
        // }

        if (logger.isDebugEnabled()) {
            logger.debug("processPostTargetExecution completed");
        }
    }

    @Override
    public List<AtlasModuleMode> listSupportedModes() {
        return Arrays.asList(AtlasModuleMode.SOURCE, AtlasModuleMode.TARGET);
    }

    @Override
    public Boolean isSupportedField(Field field) {
        if (field instanceof JsonField) {
            return true;
        } else if (field instanceof PropertyField) {
            return true;
        } else if (field instanceof ConstantField) {
            return true;
        } else if (field instanceof SimpleField) {
            return true;
        }
        return false;
    }

    @Override
    public int getCollectionSize(AtlasSession session, Field field) throws AtlasException {
        String sourceDocument = null;
        if (field.getDocId() != null && session.hasSource(field.getDocId())) {
            // Use docId only when it exists, otherwise use default source
            sourceDocument = (String) session.getSource(field.getDocId());
        } else {
            sourceDocument = (String) session.getSource();
        }

        // make this a JSON document
        JsonFactory jsonFactory = new JsonFactory();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonParser parser = jsonFactory.createParser(sourceDocument);
            JsonNode rootNode = objectMapper.readTree(parser);
            ObjectNode parentNode = (ObjectNode) rootNode;
            String parentSegment = "[root node]";
            for (SegmentContext sc : new PathUtil(field.getPath()).getSegmentContexts(false)) {
                JsonNode currentNode = JsonFieldWriter.getChildNode(parentNode, parentSegment, sc.getSegment());
                if (currentNode == null) {
                    return 0;
                }
                if (PathUtil.isCollectionSegment(sc.getSegment())) {
                    if (currentNode != null && currentNode.isArray()) {
                        return currentNode.size();
                    }
                    return 0;
                }
                parentNode = (ObjectNode) currentNode;
            }
        } catch (IOException e) {
            throw new AtlasException(e.getMessage(), e);
        }
        return 0;
    }

    @Override
    public Field cloneField(Field field) throws AtlasException {
        return AtlasJsonModelFactory.cloneField(field);
    }
}
