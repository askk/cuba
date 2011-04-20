/*
 * Copyright (c) 2011 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.

 * Author: Sergey Ovchinnikov
 * Created: 13.04.11 11:17
 *
 * $Id$
 */

package com.haulmont.cuba.core.app;

import com.haulmont.cuba.core.*;
import com.haulmont.cuba.core.entity.SendingAttachment;
import com.haulmont.cuba.core.entity.SendingMessage;
import com.haulmont.cuba.core.global.ConfigProvider;
import com.haulmont.cuba.core.global.SendingStatus;
import com.haulmont.cuba.core.global.TimeProvider;
import com.haulmont.cuba.core.global.View;
import com.haulmont.cuba.core.sys.AppContext;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.ManagedBean;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

@ManagedBean(EmailManagerMBean.NAME)
public class EmailManager extends ManagementBean implements EmailManagerMBean {

    private Log log = LogFactory.getLog(EmailManager.class);

    private Set<SendingMessage> messageQueue;
    private static int callCount = 0;
    private static final int MAX_SENDING_TIME_SEC = 120;

    @Inject
    private ThreadPoolTaskExecutor mailSendTaskExecutor;
    private EmailerConfig config;

    @Inject
    public void setConfig(ConfigProvider configProvider) {
        this.config = configProvider.doGetConfig(EmailerConfig.class);
    }

    @Override
    protected Credentials getCredentialsForLogin() {
        return new Credentials(AppContext.getProperty(EmailerAPI.NAME + ".login"), AppContext.getProperty(EmailerAPI.NAME + ".password"));
    }

    public void queueEmailsToSend() {
        try {
            int delay = config.getDelayCallCount();
            if (callCount >= delay) {
                loginOnce();
                List<SendingMessage> loadedMessages = loadEmailsToSend();
                List<SendingMessage> updatedMessages = updateSendingMessagesStatus(loadedMessages);

                if (messageQueue == null)
                    messageQueue = new LinkedHashSet<SendingMessage>();
                messageQueue.addAll(updatedMessages);

                List<SendingMessage> processedMessages = new ArrayList<SendingMessage>();
                List<UUID> notSentMessageIds = new ArrayList<UUID>();
                for (SendingMessage msg : messageQueue) {
                    if (needToSetStatusNotSent(msg))
                        notSentMessageIds.add(msg.getId());
                    else {
                        sendAsync(msg);
                    }
                    processedMessages.add(msg);
                }
                messageQueue.removeAll(processedMessages);
                if (!notSentMessageIds.isEmpty())
                    updateSendingMessagesStatus(notSentMessageIds, SendingStatus.NOTSENT);
            } else {
                callCount++;
            }
        } catch (Throwable e) {
            log.error(EmailManagerMBean.NAME + " error:" + ExceptionUtils.getStackTrace(e));
        }
    }

    private void updateSendingMessagesStatus(List<UUID> messages, SendingStatus status) {
        StringBuffer updateQueryStr = new StringBuffer();
        updateQueryStr.append("update sys$SendingMessage sm set sm.status= :status, sm.updateTs = :currentTime")
                .append("\t where sm.id in (:list)");
        Transaction tx = Locator.createTransaction();
        try {
            EntityManager em = PersistenceProvider.getEntityManager();
            em.createQuery(updateQueryStr.toString())
                    .setParameter("status", status.getId())
                    .setParameter("list", messages)
                    .setParameter("currentTime", TimeProvider.currentTimestamp())
                    .executeUpdate();
            tx.commit();
        } finally {
            tx.end();
        }
    }

    private boolean needToSetStatusNotSent(SendingMessage sendingMessage) {
        if (sendingMessage.getDeadline() != null && sendingMessage.getDeadline().getTime() < TimeProvider.currentTimestamp().getTime())
            return true;
        else if (sendingMessage.getAttemptsCount() != null && sendingMessage.getAttemptsMade() != null && sendingMessage.getAttemptsMade() >= sendingMessage.getAttemptsCount())
            return true;
        return false;
    }

    private void sendAsync(SendingMessage msg) {
        try {
            Runnable mailSendTask = new EmailSendTask(msg);
            mailSendTaskExecutor.execute(mailSendTask);
        } catch (RejectedExecutionException e) {
            updateSendingMessageStatus(msg, SendingStatus.QUEUE);
        } catch (Exception e) {
            log.error("Exception while sending email: " + ExceptionUtils.getStackTrace(e));
            updateSendingMessageStatus(msg, SendingStatus.QUEUE);
        }
    }

