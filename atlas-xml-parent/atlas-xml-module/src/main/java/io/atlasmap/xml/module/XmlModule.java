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
package io.atlasmap.xml.module;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import io.atlasmap.api.AtlasConversionException;
import io.atlasmap.api.AtlasException;
import io.atlasmap.api.AtlasSession;
import io.atlasmap.api.AtlasValidationException;
import io.atlasmap.core.AtlasUtil;
import io.atlasmap.core.BaseAtlasModule;
import io.atlasmap.core.PathUtil;
import io.atlasmap.core.PathUtil.SegmentContext;
import io.atlasmap.spi.AtlasModuleDetail;
import io.atlasmap.v2.Audit;
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
import io.atlasmap.xml.v2.AtlasXmlModelFactory;
import io.atlasmap.xml.core.XmlFieldReader;
import io.atlasmap.xml.core.XmlFieldWriter;
import io.atlasmap.xml.v2.XmlDataSource;
import io.atlasmap.xml.v2.XmlField;
import io.atlasmap.xml.v2.XmlNamespace;
import io.atlasmap.xml.v2.XmlNamespaces;

@AtlasModuleDetail(name = "XmlModule", uri = "atlas:xml", modes = { "SOURCE", "TARGET" }, dataFormats = {
        "xml" }, configPackages = { "io.atlasmap.xml.v2" })
public class XmlModule extends BaseAtlasModule {
    private static final Logger logger = LoggerFactory.getLogger(XmlModule.class);

    @Override
    public void processPreTargetExecution(AtlasSession session) throws AtlasException {
        XmlNamespaces xmlNs = null;
        String template = null;
        for (DataSource ds : session.getMapping().getDataSource()) {
            if (DataSourceType.TARGET.equals(ds.getDataSourceType()) && ds instanceof XmlDataSource) {
                xmlNs = ((XmlDataSource) ds).getXmlNamespaces();
                template = ((XmlDataSource) ds).getTemplate();
            }
        }

        Map<String, String> nsMap = new HashMap<String, String>();
        if (xmlNs != null && xmlNs.getXmlNamespace() != null && !xmlNs.getXmlNamespace().isEmpty()) {
            for (XmlNamespace ns : xmlNs.getXmlNamespace()) {
                nsMap.put(ns.getAlias(), ns.getUri());
            }
        }

        XmlFieldWriter writer = new XmlFieldWriter(nsMap, template);
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

        XmlValidationService xmlValidationService = new XmlValidationService(getConversionService());
        List<Validation> xmlValidations = xmlValidationService.validateMapping(atlasSession.getMapping());
        atlasSession.getValidations().getValidation().addAll(xmlValidations);

        if (logger.isDebugEnabled()) {
            logger.debug("Detected " + xmlValidations.size() + " xml validation notices");
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

            XmlField sourceField = (XmlField) field;

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

            Document document = null;

            Map<String, String> sourceUriParams = AtlasUtil
                    .getUriParameters(session.getMapping().getDataSource().get(0).getUri());

            boolean enableNamespaces = true;
            for (String key : sourceUriParams.keySet()) {
                if ("disableNamespaces".equals(key)) {
                    if ("true".equals(sourceUriParams.get("disableNamespaces"))) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Disabling namespace support");
                        }
                        enableNamespaces = false;
                    }
                }
            }

            try {
                document = getDocument((String) sourceObject, enableNamespaces);
            } catch (IOException | ParserConfigurationException | SAXException e) {
                logger.error(String.format("Error parsing xml source object msg=%s", e.getMessage()), e);
                Audit audit = new Audit();
                audit.setDocId(field.getDocId());
                audit.setPath(field.getPath());
                audit.setStatus(AuditStatus.ERROR);
                audit.setMessage(String.format("Error parsing xml source object msg=%s", field.getClass().getName()));
                session.getAudits().getAudit().add(audit);
                return;
            }

            XmlFieldReader dxfr = new XmlFieldReader();
            dxfr.readNew(document, sourceField);

