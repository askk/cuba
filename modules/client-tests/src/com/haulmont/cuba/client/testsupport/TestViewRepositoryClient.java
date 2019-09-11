/*
 * Copyright (c) 2008-2016 Haulmont.
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
 *
 */

package com.haulmont.cuba.client.testsupport;

import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.sys.AbstractViewRepository;
import com.haulmont.cuba.core.sys.ResourcesImpl;
import com.haulmont.cuba.core.sys.ViewLoader;
import org.apache.commons.lang3.StringUtils;

public class TestViewRepositoryClient extends AbstractViewRepository {

    private String viewsConfig;

    public TestViewRepositoryClient(String viewsConfig) {
        this.viewsConfig = viewsConfig;
        this.resources = new ResourcesImpl(getClass().getClassLoader());
        this.viewLoader = new ViewLoader();
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
        this.viewLoader.setMetadata(metadata);
    }

    @Override
    protected void init() {
        initialized = true;
        if (!StringUtils.isEmpty(viewsConfig))
            deployViews(viewsConfig);
    }
}