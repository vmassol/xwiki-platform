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

import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.suigeneris.jrcs.rcs.Version;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.index.TaskConsumer;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.observation.remote.RemoteObservationManagerConfiguration;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.DocumentRevisionProvider;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.doc.tasks.XWikiTask;
import com.xpn.xwiki.doc.tasks.XWikiTaskId;

import ch.qos.logback.classic.Level;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test of {@link DefaultTasksManager}.
 *
 * @version $Id$
 * @since 14.1RC1
 */
@ComponentTest
class DefaultTasksManagerTest
{
    public static final DocumentReference DOCUMENT_REFERENCE_WIKIID_42 =
        new DocumentReference("xwiki", "Space", "Page42");

    public static final DocumentReference DOCUMENT_REFERENCE_WIKIID_43 =
        new DocumentReference("xwiki", "Space", "Page43");

    public static final String INSTANCE_ID = "instance-id";

    @InjectMockComponents
    private DefaultTasksManager tasksManager;

    @MockComponent
    private WikiDescriptorManager wikiDescriptorManager;

    @MockComponent
    private Provider<TasksStore> tasksStoreProvider;

    @MockComponent
    private Provider<ComponentManager> componentManagerProvider;

    @MockComponent
    private RemoteObservationManagerConfiguration remoteObservationManagerConfiguration;

    @MockComponent
    private Logger logger;

    @MockComponent
    private Provider<XWikiContext> contextProvider;

    @MockComponent
    private DocumentRevisionProvider documentRevisionProvider;

    @InjectComponentManager
    private ComponentManager componentManager;

    /**
     * Mock component for the "testtask" tasks.
     */
    @MockComponent
    @Named("testtask")
    private TaskConsumer testTaskConsumer;

    /**
     * Mock component for the "othertask" tasks.
     */
    @MockComponent
    @Named("othertask")
    private TaskConsumer otherTaskConsumer;

    @Mock
    private TasksStore tasksStore;

    @Mock
    private XWikiContext context;

    @RegisterExtension
    LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @BeforeEach
    void setUp() throws Exception
    {
        when(this.tasksStoreProvider.get()).thenReturn(this.tasksStore);
        when(this.remoteObservationManagerConfiguration.getId()).thenReturn(INSTANCE_ID);
        when(this.contextProvider.get()).thenReturn(this.context);
        when(this.componentManagerProvider.get()).thenReturn(this.componentManager);

        XWikiDocument documentWikiId42 = mock(XWikiDocument.class);
        when(this.tasksStore.getDocument("wikiId", 42)).thenReturn(documentWikiId42);
        when(this.documentRevisionProvider.getRevision(documentWikiId42, "1.3")).thenReturn(documentWikiId42);
        when(documentWikiId42.getDocumentReference()).thenReturn(DOCUMENT_REFERENCE_WIKIID_42);
        when(documentWikiId42.getVersion()).thenReturn("1.3");

        XWikiDocument documentWikiId43 = mock(XWikiDocument.class);
        when(this.tasksStore.getDocument("wikiId", 43)).thenReturn(documentWikiId43);
        when(this.documentRevisionProvider.getRevision(documentWikiId43, "1.3")).thenReturn(documentWikiId43);
        when(documentWikiId43.getDocumentReference()).thenReturn(DOCUMENT_REFERENCE_WIKIID_43);
        when(documentWikiId43.getVersion()).thenReturn("1.3");
    }

    @Test
    void addTask() throws Exception
    {
        CompletableFuture<TaskData> taskFuture =
            this.tasksManager.addTask("wikiId", 42, "1.3", "testtask");

        XWikiTask task = new XWikiTask();
        XWikiTaskId taskId = new XWikiTaskId();
        taskId.setDocId(42);
        taskId.setKind("testtask");
        taskId.setInstanceId(INSTANCE_ID);
        taskId.setVersion(new Version(1, 3));
        task.setId(taskId);
        verify(this.tasksStore).addTask("wikiId", task);
        assertEquals(1, this.tasksManager.getQueueSize());
        assertEquals(1, this.tasksManager.getQueueSize("testtask"));

        // Start consuming the task.
        this.tasksManager.startThread();
        TaskData taskData = new TaskData();
        taskData.setDocId(42);
        taskData.setKind("testtask");
        taskData.setWikiId("wikiId");
        taskData.setVersion(new Version(1, 3));
        assertEquals(taskData, taskFuture.get());
        verify(this.testTaskConsumer).consume(DOCUMENT_REFERENCE_WIKIID_42, "1.3");
    }

