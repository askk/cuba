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

package com.haulmont.cuba.client.sys;

import com.haulmont.cuba.client.ClientConfig;
import com.haulmont.cuba.core.app.LocalizedMessageService;
import com.haulmont.cuba.core.global.Configuration;
import com.haulmont.cuba.core.global.LocaleResolver;
import com.haulmont.cuba.core.global.Messages;
import com.haulmont.cuba.core.global.UserSessionSource;
import com.haulmont.cuba.core.sys.AbstractMessages;
import com.haulmont.cuba.core.sys.AppContext;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.perf4j.StopWatch;
import org.perf4j.slf4j.Slf4JStopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.net.SocketException;
import java.util.List;
import java.util.Locale;

@Component(Messages.NAME)
public class MessagesClientImpl extends AbstractMessages {

    @Inject
    protected LocalizedMessageService localizedMessageService;

    @Inject
    protected UserSessionSource userSessionSource;

    protected volatile boolean remoteSearch;

    protected ClientConfig clientConfig;

    private static final Logger log = LoggerFactory.getLogger(MessagesClientImpl.class);

    @Inject
    @Override
    public void setConfiguration(Configuration configuration) {
        super.setConfiguration(configuration);
        clientConfig = configuration.getConfig(ClientConfig.class);
        remoteSearch = clientConfig.getRemoteMessagesSearchEnabled();
    }

    @Override
    protected Locale getUserLocale() {
        return userSessionSource.checkCurrentUserSession() ?
                userSessionSource.getUserSession().getLocale() :
                messageTools.getDefaultLocale();
    }

    @Override
    protected String searchRemotely(String pack, String key, Locale locale) {
        if (!remoteSearch || !AppContext.isStarted())
            return null;

        if (log.isTraceEnabled())
            log.trace("searchRemotely: " + pack + "/" +  LocaleResolver.localeToString(locale) + "/" + key);

        StopWatch stopWatch = new Slf4JStopWatch("Messages.searchRemotely");
        try {
            String message = localizedMessageService.getMessage(pack, key, locale);
            if (key.equals(message))
                return null;
            else
                return message;
        } catch (Exception e) {
            List list = ExceptionUtils.getThrowableList(e);
            for (Object throwable : list) {
                if (throwable instanceof SocketException) {
                    log.trace("searchRemotely: {}", throwable);
                    return null; // silently ignore network errors
                }
            }
            throw (RuntimeException) e;
        } finally {
            stopWatch.stop();
        }
    }

    public boolean isRemoteSearch() {
        return remoteSearch;
    }

    public void setRemoteSearch(boolean remoteSearch) {
        this.remoteSearch = remoteSearch && clientConfig.getRemoteMessagesSearchEnabled();
    }
}
