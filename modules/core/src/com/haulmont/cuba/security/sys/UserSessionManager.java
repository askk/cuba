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
package com.haulmont.cuba.security.sys;

import com.haulmont.chile.core.datatypes.Datatype;
import com.haulmont.chile.core.datatypes.Datatypes;
import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.TypedQuery;
import com.haulmont.cuba.core.app.multitenancy.TenantProvider;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.core.sys.DefaultPermissionValuesConfig;
import com.haulmont.cuba.security.app.UserSessionsAPI;
import com.haulmont.cuba.security.entity.*;
import com.haulmont.cuba.security.global.NoUserSessionException;
import com.haulmont.cuba.security.global.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * INTERNAL.
 *
 * System-level class managing {@link UserSession}s.
 */
@Component(UserSessionManager.NAME)
public class UserSessionManager {

    private final Logger log = LoggerFactory.getLogger(UserSessionManager.class);

    public static final String NAME = "cuba_UserSessionManager";

    @Inject
    protected UuidSource uuidSource;

    @Inject
    protected UserSessionsAPI sessions;

    @Inject
    protected UserSessionSource userSessionSource;

    @Inject
    protected Persistence persistence;

    @Inject
    protected EntityStates entityStates;

    @Inject
    protected GlobalConfig globalConfig;

    @Inject
    protected Metadata metadata;

    @Inject
    protected DefaultPermissionValuesConfig defaultPermissionValuesConfig;

    /**
     * Create a new session and fill it with security data. Must be called inside a transaction.
     * @param user      user instance
     * @param locale    user locale
     * @param system    create system session
     * @return          new session instance
     */
    public UserSession createSession(User user, Locale locale, boolean system) {
        return createSession(uuidSource.createUuid(), user, locale, system);
    }

    /**
     * Create a new session and fill it with security data. Must be called inside a transaction.
     * @param sessionId target session id
     * @param user      user instance
     * @param locale    user locale
     * @param system    create system session
     * @return          new session instance
     */
    public UserSession createSession(UUID sessionId, User user, Locale locale, boolean system) {
        List<Role> roles = new ArrayList<>();
        for (UserRole userRole : user.getUserRoles()) {
            if (userRole.getRole() != null) {
                roles.add(userRole.getRole());
            }
        }
        UserSession session = new UserSession(sessionId, user, roles, locale, system);
        compilePermissions(session, roles);
        if (user.getGroup() == null)
            throw new IllegalStateException("User is not in a Group");
        compileConstraints(session, user.getGroup());
        compileSessionAttributes(session, user.getGroup());
        setTenantIdAttribute(session, user);
        return session;
    }

    /**
     * Create a new session from existing for another user and fill it with security data for that new user.
     * Must be called inside a transaction.
     * @param src   existing session
     * @param user  another user instance
     * @return      new session with the same ID as existing
     */
    public UserSession createSession(UserSession src, User user) {
        List<Role> roles = new ArrayList<>();
        for (UserRole userRole : user.getUserRoles()) {
            if (userRole.getRole() != null) {
                roles.add(userRole.getRole());
            }
        }
        UserSession session = new UserSession(src, user, roles, src.getLocale());
        compilePermissions(session, roles);
        if (user.getGroup() == null)
            throw new IllegalStateException("User is not in a Group");
        compileConstraints(session, user.getGroup());
        compileSessionAttributes(session, user.getGroup());
        setTenantIdAttribute(session, user);
        return session;
    }

    protected void compilePermissions(UserSession session, List<Role> roles) {
        for (Role role : roles) {
            if (RoleType.SUPER.equals(role.getType())) {
                // Don't waste memory, as the user with SUPER role has all permissions.
                return;
            }
        }
        for (Role role : roles) {
            for (Permission permission : role.getPermissions()) {
                PermissionType type = permission.getType();
                if (type != null && permission.getValue() != null) {
                    try {
                        session.addPermission(type,
                                permission.getTarget(), convertToExtendedEntityTarget(permission), permission.getValue());
                    } catch (Exception ignored) {}
                }
            }
        }

        defaultPermissionValuesConfig.getDefaultPermissionValues().forEach((target, permission) -> {
            if (session.getPermissionValue(permission.getType(), permission.getTarget()) == null) {
                session.addPermission(permission.getType(), permission.getTarget(),
                        convertToExtendedEntityTarget(permission), permission.getValue());
            }
        });
    }

    protected void setTenantIdAttribute(UserSession session, User user) {
        if (user.getTenantId() == null) {
            session.setAttribute(TenantProvider.TENANT_ID_ATTRIBUTE_NAME, TenantProvider.NO_TENANT);
        } else {
            session.setAttribute(TenantProvider.TENANT_ID_ATTRIBUTE_NAME, user.getTenantId());
        }
    }

