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
package org.xwiki.mentions.internal.listeners;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.suigeneris.jrcs.rcs.Version;
import org.xwiki.bridge.event.DocumentUpdatedEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.index.TaskManager;
import org.xwiki.index.internal.TaskData;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.observation.remote.RemoteObservationManagerContext;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceSerializer;

import com.xpn.xwiki.doc.XWikiDocument;

import static java.util.Collections.singletonList;

/**
 * Listen to entities update.
 *
 * @version $Id$
 * @since 12.5RC1
 */
@Component
@Singleton
@Named("MentionsUpdatedEventListener")
public class MentionsUpdatedEventListener extends AbstractEventListener
{
    private static final List<DocumentUpdatedEvent> EVENTS = singletonList(new DocumentUpdatedEvent());

    @Inject
    private Logger logger;

    @Inject
    private TaskManager executor;

    @Inject
    private RemoteObservationManagerContext remoteObservationManagerContext;

    @Inject
    private EntityReferenceSerializer<String> entityReferenceSerializer;

    @Inject
    private UserReferenceSerializer<String> userReferenceSerializer;

    /**
     * Default constructor.
     */
    public MentionsUpdatedEventListener()
    {
        super("MentionsUpdatedEventListener", EVENTS);
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        if (!(event instanceof DocumentUpdatedEvent) || this.remoteObservationManagerContext.isRemoteState()) {
            return;
        }

        this.logger.debug("Event [{}] received from [{}] with data [{}].",
            DocumentUpdatedEvent.class.getName(), source, data);

        XWikiDocument doc = (XWikiDocument) source;
        UserReference author = doc.getAuthors().getOriginalMetadataAuthor();
        this.executor.addTask(new TaskData().setKind("mention").setVersion(new Version(doc.getVersion()))
                .setAuthor(this.userReferenceSerializer.serialize(author))
                .setDocName(this.entityReferenceSerializer.serialize(doc.getDocumentReference()))
                .setWikiId(doc.getDocumentReference().getWikiReference().getName()),
            doc.getDocumentReference().getWikiReference().getName());
    }
}
