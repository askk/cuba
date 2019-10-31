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

package com.haulmont.cuba.security.app.group;

import com.google.common.base.Strings;
import com.haulmont.chile.core.datatypes.Datatype;
import com.haulmont.chile.core.datatypes.DatatypeRegistry;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.DataManager;
import com.haulmont.cuba.core.global.GlobalConfig;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.security.entity.*;
import com.haulmont.cuba.security.group.AccessGroupDefinition;
import com.haulmont.cuba.security.group.GroupIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.Serializable;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component(GroupsRepository.NAME)
public class GroupsRepositoryImpl implements GroupsRepository {
    @Inject
    protected Persistence persistence;

    @Inject
    protected Metadata metadata;

    @Inject
    protected DataManager dataManager;

    @Inject
    protected DatatypeRegistry datatypes;

    @Inject
    protected GlobalConfig config;

    @Autowired(required = false)
    protected List<AccessGroupDefinition> groupDefinitions;

    protected Map<String, AccessGroupDefinition> groupDefinitionsByName;

    private final Logger log = LoggerFactory.getLogger(GroupsRepositoryImpl.class);

    @PostConstruct
    protected void init() {
        groupDefinitionsByName = new ConcurrentHashMap<>();
        if (groupDefinitions != null) {
            for (AccessGroupDefinition groupDefinition : groupDefinitions) {
                groupDefinitionsByName.put(groupDefinition.getName(), groupDefinition);
            }
        }
    }

    @Override
    public AccessGroupDefinition getGroupDefinition(GroupIdentifier identifier) {
        if (identifier.getDbId() != null) {
            return getGroupDefinitionFromDB(identifier.getDbId());
        } else if (identifier.getGroupName() != null) {
            return getGroupDefinitionFromAnnotations(identifier.getGroupName());
        }
        throw new IllegalArgumentException(String.format("%s isn't valid", identifier));
    }

    @Override
    public Collection<AccessGroupDefinition> getGroupDefinitions() {
        return Collections.unmodifiableCollection(groupDefinitionsByName.values());
    }

    @Override
    public void registerGroupDefinition(AccessGroupDefinition groupDefinition) {
        groupDefinitionsByName.put(groupDefinition.getName(), groupDefinition);
    }

    protected AccessGroupDefinition getGroupDefinitionFromAnnotations(String groupName) {
        AccessGroupDefinition groupDefinition = groupDefinitionsByName.get(groupName);
        if (groupDefinition == null) {
            throw new IllegalStateException(String.format("Unable to find predefined group definition %s", groupName));
        }
        return groupDefinition;
    }

    protected AccessGroupDefinition getGroupDefinitionFromDB(UUID groupId) {
        return persistence.callInTransaction(em -> {
            AccessGroupDefinitionBuilder groupDefinitionBuilder = AccessGroupDefinitionBuilder.create();

            Group group = em.find(Group.class, groupId);

            //noinspection ConstantConditions
            List<Constraint> constraints = new ArrayList<>(group.getConstraints());

            List<Constraint> parentConstraints = em.createQuery("select c from sec$GroupHierarchy h join h.parent.constraints c " +
                    "where h.group.id = ?1", Constraint.class)
                    .setParameter(1, groupId)
                    .getResultList();

            constraints.addAll(parentConstraints);

            for (Constraint constraint : constraints) {
                processConstraints(constraint, groupDefinitionBuilder);
            }

            List<SessionAttribute> attributes = em.createQuery("select a from sec$GroupHierarchy h join h.parent.sessionAttributes a " +
                    "where h.group.id = ?1 order by h.level desc", SessionAttribute.class)
                    .setParameter(1, groupId)
                    .getResultList();

            Set<String> attributeKeys = new HashSet<>();
            for (SessionAttribute attribute : attributes) {
                Datatype datatype = datatypes.get(attribute.getDatatype());
                try {
                    if (attributeKeys.contains(attribute.getName())) {
                        log.warn("Duplicate definition of '{}' session attribute in the group hierarchy", attribute.getName());
                    }

                    groupDefinitionBuilder.withSessionAttribute(attribute.getName(), (Serializable) datatype.parse(attribute.getStringValue()));

                    attributeKeys.add(attribute.getName());
                } catch (ParseException e) {
                    throw new RuntimeException(String.format("Unable to load session attribute %s", attribute.getName()), e);
                }
            }

            return groupDefinitionBuilder.build();
        });
    }

    protected void processConstraints(Constraint constraint, AccessGroupDefinitionBuilder groupDefinitionBuilder) {
        if (Boolean.TRUE.equals(constraint.getIsActive())) {
            Class<? extends Entity> targetClass = metadata.getClassNN(constraint.getEntityName()).getJavaClass();
            if (constraint.getOperationType() == ConstraintOperationType.CUSTOM) {
                groupDefinitionBuilder.withCustomGroovyConstraint(targetClass, constraint.getCode(), constraint.getJoinClause());
            } else {
                for (EntityOp operation : constraint.getOperationType().toEntityOps()) {

                    if (EntityOp.READ == operation && !Strings.isNullOrEmpty(constraint.getWhereClause())) {
                        groupDefinitionBuilder.withJpqlConstraint(targetClass, constraint.getWhereClause(), constraint.getJoinClause());
                    }

                    if (!Strings.isNullOrEmpty(constraint.getGroovyScript())) {
                        groupDefinitionBuilder.withGroovyConstraint(targetClass, operation, constraint.getGroovyScript());
                    }

                }
            }
        }
    }
}
