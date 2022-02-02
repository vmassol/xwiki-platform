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

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.hibernate.Session;
import org.xwiki.component.annotation.Component;
import org.xwiki.context.ExecutionContext;
import org.xwiki.context.ExecutionContextManager;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.WikiReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.tasks.XWikiTask;
import com.xpn.xwiki.doc.tasks.XWikiTaskId;
import com.xpn.xwiki.store.XWikiHibernateBaseStore;

/**
 * Provide the operations to interact with the tasks store.
 *
 * @version $Id$
 * @since 14.1RC1
 */
@Component(roles = TasksStore.class)
@Singleton
public class TasksStore extends XWikiHibernateBaseStore
{
    @Inject
    private ExecutionContextManager contextManager;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private DocumentReferenceResolver<String> documentReferenceResolver;

    /**
     * Retrieve the list of all the tasks queued for a given wiki.
     *
     * @param wikiId the wiki in which to execute the query
     * @return the list of all the task
     * @throws XWikiException in case of error when creating or executing the query
     */
    public List<XWikiTask> getAllTasks(String wikiId) throws XWikiException
    {
        return initWikiContext(xWikiContext -> (List<XWikiTask>) executeRead(xWikiContext,
            session -> session.createQuery("SELECT t FROM XWikiTask t").getResultList()), null, wikiId);
    }

    /**
     * Persist a task to the queue.
     *
     * @param wikiId the wiki in which to execute the query
     * @param task the task to persist
     * @throws XWikiException in case of error when saving the task
     */
    public void addTask(String wikiId, XWikiTask task) throws XWikiException
    {
        initWikiContext(xWikiContext -> {
            executeWrite(xWikiContext, session -> {
                innerAddTask(task, session);
                return null;
            });
            return null;
        }, task.getAuthor(), wikiId);
    }

    /**
     * Remove a task from the queue.
     *
     * @param wikiId the wiki in which to execute the query
     * @param task the task to remove
     * @throws XWikiException in case of error when removing the task
     */
    public void deleteTask(String wikiId, XWikiTask task) throws XWikiException
    {
        initWikiContext(xWikiContext -> {
            executeWrite(xWikiContext, session -> {
                XWikiTaskId taskId = task.getId();
                session.createQuery("delete from XWikiTask t where t.id.docName = :docName "
                        + "and t.id.versionMajor = :versionMajor "
                        + "and t.id.versionMinor = :versionMinor "
                        + "and t.id.kind = :kind")
                    .setParameter("docName", taskId.getDocName())
                    .setParameter("versionMajor", taskId.getVersionMajor())
                    .setParameter("versionMinor", taskId.getVersionMinor())
                    .setParameter("kind", taskId.getKind())
                    .executeUpdate();
                return null;
            });
            return null;
        }, task.getAuthor(), wikiId);
    }

    /**
     * Remove all tasks of the same kind and document, regardless of tehe version, then add the new task to the queue.
     *
     * @param wikiId the wiki in which to execute the query
     * @param task the task replacing the previously queued tasks for the same document and the same kind
     * @throws XWikiException in case of error when removing or adding the tasks
     */
    public void replaceTask(String wikiId, XWikiTask task) throws XWikiException
    {
        initWikiContext(xWikiContext -> {
            executeWrite(xWikiContext, session -> {
                XWikiTaskId taskId = task.getId();
                session.createQuery("delete from XWikiTask t where t.id.docName = :docName "
                        + "and t.id.kind = :kind")
                    .setParameter("docName", taskId.getDocName())
                    .setParameter("kind", taskId.getKind())
                    .executeUpdate();
                innerAddTask(task, session);
                return null;
            });
            return null;
        }, task.getAuthor(), wikiId);
    }

    private <T> T initWikiContext(Lambda<T> r, String author, String wikiId) throws XWikiException
    {
        try {
            ExecutionContext context = new ExecutionContext();
            this.contextManager.initialize(context);

            XWikiContext xWikiContext = this.xcontextProvider.get();
            if (author != null) {
                xWikiContext.setUserReference(this.documentReferenceResolver.resolve(author));
            }
            xWikiContext.setWikiReference(new WikiReference(wikiId));
            xWikiContext.setWikiId(wikiId);
            return r.call(xWikiContext);
        } catch (Exception e) {
            throw new XWikiException("Failed to executed the task in the context", e);
        }
    }

    private void innerAddTask(XWikiTask task, Session session)
    {
        if (task.getTimestamp() == null) {
            task.setTimestamp(new Date());
        }
        
        // Update allowed in case the same document is queued again with the same version and the same task on restart.
        session.saveOrUpdate(task);
    }

    /**
     * Internal functional interface with no return value and the possibility to throw exceptions.
     */
    @FunctionalInterface
    private interface Lambda<T>
    {
        T call(XWikiContext context) throws Exception;
    }
}
