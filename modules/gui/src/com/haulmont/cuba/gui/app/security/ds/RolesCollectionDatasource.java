/*
 * Copyright (c) 2008-2019 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.cuba.gui.app.security.ds;

import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.gui.data.impl.CustomCollectionDatasource;
import com.haulmont.cuba.security.designtime.RolesService;
import com.haulmont.cuba.security.entity.Role;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * INTERNAL
 */
public class RolesCollectionDatasource extends CustomCollectionDatasource<Role, UUID> {

    protected RolesService rolesService = AppBeans.get(RolesService.NAME);

    @Override
    protected Collection<Role> getEntities(Map<String, Object> params) {
        return rolesService.getAllRoles();
    }

}
