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
package io.atlasmap.validators;

import io.atlasmap.core.AtlasMappingUtil;
import io.atlasmap.spi.AtlasValidator;
import io.atlasmap.v2.*;
import io.atlasmap.validators.AtlasValidationTestHelper;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseValidatorTest {

    protected static Logger logger = LoggerFactory.getLogger(BaseValidatorTest.class);

    // protected io.atlasmap.java.v2.ObjectFactory javaModelFactory = new
    // io.atlasmap.java.v2.ObjectFactory();
    protected AtlasMappingUtil mappingUtil = new AtlasMappingUtil("io.atlasmap.v2");
    protected AtlasValidationTestHelper validationHelper = null;
    protected List<Validation> validations = null;
    protected AtlasValidator validator = null;

    @Before
    public void setUp() {
        validationHelper = new AtlasValidationTestHelper();
        validations = validationHelper.getValidation();
    }

    @After
    public void tearDown() {
        validationHelper = null;
        validations = null;
    }

    protected AtlasMapping getAtlasMappingFullValid() throws Exception {
        AtlasMapping mapping = AtlasModelFactory.createAtlasMapping();

        mapping.setName("thisis_a_valid.name");

        DataSource src = new DataSource();
        src.setDataSourceType(DataSourceType.SOURCE);
        src.setUri("atlas:java?2");

        DataSource tgt = new DataSource();
        tgt.setDataSourceType(DataSourceType.TARGET);
        tgt.setUri("atlas:java?3");

        mapping.getDataSource().add(src);
        mapping.getDataSource().add(tgt);

        Mapping mapFieldMapping = AtlasModelFactory.createMapping(MappingType.MAP);

        Field sourceField = createJavaField("sourceName");
        mapFieldMapping.getSourceField().add(sourceField);

        Field targetField = createJavaField("targetName");
        mapFieldMapping.getTargetField().add(targetField);

        Mapping separateMapping = AtlasModelFactory.createMapping(MappingType.SEPARATE);

        Field sIJavaField = createJavaField("sourceName");
        separateMapping.getSourceField().add(sIJavaField);

        Field sOJavaField = createJavaField("targetName");
        sOJavaField.setIndex(0);
        separateMapping.getTargetField().add(sOJavaField);

        mapping.getMappings().getMapping().add(mapFieldMapping);
        mapping.getMappings().getMapping().add(separateMapping);
        return mapping;
    }

    protected void createMockMappedFields(AtlasMapping mapping, Mapping mapFieldMapping) {
        // Mock MappedField
        MockField sourceField = new MockField();
        sourceField.setName("source.name");
        MockField targetField = new MockField();
        targetField.setName("target.name");

        mapFieldMapping.getSourceField().add(sourceField);
        mapFieldMapping.getTargetField().add(targetField);

        mapping.getMappings().getMapping().add(mapFieldMapping);
    }

    protected AtlasMapping getAtlasMappingWithLookupTables(String... names) throws Exception {
        AtlasMapping mapping = this.getAtlasMappingFullValid();
        LookupTables lookupTables = new LookupTables();
        mapping.setLookupTables(lookupTables);
        for (String name : names) {
            LookupTable lookupTable = new LookupTable();
            lookupTable.setName(name);
            lookupTable.setDescription("desc_".concat(name));
            lookupTables.getLookupTable().add(lookupTable);

            Mapping lookupFieldMapping = AtlasModelFactory.createMapping(MappingType.LOOKUP);
            lookupFieldMapping.setDescription("field_desc_".concat(name));
            lookupFieldMapping.setLookupTableName(name);

            Field sourceField = createJavaField("sourceName");
            Field targetField = createJavaField("targetName");

            lookupFieldMapping.getSourceField().add(sourceField);
            lookupFieldMapping.getTargetField().add(targetField);
            mapping.getMappings().getMapping().add(lookupFieldMapping);
        }

        return mapping;
    }

    protected Field createJavaField(String name) {
        MockField javaField = AtlasModelFactory.createMockField();
        javaField.setFieldType(FieldType.STRING);
        javaField.setCustom("java.lang.String");
        javaField.setName(name);
        return javaField;
    }

    protected void debugErrors(Validations validations) {
        for (Validation validation : validations.getValidation()) {
            logger.debug(AtlasValidationTestHelper.validationToString(validation));
        }
    }
}