            if (sourceField.getFieldType() == null) {
                sourceField.setFieldType(FieldType.STRING);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Processed source field sPath=" + field.getPath() + " sV=" + field.getValue() + " sT="
                        + field.getFieldType() + " docId: " + field.getDocId());
            }
        }
    }

    @Override
    public void processTargetMapping(AtlasSession session, BaseMapping baseMapping) throws AtlasException {

        XmlFieldWriter writer = null;
        if (session.getTarget() == null) {
            writer = new XmlFieldWriter();
            session.setTarget(writer);
        } else if (session.getTarget() != null && session.getTarget() instanceof XmlFieldWriter) {
            writer = (XmlFieldWriter) session.getTarget();
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
            if (!(targetField instanceof XmlField)) {
                addAudit(session, targetField.getDocId(),
                        String.format("Unsupported target field type=%s", targetField.getClass().getName()),
                        targetField.getPath(), AuditStatus.ERROR, null);
                return;
            }

            switch (mapping.getMappingType()) {
            case MAP:
                Field inField = mapping.getSourceField().get(0);
                if (inField.getValue() == null) {
                    continue;
                }

                // Attempt to Auto-detect field type based on source value
                if (targetField.getFieldType() == null && inField.getValue() != null) {
                    targetField.setFieldType(getConversionService().fieldTypeFromClass(inField.getValue().getClass()));
                }

                Object targetValue = null;

                // Do auto-conversion
                if (inField.getFieldType() != null && inField.getFieldType().equals(targetField.getFieldType())) {
                    targetValue = inField.getValue();
                } else {
                    try {
                        targetValue = getConversionService().convertType(inField.getValue(), inField.getFieldType(),
                                targetField.getFieldType());
                    } catch (AtlasConversionException e) {
                        logger.error(String.format("Unable to auto-convert for iT=%s oT=%s oF=%s msg=%s",
                                inField.getFieldType(), targetField.getFieldType(), targetField.getPath(),
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

                writer.write((XmlField) targetField);
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
        if (logger.isDebugEnabled()) {
            logger.debug("processPostTargetExecution completed");
        }

        Object target = session.getTarget();
        if (target != null) {
            if (target instanceof XmlFieldWriter) {
                session.setTarget(convertDocumentToString(((XmlFieldWriter) target).getDocument()));
            }
        }
    }

    @Override
    public Boolean isSupportedField(Field field) {
        if (field instanceof XmlField) {
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

    private String convertDocumentToString(Document document) throws AtlasException {
        DocumentBuilderFactory domFact = DocumentBuilderFactory.newInstance();
        domFact.setNamespaceAware(true);

        StringWriter writer = null;
        try {
            DOMSource domSource = new DOMSource(document);
            writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.transform(domSource, result);
            return writer.toString();
        } catch (TransformerException e) {
            logger.error(String.format("Error converting Xml document to string msg=%s", e.getMessage()), e);
            throw new AtlasException(e.getMessage(), e);
        }
    }

    private Document getDocument(String data, boolean namespaced)
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(namespaced); // this must be done to use namespaces
        DocumentBuilder b = dbf.newDocumentBuilder();
        return b.parse(new ByteArrayInputStream(data.getBytes("UTF-8")));
    }

    @Override
    public int getCollectionSize(AtlasSession session, Field field) throws AtlasException {
        try {
            Object sourceObject = null;
            if (field.getDocId() != null && session.hasSource(field.getDocId())) {
                // Use docId only when it exists, otherwise use default source
                sourceObject = session.getSource(field.getDocId());
            } else {
                sourceObject = session.getSource();
            }
            Document document = getDocument((String) sourceObject, false);
            Element parentNode = document.getDocumentElement();
            for (SegmentContext sc : new PathUtil(field.getPath()).getSegmentContexts(false)) {
                if (sc.getPrev() == null) {
                    // processing root node part of path such as the "XOA" part of
                    // "/XOA/contact<>/firstName", skip.
                    continue;
                }
                String childrenElementName = PathUtil.cleanPathSegment(sc.getSegment());
                String namespaceAlias = PathUtil.getNamespace(sc.getSegment());
                if (namespaceAlias != null && !"".equals(namespaceAlias)) {
                    childrenElementName = namespaceAlias + ":" + childrenElementName;
                }
                List<Element> children = XmlFieldWriter.getChildrenWithName(childrenElementName, parentNode);
                if (children == null || children.isEmpty()) {
                    return 0;
                }
                if (PathUtil.isCollectionSegment(sc.getSegment())) {
                    return children.size();
                }
                parentNode = children.get(0);
            }
            return 0;
        } catch (Exception e) {
            throw new AtlasException(e);
        }
    }

    @Override
    public Field cloneField(Field field) throws AtlasException {
        return AtlasXmlModelFactory.cloneField(field);
    }
}
