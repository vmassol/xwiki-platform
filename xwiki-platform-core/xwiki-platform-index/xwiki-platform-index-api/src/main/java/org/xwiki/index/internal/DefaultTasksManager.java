/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.index.internal;

import java.util.Comparator;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLifecycleException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.component.phase.Disposable;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.index.TaskConsumer;
import org.xwiki.index.TaskManager;
import org.xwiki.index.internal.jmx.JMXTasks;
import org.xwiki.management.JMXBeanRegistration;
import org.xwiki.observation.remote.RemoteObservationManagerConfiguration;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;
import org.xwiki.wiki.manager.WikiManagerException;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.tasks.XWikiTask;
import com.xpn.xwiki.doc.tasks.XWikiTaskId;

import static java.lang.Thread.NORM_PRIORITY;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;

/**
 * Initialize a {@link PriorityBlockingQueue} with the tasks stored in database.
 *
 * @version $Id$
 * @since 14.1RC1
 */
@Component
@Singleton
public class DefaultTasksManager implements TaskManager, Initializable, Disposable
{
    private static final String MBEAN_NAME = "name=index";

    private PriorityBlockingQueue<TaskData> queue;

    @Inject
    private WikiDescriptorManager wikiDescriptorManager;

    @Inject
    private Provider<TasksStore> tasksStore;

    @Inject
    private Provider<ComponentManager> componentManager;

    @Inject
    private RemoteObservationManagerConfiguration remoteObservationManagerConfiguration;

    @Inject
    private JMXBeanRegistration jmxRegistration;

    @Inject
    private Logger logger;

    private Thread thread;

    @Override
    public void addTask(TaskData taskData, String wikiId)
    {
        try {
            taskData.setTimestamp(new Date().getTime());
            this.tasksStore.get().addTask(wikiId, convert(taskData));
        } catch (XWikiException e) {
            this.logger.warn("Failed to add task [{}] in wiki [{}]. This task is queued but will not be will not be"
                    + " restarted if not completed before the server stops. Cause: [{}].", taskData, wikiId,
                getRootCauseMessage(e));
        }

        this.queue.add(taskData);
    }

    @Override
    public void replaceTask(TaskData taskData, String wikiId)
    {
        try {
            taskData.setTimestamp(new Date().getTime());
            this.tasksStore.get().replaceTask(wikiId, convert(taskData));
        } catch (XWikiException e) {
            this.logger.warn("Failed to persist task [{}] in wiki [{}]. The tasks are replaced but will not be "
                    + "restarted if not completed before the server stops. Cause: [{}].", taskData, wikiId,
                getRootCauseMessage(e));
        }

        this.queue.removeIf(queuedTask -> Objects.equals(queuedTask.getWikiId(), wikiId)
            && Objects.equals(queuedTask.getKind(), taskData.getKind())
            && Objects.equals(queuedTask.getDocName(), taskData.getDocName()));
        this.queue.add(taskData);
    }

    @Override
    public void initialize() throws InitializationException
    {
        this.jmxRegistration.registerMBean(new JMXTasks(this::getQueueSize,
                () -> this.queue.stream().collect(Collectors.groupingBy(TaskData::getKind, Collectors.counting()))),
            MBEAN_NAME);
        this.queue = new PriorityBlockingQueue<>(11, Comparator.comparingLong(TaskData::getTimestamp));
    }

    @Override
    public void dispose() throws ComponentLifecycleException
    {
        this.jmxRegistration.unregisterMBean(MBEAN_NAME);
        this.queue.add(TaskData.STOP);
    }

    @Override
    public void startThread()
    {
        this.thread = new Thread(new TasksRunnable());
        thread.setName("task-manager-consumer");
        thread.setPriority(NORM_PRIORITY - 1);
        thread.start();
    }

    @Override
    public long getQueueSize()
    {
        return this.queue.size();
    }

    @Override
    public long getQueueSize(String kind)
    {
        return this.queue.stream().filter(taskData -> Objects.equals(taskData.getKind(), kind)).count();
    }

    private void loadWiki(String wikiId) throws InitializationException
    {
        try {
            this.queue.addAll(
                this.tasksStore.get().getAllTasks(wikiId, this.remoteObservationManagerConfiguration.getId())
                    .stream()
                    .map(task -> convert(wikiId, task))
                    .collect(Collectors.toList()));
        } catch (XWikiException e) {
            throw new InitializationException(String.format("Failed to get tasks for wiki [%s]", wikiId), e);
        }
    }

    private class TasksRunnable implements Runnable
    {
        private boolean halt;

        @Override
        public void run()
        {
            try {
                initQueue();
                while (!this.halt) {
                    consume();
                }
            } catch (InitializationException e) {
                DefaultTasksManager.this.logger.error("Failed to initialize the tasks consumer thread.", e);
            }
        }

        private void consume()
        {
            TaskData task = null;
            try {
                task = DefaultTasksManager.this.queue.take();
                task.increaseAttempts();
                if (task.isStop()) {
                    this.halt = true;
                } else if (!task.isDeprecated()) {
                    DefaultTasksManager.this.componentManager.get()
                        .<TaskConsumer>getInstance(TaskConsumer.class, task.getKind())
                        .consume(task.getWikiId(), task.getDocName(), task.getVersion(),
                            task.getAuthor());
                    DefaultTasksManager.this.tasksStore.get().deleteTask(task.getWikiId(), convert(task));
                }
            } catch (Exception e) {
                DefaultTasksManager.this.logger.warn("Error during the execution of task [{}]. Cause: [{}].", task,
                    getRootCauseMessage(e));
                if (task != null) {
                    if (!task.tooManyAttempts()) {
                        // Push back the failed task at the beginning of the queue by resetting its timestamp.
                        task.setTimestamp(System.currentTimeMillis());
                        DefaultTasksManager.this.queue.put(task);
                    } else {
                        logger.error("[{}] abandoned because it has failed to many times.", task);
                    }
                }
            }
        }

        private void initQueue() throws InitializationException
        {
            try {
                // Load the tasks for all wikis.
                for (String wikiId : DefaultTasksManager.this.wikiDescriptorManager.getAllIds()) {
                    loadWiki(wikiId);
                }
            } catch (WikiManagerException e) {
                throw new InitializationException("Failed to list the wiki IDs.", e);
            }
        }
    }

    private TaskData convert(String wikiId, XWikiTask task)
    {
        TaskData taskData = new TaskData();
        taskData.setTimestamp(task.getTimestamp().getTime());
        taskData.setVersion(task.getId().getVersion());
        taskData.setDocName(task.getId().getDocName());
        taskData.setKind(task.getId().getKind());
        taskData.setWikiId(wikiId);
        taskData.setAuthor(task.getAuthor());
        return taskData;
    }

    private XWikiTask convert(TaskData taskData)
    {
        XWikiTask xWikiTask = new XWikiTask();
        XWikiTaskId id = new XWikiTaskId();
        id.setDocName(taskData.getDocName());
        id.setKind(taskData.getKind());
        id.setVersion(taskData.getVersion());
        id.setInstanceId(this.remoteObservationManagerConfiguration.getId());
        xWikiTask.setId(id);
        xWikiTask.setAuthor(taskData.getAuthor());
        xWikiTask.setTimestamp(new Date(taskData.getTimestamp()));
        return xWikiTask;
    }
}
