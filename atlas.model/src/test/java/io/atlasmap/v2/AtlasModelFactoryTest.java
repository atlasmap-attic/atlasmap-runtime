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
package io.atlasmap.v2;

import org.junit.Test;

import io.atlasmap.v2.AtlasModelFactory;

import static org.junit.Assert.*;

public class AtlasModelFactoryTest {
	
	@Test
	public void testCreateSeparateFieldMapping() {
		SeparateFieldMapping fm = AtlasModelFactory.createFieldMapping(SeparateFieldMapping.class);
		assertNotNull(fm);
		assertNull(fm.getAlias());
		assertNull(fm.getDescription());
		assertNotNull(fm.getOutputFields());
		assertNotNull(fm.getOutputFields().getMappedField());
		assertNull(fm.getInputField());
		assertEquals(new Integer(0), new Integer(fm.getOutputFields().getMappedField().size()));
	}
	
	@Test
	public void testCreateMapFieldMapping() {
		MapFieldMapping fm = AtlasModelFactory.createFieldMapping(MapFieldMapping.class);
		assertNotNull(fm);
		assertNull(fm.getAlias());
		assertNull(fm.getDescription());
		assertNull(fm.getOutputField());
		assertNull(fm.getInputField());
	}
	
	@Test
	public void testCreateCombineFieldMapping() {
		CombineFieldMapping fm = AtlasModelFactory.createFieldMapping(CombineFieldMapping.class);
		assertNotNull(fm);
		assertNull(fm.getAlias());
		assertNull(fm.getDescription());
		assertNotNull(fm.getInputFields());
		assertNotNull(fm.getInputFields().getMappedField());
		assertNull(fm.getOutputField());
		assertEquals(new Integer(0), new Integer(fm.getInputFields().getMappedField().size()));
	}
	
	@Test
	public void testCreateLookupFieldMapping() {
		LookupFieldMapping fm = AtlasModelFactory.createFieldMapping(LookupFieldMapping.class);
		assertNotNull(fm);
		assertNull(fm.getInputField());
		assertNull(fm.getOutputField());
		assertNull(fm.getAlias());
		assertNull(fm.getDescription());
		assertNull(fm.getLookupTableName());
	}

}
