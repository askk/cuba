/*
 * Copyright (c) 2011 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */

package com.haulmont.cuba.client.testsupport;

import com.haulmont.cuba.core.app.PersistenceManagerService;
import com.haulmont.cuba.core.global.Configuration;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.UserSessionSource;
import com.haulmont.cuba.core.sys.AppContext;
import mockit.Mocked;
import mockit.NonStrictExpectations;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for building client-side integration tests.
 *
 * <p>$Id$</p>
 *
 * @author krivopustov
 */
public class CubaClientTestCase {

    private List<String> entityPackages = new ArrayList<String>();

    private String viewConfig;

    @Mocked
    protected AppContext appContext;

    @Mocked
    protected Configuration configuration;

    @Mocked
    protected PersistenceManagerService persistenceManager;

    protected TestMetadataClient metadata;

    protected TestUserSessionSource userSessionSource;

    /**
     * Add entities package to build metadata from. Should be invoked by concrete test classes in their @Before method.
     * @param pack  package FQN, e.g. <code>com.haulmont.cuba.core.entity</code>
     */
    protected void addEntityPackage(String pack) {
        entityPackages.add(pack);
    }

    /**
     * Set path to Views configuration file to build ViewRepository from it. Should be invoked by concrete test classes
     * once in their @Before method.
     * @param viewConfigPath    configuration resource FQN, e.g. <code>/com/haulmont/cuba/gui/data/impl/testmodel1/test-views.xml</code>
     */
    protected void setViewConfig(String viewConfigPath) {
        viewConfig = viewConfigPath;
    }

    /**
     * Set up {@link Metadata} and other infrastructure objects before running test. Should be invoked by concrete test classes
     * once in their @Before method.
     */
    protected void setupInfrastructure() {
        metadata = new TestMetadataClient(entityPackages, viewConfig);
        userSessionSource = new TestUserSessionSource();

        new NonStrictExpectations() {
            {
                AppContext.getProperty("cuba.confDir"); result = System.getProperty("user.dir");

                AppContext.getBean(Metadata.NAME); result = metadata;
                AppContext.getBean(Metadata.NAME, Metadata.class); result = metadata;

                AppContext.getBean(Configuration.NAME); result = configuration;
                AppContext.getBean(Configuration.NAME, Configuration.class); result = configuration;

                AppContext.getBean(PersistenceManagerService.NAME); result = persistenceManager;
                AppContext.getBean(PersistenceManagerService.NAME, PersistenceManagerService.class); result = persistenceManager;

                AppContext.getBean(UserSessionSource.NAME); result = userSessionSource;
                AppContext.getBean(UserSessionSource.NAME, UserSessionSource.class); result = userSessionSource;
            }
        };

    }
}
