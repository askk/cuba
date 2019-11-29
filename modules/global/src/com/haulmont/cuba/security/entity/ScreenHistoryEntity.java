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
package com.haulmont.cuba.security.entity;

import com.haulmont.chile.core.annotations.MetaProperty;
import com.haulmont.cuba.core.entity.BaseUuidEntity;
import com.haulmont.cuba.core.entity.Creatable;
import com.haulmont.cuba.core.entity.TenantEntity;
import com.haulmont.cuba.core.entity.ReferenceToEntity;
import com.haulmont.cuba.core.entity.annotation.EmbeddedParameters;
import com.haulmont.cuba.core.entity.annotation.SystemLevel;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.UserFormatTools;
import com.haulmont.cuba.core.global.UserSessionSource;
import com.haulmont.cuba.security.global.UserSession;

import javax.annotation.PostConstruct;
import javax.persistence.*;
import java.util.Date;

/**
 * Screen history item.
 */
@Entity(name = "sec$ScreenHistory")
@Table(name = "SEC_SCREEN_HISTORY")
@SystemLevel
public class ScreenHistoryEntity extends BaseUuidEntity implements Creatable, TenantEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "CREATE_TS")
    protected Date createTs;

    @Column(name = "CREATED_BY", length = 50)
    protected String createdBy;

    @Column(name = "SYS_TENANT_ID")
    protected String sysTenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID")
    protected User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SUBSTITUTED_USER_ID")
    protected User substitutedUser;

    @Column(name = "CAPTION", length = 255)
    protected String caption;

    @Column(name = "URL", length = 4000)
    protected String url;

    @Embedded
    @EmbeddedParameters(nullAllowed = false)
    private ReferenceToEntity entityRef;

    @Override
    public Date getCreateTs() {
        return createTs;
    }

    @Override
    public void setCreateTs(Date createTs) {
        this.createTs = createTs;
    }

    @Override
    public String getCreatedBy() {
        return createdBy;
    }

    @Override
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getSysTenantId() {
        return sysTenantId;
    }

    public void setSysTenantId(String sysTenantId) {
        this.sysTenantId = sysTenantId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public User getSubstitutedUser() {
        return substitutedUser;
    }

    public void setSubstitutedUser(User substitutedUser) {
        this.substitutedUser = substitutedUser;
    }

    public ReferenceToEntity getEntityRef() {
        return entityRef;
    }

    public void setEntityRef(ReferenceToEntity entityRef) {
        this.entityRef = entityRef;
    }

    public void setObjectEntityId(Object entity) {
        if (entityRef == null) {
            entityRef = AppBeans.get(Metadata.class).create(ReferenceToEntity.class);
        }
        entityRef.setObjectEntityId(entity);
    }

    public Object getObjectEntityId() {
        return entityRef == null ? null : entityRef.getObjectEntityId();
    }

    @MetaProperty
    public String getDisplayUser() {
        UserFormatTools formatTools = AppBeans.get(UserFormatTools.NAME);
        return formatTools.formatUser(user, substitutedUser);
    }

    @PostConstruct
    protected void init() {
        UserSession userSession = AppBeans.get(UserSessionSource.class).getUserSession();
        setUser(userSession.getUser());
        setSubstitutedUser(userSession.getSubstitutedUser());
        Metadata metadata = AppBeans.get(Metadata.NAME);
        entityRef = metadata.create(ReferenceToEntity.class);
    }
}