    private List<SendingMessage> loadEmailsToSend() {
        Transaction tx = Locator.createTransaction();
        try {
            EntityManager em = PersistenceProvider.getEntityManager();
            View view = new View(SendingMessage.class, true)
                    .addProperty("attachments", new View(SendingAttachment.class, true)
                            .addProperty("content")
                            .addProperty("contentId")
                            .addProperty("name")
                            .addProperty("message"))
                    .addProperty("caption")
                    .addProperty("address")
                    .addProperty("from")
                    .addProperty("contentText")
                    .addProperty("deadline")
                    .addProperty("attemptsCount")
                    .addProperty("attemptsMade")
                    .addProperty("version")
                    .addProperty("status");
            em.setView(view);
            Query query = em.createQuery("select sm from sys$SendingMessage sm " +
                    "where sm.status=:statusQueue \n" +
                    "\t or (sm.status = :statusSending and sm.updateTs<:time)" +
                    "\t order by sm.createTs")
                    .setParameter("statusQueue", SendingStatus.QUEUE.getId())
                    .setParameter("time", DateUtils.addSeconds(TimeProvider.currentTimestamp(), -MAX_SENDING_TIME_SEC))
                    .setParameter("statusSending", SendingStatus.SENDING.getId());
            List<SendingMessage> res = query.setMaxResults(config.getMessageQueueSize()).getResultList();
            tx.commit();
            return res;
        } finally {
            tx.end();
        }
    }

    private List<SendingMessage> updateSendingMessagesStatus(List<SendingMessage> messageList) {
        if (messageList == null || messageList.isEmpty())
            return Collections.emptyList();

        List<SendingMessage> messagesToRemove = new ArrayList<SendingMessage>();
        Transaction tx = Locator.createTransaction();
        try {
            EntityManager em = PersistenceProvider.getEntityManager();

            for (SendingMessage msg : messageList) {
                int recordForUpdateCount = 0;
                String queryStr = "update sys$SendingMessage sm set sm.status= :status, sm.updateTs= :currentTime, sm.updatedBy = :user, sm.version= :version1 " +
                        "\t where sm.id =:id and sm.version=:version";
                Query query = em.createQuery(queryStr);
                query.setParameter("status", SendingStatus.SENDING.getId());
                query.setParameter("currentTime", TimeProvider.currentTimestamp());
                query.setParameter("user", SecurityProvider.currentUserSession().getUser().getLogin());
                query.setParameter("version", msg.getVersion());
                query.setParameter("version1", msg.getVersion() + 1);
                query.setParameter("id", msg.getId());
                recordForUpdateCount += query.executeUpdate();
                if (recordForUpdateCount == 0)
                    messagesToRemove.add(msg);
            }
            tx.commit();
        } finally {
            tx.end();
        }
        List<SendingMessage> res = new ArrayList<SendingMessage>();
        res.addAll(messageList);
        res.removeAll(messagesToRemove);
        return res;
    }

    public void addEmailsToQueue(List<SendingMessage> sendingMessageList) {
        Transaction tx = Locator.createTransaction();
        try {
            EntityManager em = PersistenceProvider.getEntityManager();
            for (SendingMessage message : sendingMessageList) {
                em.persist(message);
                if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
                    for (SendingAttachment attachment : message.getAttachments())
                        em.persist(attachment);
                }
            }
            tx.commit();
        } finally {
            tx.end();
        }
    }

    private void updateSendingMessageStatus(SendingMessage sendingMessage, SendingStatus status) {
        if (sendingMessage != null) {
            boolean increaseAttemptsMade = !status.equals(SendingStatus.SENDING);
            Date currentTimestamp = TimeProvider.currentTimestamp();

            Transaction tx = Locator.createTransaction();
            try {
                EntityManager em = PersistenceProvider.getEntityManager();
                StringBuffer queryStr = new StringBuffer("update sys$SendingMessage sm set sm.status = :status, sm.updateTs=:updateTs, sm.updatedBy = :updatedBy, sm.version = sm.version + 1 ");
                if (increaseAttemptsMade)
                    queryStr.append(", sm.attemptsMade = sm.attemptsMade + 1 ");
                if (status.equals(SendingStatus.SENT))
                    queryStr.append(", sm.dateSent = :dateSent");
                queryStr.append("\n where sm.id=:id");
                Query query = em.createQuery(queryStr.toString())
                        .setParameter("status", status.getId())
                        .setParameter("id", sendingMessage.getId())
                        .setParameter("updateTs", currentTimestamp)
                        .setParameter("updatedBy", SecurityProvider.currentUserSession().getUser().getLogin());
                if (status.equals(SendingStatus.SENT))
                    query.setParameter("dateSent", currentTimestamp);
                query.executeUpdate();
                tx.commit();
            } finally {
                tx.end();
            }
        }
    }


}