    protected String convertToExtendedEntityTarget(Permission permission) {
        if (permission.getType() == PermissionType.ENTITY_OP || permission.getType() == PermissionType.ENTITY_ATTR) {
            String target = permission.getTarget();
            int pos = target.indexOf(Permission.TARGET_PATH_DELIMETER);
            if (pos > -1) {
                String entityName = target.substring(0, pos);
                Class extendedClass = metadata.getExtendedEntities().getExtendedClass(metadata.getClassNN(entityName));
                if (extendedClass != null) {
                    MetaClass extMetaClass = metadata.getClassNN(extendedClass);
                    return extMetaClass.getName() + Permission.TARGET_PATH_DELIMETER + target.substring(pos + 1);
                }
            }
        }
        return null;
    }

    protected void compileConstraints(UserSession session, Group group) {
        EntityManager em = persistence.getEntityManager();
        TypedQuery<Constraint> q = em.createQuery("select c from sec$GroupHierarchy h join h.parent.constraints c " +
                "where h.group.id = ?1", Constraint.class);
        q.setParameter(1, group.getId());
        List<Constraint> constraints = q.getResultList();
        List<Constraint> list = new ArrayList<>(constraints);
        list.addAll(group.getConstraints());
        for (Constraint constraint : list) {
            if (Boolean.TRUE.equals(constraint.getIsActive())) {
                session.addConstraint(constraint);
            }
        }
    }

    protected void compileSessionAttributes(UserSession session, Group group) {
        List<SessionAttribute> list = new ArrayList<>(group.getSessionAttributes());

        EntityManager em = persistence.getEntityManager();
        TypedQuery<SessionAttribute> q = em.createQuery("select a from sec$GroupHierarchy h join h.parent.sessionAttributes a " +
                "where h.group.id = ?1 order by h.level desc", SessionAttribute.class);
        q.setParameter(1, group.getId());
        List<SessionAttribute> attributes = q.getResultList();
        list.addAll(attributes);

        for (SessionAttribute attribute : list) {
            Datatype datatype = Datatypes.get(attribute.getDatatype());
            try {
                if (session.getAttributeNames().contains(attribute.getName())) {
                    log.warn("Duplicate definition of '{}' session attribute in the group hierarchy", attribute.getName());
                }
                Serializable value = (Serializable) datatype.parse(attribute.getStringValue());
                if (value != null)
                    session.setAttribute(attribute.getName(), value);
                else
                    session.removeAttribute(attribute.getName());
            } catch (ParseException e) {
                throw new RuntimeException("Unable to set session attribute " + attribute.getName(), e);
            }
        }
    }

    /**
     * @deprecated use {@link UserSessionsAPI#add(UserSession)}}
     */
    @Deprecated
    public void storeSession(UserSession session) {
        sessions.add(session);
    }

    /**
     * @deprecated use {@link UserSessionsAPI#remove(UserSession)}}
     */
    @Deprecated
    public void removeSession(UserSession session) {
        sessions.remove(session);
    }

    /**
     * @deprecated use {@link UserSessionsAPI#getNN(UUID)}}
     */
    @Deprecated
    public UserSession getSession(UUID sessionId) {
        UserSession session = findSession(sessionId);
        if (session == null) {
            throw new NoUserSessionException(sessionId);
        }
        return session;
    }

    /**
     * @deprecated use {@link UserSessionsAPI#get(UUID)}
     */
    @Deprecated
    public UserSession findSession(UUID sessionId) {
        return sessions.getAndRefresh(sessionId, false);
    }

    public Integer getPermissionValue(User user, PermissionType permissionType, String target) {
        Integer result;
        List<Role> roles = new ArrayList<>();

        Transaction tx = persistence.createTransaction();
        try {
            EntityManager em = persistence.getEntityManager();
            user = em.find(User.class, user.getId());
            for (UserRole userRole : user.getUserRoles()) {
                if (userRole.getRole() != null) {
                    roles.add(userRole.getRole());
                }
            }
            UserSession session = new UserSession(uuidSource.createUuid(), user, roles, userSessionSource.getLocale(), false);
            compilePermissions(session, roles);
            result = session.getPermissionValue(permissionType, target);
            tx.commit();
        } finally {
            tx.end();
        }
        return result; 
    }

    /**
     * INTERNAL
     */
    public void clearPermissionsOnUser(UserSession session) {
        List<User> users = new ArrayList<>();
        users.add(session.getUser());
        if (session.getSubstitutedUser() != null) {
            users.add(session.getSubstitutedUser());
        }
        for (User user : users) {
            if (entityStates.isDetached(user) && user.getUserRoles() != null) {
                for (UserRole userRole : user.getUserRoles()) {
                    Role role = userRole.getRole();
                    if (userRole.getRole() != null && entityStates.isLoaded(role, "permissions")) {
                        userRole.getRole().setPermissions(null);
                    }
                }
            }
        }
    }
}
