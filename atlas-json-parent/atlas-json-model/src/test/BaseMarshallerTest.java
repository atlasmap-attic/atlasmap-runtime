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
package io.atlasmap.json.v2;

import io.atlasmap.v2.*;
import io.atlasmap.json.v2.InspectionType;
import io.atlasmap.json.v2.JsonField;
import io.atlasmap.json.v2.JsonInspectionRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.Assert.*;

public abstract class BaseMarshallerTest {

    public boolean deleteTestFolders = true;

    @Rule
    public TestName testName = new TestName();

    @Before
    public void setUp() throws Exception {
        Files.createDirectories(Paths.get("target/junit/" + testName.getMethodName()));
    }

    @After
    public void tearDown() throws Exception {
        if (deleteTestFolders) {
            Path directory = Paths.get("target/junit/" + testName.getMethodName());
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc == null) {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    } else {
                        throw exc;
                    }
                }
            });
        }
    }

    protected AtlasMapping generateAtlasMapping() {
        AtlasMapping mapping = AtlasModelFactory.createAtlasMapping();
        mapping.setName("junit");

        JsonField sourceJsonField = new JsonField();
        sourceJsonField.setName("foo");
        sourceJsonField.setValue("bar");

        JsonField targetJsonField = new JsonField();
        targetJsonField.setName("woot");
        targetJsonField.setValue("blerg");

        Mapping fm = AtlasModelFactory.createMapping(MappingType.MAP);
        fm.getSourceField().add(sourceJsonField);
        fm.getTargetField().add(targetJsonField);

        mapping.getMappings().getMapping().add(fm);
        return mapping;
    }

    protected AtlasMapping generateCollectionMapping() {
        AtlasMapping innerMapping1 = generateAtlasMapping();
        AtlasMapping innerMapping2 = generateAtlasMapping();

        Collection cMapping = AtlasModelFactory.createMapping(MappingType.COLLECTION);
        cMapping.getMappings().getMapping().addAll(innerMapping1.getMappings().getMapping());
        cMapping.getMappings().getMapping().addAll(innerMapping2.getMappings().getMapping());
        cMapping.setCollectionType(CollectionType.LIST);

        AtlasMapping mapping = generateAtlasMapping();
        mapping.getMappings().getMapping().clear();
        mapping.getMappings().getMapping().add(cMapping);
        return mapping;
    }

    protected AtlasMapping generateCombineMapping() {

        JsonField sourceJsonField = new JsonField();
        sourceJsonField.setName("foo");
        sourceJsonField.setValue("bar");
        sourceJsonField.setIndex(0);

        JsonField sourceJsonFieldB = new JsonField();
        sourceJsonFieldB.setName("foo3");
        sourceJsonFieldB.setValue("bar3");
        sourceJsonFieldB.setIndex(1);

        JsonField targetJsonFieldA = new JsonField();
        targetJsonFieldA.setName("woot");
        targetJsonFieldA.setValue("blerg");

        Mapping fm = AtlasModelFactory.createMapping(MappingType.COMBINE);
        fm.getSourceField().add(sourceJsonField);
        fm.getSourceField().add(sourceJsonFieldB);
        fm.getTargetField().add(targetJsonFieldA);

        AtlasMapping mapping = generateAtlasMapping();
        mapping.getMappings().getMapping().clear();
        mapping.getMappings().getMapping().add(fm);
        return mapping;
    }

    protected AtlasMapping generatePropertyReferenceMapping() {
        AtlasMapping mapping = generateAtlasMapping();

        MappedField sourceField = new MappedField();
        PropertyField sourcePropertyField = new PropertyField();
        sourcePropertyField.setName("foo");
        sourceField.setField(sourcePropertyField);

        MapFieldMapping fm = (MapFieldMapping) mapping.getFieldMappings().getFieldMapping().get(0);
        fm.setSourceField(sourceField);

        Property p = new Property();
        p.setName("foo");
        p.setValue("bar");
        mapping.setProperties(new Properties());
        mapping.getProperties().getProperty().add(p);
        return mapping;
    }

    protected AtlasMapping generateConstantMapping() {
        AtlasMapping mapping = generateAtlasMapping();

        MappedField sourceField = new MappedField();
        ConstantField sourcePropertyField = new ConstantField();
        sourcePropertyField.setValue("foo");
        sourceField.setField(sourcePropertyField);

        MapFieldMapping fm = (MapFieldMapping) mapping.getFieldMappings().getFieldMapping().get(0);
        fm.setSourceField(sourceField);

        return mapping;
    }

    protected AtlasMapping generateMultiSourceMapping() {
        AtlasMapping mapping = generateSeparateAtlasMapping();
        mapping.setSourceUri(null);
        mapping.setTargetUri(null);

        mapping.setSources(new DataSources());
        mapping.setTargets(new DataSources());

        DataSource sourceA = new DataSource();
        sourceA.setUri("someSourceURI:A");
        sourceA.setDocId("sourceA");
        mapping.getSources().getDataSource().add(sourceA);

        DataSource targetA = new DataSource();
        targetA.setUri("someTargetURI:A");
        targetA.setDocId("targetA");
        mapping.getTargets().getDataSource().add(targetA);

        DataSource targetB = new DataSource();
        targetB.setUri("someTargetURI:B");
        targetB.setDocId("targetB");
        mapping.getTargets().getDataSource().add(targetB);

        SeparateFieldMapping fm = (SeparateFieldMapping) mapping.getFieldMappings().getFieldMapping().get(0);
        fm.getSourceField().getField().setDocId("sourceA");
        fm.getTargetFields().getMappedField().get(0).getField().setDocId("targetA");
        fm.getTargetFields().getMappedField().get(1).getField().setDocId("targetB");

        return mapping;
    }

    protected void validateAtlasMapping(AtlasMapping mapping) {
        assertNotNull(mapping);
        assertNotNull(mapping.getName());
        assertEquals("junit", mapping.getName());
        assertNotNull(mapping.getFieldMappings());
        assertEquals(new Integer(1), new Integer(mapping.getFieldMappings().getFieldMapping().size()));
        assertNull(mapping.getProperties());

        MapFieldMapping fm = (MapFieldMapping) mapping.getFieldMappings().getFieldMapping().get(0);
        assertNotNull(fm);
        assertNull(fm.getAlias());

        MappedField m1 = fm.getSourceField();
        assertNotNull(m1);
        assertNull(m1.getFieldActions());
        // assertTrue(m1.getFieldActions().isEmpty());
        assertNotNull(m1.getField());
        Field f1 = m1.getField();
        assertTrue(f1 instanceof JsonField);
        assertEquals("foo", ((JsonField) f1).getName());
        assertEquals("bar", f1.getValue());
        assertNull(((JsonField) f1).getType());

        MappedField m2 = fm.getTargetField();
        assertNotNull(m2);
        assertNull(m2.getFieldActions());
        // assertTrue(m2.getFieldActions().isEmpty());
        assertNotNull(m2.getField());
        Field f2 = m2.getField();
        assertTrue(f2 instanceof JsonField);
        assertEquals("woot", ((JsonField) f2).getName());
        assertEquals("blerg", f2.getValue());
        assertNull(((JsonField) f2).getType());

    }

    protected AtlasMapping generateSeparateAtlasMapping() {
        AtlasMapping mapping = new AtlasMapping();
        mapping.setName("junit");
        mapping.setFieldMappings(new FieldMappings());

        MappedField sourceField = new MappedField();
        JsonField sourceJsonField = new JsonField();
        sourceJsonField.setName("foo");
        sourceJsonField.setValue("bar");
        sourceField.setField(sourceJsonField);

        MappedField targetFieldA = new MappedField();
        JsonField targetJsonFieldA = new JsonField();
        targetJsonFieldA.setName("woot");
        targetJsonFieldA.setValue("blerg");
        targetFieldA.setField(targetJsonFieldA);

        MapAction targetActionA = new MapAction();
        targetActionA.setIndex(new Integer(1));
        targetFieldA.setFieldActions(new FieldActions());
        // targetFieldA.getFieldActions().getFieldAction().add(targetActionA);

        MappedField targetFieldB = new MappedField();
        JsonField targetJsonFieldB = new JsonField();
        targetJsonFieldB.setName("meow");
        targetJsonFieldB.setValue("ruff");
        targetFieldB.setField(targetJsonFieldB);

        MapAction targetActionB = new MapAction();
        targetActionB.setIndex(new Integer(2));
        targetFieldB.setFieldActions(new FieldActions());
        // targetFieldB.getFieldActions().getFieldAction().add(targetActionB);

        SeparateFieldMapping fm = AtlasModelFactory.createFieldMapping(SeparateFieldMapping.class);
        fm.setSourceField(sourceField);
        fm.getTargetFields().getMappedField().add(targetFieldA);
        fm.getTargetFields().getMappedField().add(targetFieldB);

        mapping.getFieldMappings().getFieldMapping().add(fm);
        return mapping;
    }

    protected void validateSeparateAtlasMapping(AtlasMapping mapping) {
        assertNotNull(mapping);
        assertNotNull(mapping.getName());
        assertEquals("junit", mapping.getName());
        assertNotNull(mapping.getFieldMappings());
        assertEquals(new Integer(1), new Integer(mapping.getFieldMappings().getFieldMapping().size()));
        assertNull(mapping.getProperties());

        FieldMapping fm = mapping.getFieldMappings().getFieldMapping().get(0);
        assertNotNull(fm);
        assertTrue(fm instanceof SeparateFieldMapping);
        assertNull(fm.getAlias());

        SeparateFieldMapping sfm = (SeparateFieldMapping) fm;
        MappedField m1 = sfm.getSourceField();
        assertNotNull(m1);
        assertNull(m1.getFieldActions());
        // assertEquals(new Integer(0), new
        // Integer(m1.getFieldActions().getFieldAction().size()));
        assertNotNull(m1.getField());
        Field f1 = m1.getField();
        assertTrue(f1 instanceof JsonField);
        assertEquals("foo", ((JsonField) f1).getName());
        assertEquals("bar", f1.getValue());
        assertNull(((JsonField) f1).getType());

        MappedFields mFields = sfm.getTargetFields();
        MappedField m2 = mFields.getMappedField().get(0);
        assertNotNull(m2);
        assertNotNull(m2.getFieldActions());
        // assertEquals(new Integer(1), new
        // Integer(m2.getFieldActions().getFieldAction().size()));
        assertNotNull(m2.getField());
        Field f2 = m2.getField();
        assertTrue(f2 instanceof JsonField);
        assertEquals("woot", ((JsonField) f2).getName());
        assertEquals("blerg", f2.getValue());
        assertNull(((JsonField) f2).getType());

        MappedField m3 = mFields.getMappedField().get(1);
        assertNotNull(m3);
        assertNotNull(m3.getFieldActions());
        // assertEquals(new Integer(1), new
        // Integer(m3.getFieldActions().getFieldAction().size()));
        assertNotNull(m3.getField());
        Field f3 = m3.getField();
        assertTrue(f3 instanceof JsonField);
        assertEquals("meow", ((JsonField) f3).getName());
        assertEquals("ruff", f3.getValue());
        assertNull(((JsonField) f3).getType());

    }

    public JsonInspectionRequest generateInspectionRequest() {
        JsonInspectionRequest jsonInspectionRequest = new JsonInspectionRequest();
        jsonInspectionRequest.setType(InspectionType.INSTANCE);

        final String jsonData = "<data>\n" + "     <intField a='1'>32000</intField>\n"
                + "     <longField>12421</longField>\n" + "     <stringField>abc</stringField>\n"
                + "     <booleanField>true</booleanField>\n" + "     <doubleField b='2'>12.0</doubleField>\n"
                + "     <shortField>1000</shortField>\n" + "     <floatField>234.5f</floatField>\n"
                + "     <charField>A</charField>\n" + "</data>";
        jsonInspectionRequest.setJsonData(jsonData);
        return jsonInspectionRequest;
    }
}
