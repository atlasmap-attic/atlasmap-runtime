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
package io.atlasmap.spi;

import java.util.List;

import io.atlasmap.api.AtlasConversionService;
import io.atlasmap.api.AtlasException;
import io.atlasmap.api.AtlasSession;
import io.atlasmap.v2.BaseMapping;
import io.atlasmap.v2.Field;

public interface AtlasModule {

    void init();

    void destroy();

    void processPreValidation(AtlasSession session) throws AtlasException;

    void processPreSourceExecution(AtlasSession session) throws AtlasException;

    void processSourceMapping(AtlasSession session, BaseMapping mapping) throws AtlasException;

    void processSourceActions(AtlasSession session, BaseMapping mapping) throws AtlasException;

    void processPostSourceExecution(AtlasSession session) throws AtlasException;

    void processPreTargetExecution(AtlasSession session) throws AtlasException;

    void processTargetMapping(AtlasSession session, BaseMapping mapping) throws AtlasException;

    void processTargetActions(AtlasSession session, BaseMapping mapping) throws AtlasException;

    void processPostTargetExecution(AtlasSession session) throws AtlasException;

    void processPostValidation(AtlasSession session) throws AtlasException;

    AtlasModuleMode getMode();

    void setMode(AtlasModuleMode atlasModuleMode);

    AtlasConversionService getConversionService();

    void setConversionService(AtlasConversionService atlasConversionService);

    List<AtlasModuleMode> listSupportedModes();

    Boolean isStatisticsSupported();

    Boolean isStatisticsEnabled();

    Boolean isSupportedField(Field field);

}