    @Test
    void addTaskFailsOnce() throws Exception
    {
        CompletableFuture<TaskData> taskFuture =
            this.tasksManager.addTask("wikiId", 42, "1.3", "testtask");

        XWikiTask task = new XWikiTask();
        XWikiTaskId taskId = new XWikiTaskId();
        taskId.setDocId(42);
        taskId.setKind("testtask");
        taskId.setInstanceId(INSTANCE_ID);
        taskId.setVersion(new Version(1, 3));
        task.setId(taskId);
        verify(this.tasksStore).addTask("wikiId", task);
        assertEquals(1, this.tasksManager.getQueueSize());
        assertEquals(1, this.tasksManager.getQueueSize("testtask"));

        // Fails the first time, then succeeds the second this it's called.
        doThrow(new RuntimeException("Test")).doNothing().when(this.testTaskConsumer).consume(any(), any());

        // Start consuming the task.
        this.tasksManager.startThread();
        TaskData taskData = new TaskData();
        taskData.setDocId(42);
        taskData.setKind("testtask");
        taskData.setWikiId("wikiId");
        taskData.setVersion(new Version(1, 3));
        assertEquals(taskData, taskFuture.get());
        verify(this.testTaskConsumer, times(2)).consume(DOCUMENT_REFERENCE_WIKIID_42, "1.3");

        assertEquals(1, this.logCapture.size());
        assertThat(this.logCapture.getMessage(0),
            matchesPattern("^Error during the execution of task \\[.+]. Cause: \\[RuntimeException: Test]\\.$"));
        assertEquals(Level.WARN, this.logCapture.getLogEvent(0).getLevel());
    }

    @Test
    void addTaskDatabaseIssue() throws Exception
    {
        XWikiTask task = new XWikiTask();
        XWikiTaskId taskId = new XWikiTaskId();
        taskId.setDocId(42);
        taskId.setKind("testtask");
        taskId.setInstanceId(INSTANCE_ID);
        taskId.setVersion(new Version(1, 3));
        task.setId(taskId);

        doThrow(new XWikiException()).when(this.tasksStore).addTask("wikiId", task);

        CompletableFuture<TaskData> taskFuture =
            this.tasksManager.addTask("wikiId", 42, "1.3", "testtask");

        assertEquals(1, this.tasksManager.getQueueSize());
        assertEquals(1, this.tasksManager.getQueueSize("testtask"));

        // Start consuming the task.
        this.tasksManager.startThread();
        TaskData taskData = new TaskData();
        taskData.setDocId(42);
        taskData.setKind("testtask");
        taskData.setWikiId("wikiId");
        taskData.setVersion(new Version(1, 3));
        assertEquals(taskData, taskFuture.get());
        verify(this.testTaskConsumer).consume(DOCUMENT_REFERENCE_WIKIID_42, "1.3");

        assertEquals(1, this.logCapture.size());
        assertEquals("Failed to add a task for docId [42], kind [testtask] and version [1.3] in wiki [wikiId]."
            + " This task is queued but will not be will not be restarted if not completed before the server stops."
            + " Cause: [XWikiException: Error number 0 in 0].", this.logCapture.getMessage(0));
        assertEquals(Level.WARN, this.logCapture.getLogEvent(0).getLevel());
    }

