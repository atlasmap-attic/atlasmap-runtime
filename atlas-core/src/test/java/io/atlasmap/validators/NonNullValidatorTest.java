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
package io.atlasmap.validators;

import io.atlasmap.v2.Validation;
import io.atlasmap.validators.NonNullValidator;

import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.After;
import org.junit.Before;

public class NonNullValidatorTest extends BaseValidatorTest {

    @Before
    public void setUp() {
        super.setUp();
        validator = new NonNullValidator("qwerty", "Cannot be null");
    }
    
    @After
    public void tearDown() {
        super.tearDown();
        validator = null;
    }
    
    @Test
    public void testSupports() throws Exception {
        assertTrue(validator.supports(String.class));
        assertTrue(validator.supports(Integer.class));
        assertTrue(validator.supports(Double.class));
    }

    @Test
    public void testValidate() throws Exception {
        String notNull = "notNull";
        validator.validate(notNull, validations);
        assertFalse(validationHelper.hasErrors());
    }

    @Test
    public void testValidateInvalid() throws Exception {
        validator.validate(null, validations);
        assertTrue(validationHelper.hasErrors());
        assertEquals(new Integer(1), new Integer(validationHelper.getCount()));

        Validation validation = validationHelper.getAllValidations().get(0);
        assertNotNull(validation);

        // TODO: Support rejected value assertNull(validation.getRejectedValue());
        assertTrue("Cannot be null".equals(validation.getMessage()));
        assertTrue("qwerty".equals(validation.getField()));

        String empty = "";
        validationHelper.getAllValidations().clear();

        validator.validate(empty, validations);

        assertTrue(validationHelper.hasErrors());
        assertEquals(new Integer(1), new Integer(validationHelper.getCount()));
    }

}