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
package org.xwiki.mentions.internal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.suigeneris.jrcs.rcs.Version;
import org.xwiki.component.annotation.Component;
import org.xwiki.context.Execution;
import org.xwiki.index.IndexException;
import org.xwiki.index.TaskConsumer;
import org.xwiki.mentions.events.NewMentionsEvent;
import org.xwiki.mentions.internal.analyzer.CreatedDocumentMentionsAnalyzer;
import org.xwiki.mentions.internal.analyzer.UpdatedDocumentMentionsAnalyzer;
import org.xwiki.mentions.notifications.MentionNotificationParameter;
import org.xwiki.mentions.notifications.MentionNotificationParameters;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.observation.ObservationManager;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.DocumentRevisionProvider;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.LargeStringProperty;

/**
 * Implementation of {@link TaskConsumer} for the mentions tasks.
 * <p>
 * This class is responsible to analyze document updates in order to identify new user mentions. {@link
 * NewMentionsEvent} are then sent for each newly introduced user mentions. This analysis is done by identifying
 * mentions macro with new identifiers in the content of entities of the updated document. In other word, in the
 * document body as well as in the values of {@link LargeStringProperty} of the xObject objects attached to the
 * document.
 *
 * @version $Id$
 * @since 12.6
 */
@Component
@Singleton
@Named("mention")
public class DefaultMentionsDataConsumer implements TaskConsumer
{
    @Inject
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    private Execution execution;

    @Inject
    private DocumentRevisionProvider documentRevisionProvider;

    @Inject
    private ObservationManager observationManager;

    @Inject
    private CreatedDocumentMentionsAnalyzer createdDocumentMentionsAnalyzer;

    @Inject
    private UpdatedDocumentMentionsAnalyzer updatedDocumentMentionsAnalyzer;

    @Override
    public void consume(String wikiId, String docName, Version version, String authorReference) throws IndexException
    {
        try {
            DocumentReference dr = this.documentReferenceResolver.resolve(docName);
            XWikiDocument doc = this.documentRevisionProvider.getRevision(dr, version.toString());
            if (doc != null) {
                // Stores the list of mentions found in the document and its attached objects.
                List<MentionNotificationParameters> mentionNotificationParameters;
                DocumentReference documentReference = doc.getDocumentReference();

                if (doc.getPreviousVersion() == null) {
                    // CREATE
                    mentionNotificationParameters = this.createdDocumentMentionsAnalyzer
                        .analyze(doc, documentReference, version.toString(), authorReference);
                } else {
                    // UPDATE
                    XWikiDocument oldDoc = this.documentRevisionProvider.getRevision(dr, doc.getPreviousVersion());
                    mentionNotificationParameters = this.updatedDocumentMentionsAnalyzer
                        .analyze(oldDoc, doc, documentReference, version.toString(), authorReference);
                }
                sendNotification(mentionNotificationParameters);
            }
        } catch (XWikiException e) {
            throw new IndexException("Failed during the mention task execution", e);
        } finally {
            this.execution.removeContext();
        }
    }

    private void sendNotification(List<MentionNotificationParameters> notificationParametersList)
    {
        // Notify the listeners of the exhaustive list of identified mentions.
        // The listeners are in charge of selecting the ones of interest for them.
        for (MentionNotificationParameters mentionNotificationParameters : notificationParametersList) {
            Map<String, Set<MentionNotificationParameter>> newMentions = mentionNotificationParameters.getNewMentions();
            if (!newMentions.isEmpty()) {
                this.observationManager
                    .notify(new NewMentionsEvent(), mentionNotificationParameters.getAuthorReference(),
                        mentionNotificationParameters);
            }
        }
    }
}