    @Test
    void replaceTask() throws Exception
    {
        // Queue three tasks, then replace the first one with a new on, then start consuming them. Only two Tasks must
        // be consumed in the end.
        // Sleeps 1 millisecond between each new task to be able to assert the execution order of the tasks without
        // timestamp collision issues.
        CompletableFuture<TaskData> future0 = this.tasksManager.replaceTask("wikiId", 42, "1.2", "testtask");
        Thread.sleep(1);
        CompletableFuture<TaskData> future1 = this.tasksManager.replaceTask("wikiId", 42, "1.3", "othertask");
        Thread.sleep(1);
        CompletableFuture<TaskData> future2 = this.tasksManager.replaceTask("wikiId", 43, "1.3", "testtask");
        Thread.sleep(1);
        CompletableFuture<TaskData> future3 = this.tasksManager.replaceTask("wikiId", 42, "1.3", "testtask");

        verify(this.tasksStore, times(4)).replaceTask(any(), any());
        assertEquals(3, this.tasksManager.getQueueSize());
        assertEquals(2, this.tasksManager.getQueueSize("testtask"));
        assertEquals(1, this.tasksManager.getQueueSize("othertask"));

        // Start consuming the task.
        this.tasksManager.startThread();
        // Cancelled since it has been replaced by the latest task.
        assertThrows(CancellationException.class, future0::get);
        assertNotNull(future1.get());
        assertNotNull(future2.get());
        assertNotNull(future3.get());

        InOrder inOrder = inOrder(this.testTaskConsumer, this.otherTaskConsumer);
        inOrder.verify(this.otherTaskConsumer).consume(DOCUMENT_REFERENCE_WIKIID_42, "1.3");
        inOrder.verify(this.testTaskConsumer).consume(DOCUMENT_REFERENCE_WIKIID_43, "1.3");
        inOrder.verify(this.testTaskConsumer).consume(DOCUMENT_REFERENCE_WIKIID_42, "1.3");
    }

    @Test
    void replaceTaskDatabaseIssue() throws Exception
    {
        doThrow(new XWikiException()).when(this.tasksStore).replaceTask(any(), any());

        // Queue three tasks, then replace the first one with a new on, then start consuming them. Only two Tasks must
        // be consumed in the end.
        CompletableFuture<TaskData> future = this.tasksManager.replaceTask("wikiId", 42, "1.3", "testtask");

        // Start consuming the task.
        this.tasksManager.startThread();
        // Cancelled since it has been replaced by the latest task.
        assertNotNull(future.get());

        verify(this.testTaskConsumer).consume(DOCUMENT_REFERENCE_WIKIID_42, "1.3");

        assertEquals(1, this.logCapture.size());
        assertEquals("Failed to persist task with docId [42], kind [testtask] and version [1.3] in wiki"
            + " [wikiId]. The tasks are replaced but will not be restarted if not completed before the server stops."
            + " Cause: [XWikiException: Error number 0 in 0].", this.logCapture.getMessage(0));
        assertEquals(Level.WARN, this.logCapture.getLogEvent(0).getLevel());
    }

    @Test
    void initQueueFromDatabase() throws Exception
    {
        when(this.wikiDescriptorManager.getAllIds()).thenReturn(List.of("wikiId", "wikiB"));
        XWikiTask xWikiTask = new XWikiTask();
        xWikiTask.setTimestamp(new Date());
        XWikiTaskId id = new XWikiTaskId();
        id.setVersion(new Version(1, 3));
        id.setKind("testtask");
        id.setDocId(42);
        xWikiTask.setId(id);
        when(this.tasksStore.getAllTasks("wikiId", INSTANCE_ID)).thenReturn(List.of(xWikiTask));

        this.tasksManager.startThread();

        // Queue a new task to have something to wait for Waits 1ms to make sure that the task is with a timestamps 
        // higher than the tasks from the database.
        Thread.sleep(1);
        CompletableFuture<TaskData> future = this.tasksManager.addTask("wikiId", 42, "1.3", "othertask");

        // Wait for the new task to be consumed to make sure that all the initialization process is completed.
        assertNotNull(future.get());
        verify(this.tasksStore).getAllTasks("wikiId", INSTANCE_ID);
        verify(this.tasksStore).getAllTasks("wikiB", INSTANCE_ID);
        verify(this.testTaskConsumer).consume(DOCUMENT_REFERENCE_WIKIID_42, "1.3");
        verify(this.otherTaskConsumer).consume(DOCUMENT_REFERENCE_WIKIID_42, "1.3");
    }
}
