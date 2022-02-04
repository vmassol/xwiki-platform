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

import java.io.Serializable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.suigeneris.jrcs.rcs.Version;

/**
 * Store the information about a task.
 *
 * @version $Id$
 * @since 14.1RC1
 */
public class TaskData implements Serializable
{
    /**
     * Singleton stop instance.
     */
    public static final TaskData STOP = new TaskData(true);

    private Long timestamp;

    private int version1;

    private int version2;

    private long docId;

    private String kind;

    private int attempts;

    private boolean stopFlag;

    private String wikiId;

    /**
     * Default empty constructor.
     */
    public TaskData()
    {
        // Empty constructor required because a private constructor exists.
    }

    private TaskData(boolean stop)
    {
        this.stopFlag = stop;
        this.timestamp = 0L;
    }

    /**
     * @return the timestamp of the creation of the task
     */
    public long getTimestamp()
    {
        return this.timestamp;
    }

    /**
     * @param timestamp the timestamp of the creation of the task
     * @return the current task
     */
    public TaskData setTimestamp(long timestamp)
    {
        this.timestamp = timestamp;
        return this;
    }

    /**
     * @return the version of the document to analyze
     */
    public Version getVersion()
    {
        return new Version(this.version1, this.version2);
    }

    /**
     * @param version the version of the document to analyze (e.g., 1.2)
     * @return the current task
     */
    public TaskData setVersion(Version version)
    {
        this.version1 = version.at(0);
        this.version2 = version.at(1);
        return this;
    }

    /**
     * @return the id of the document to analyze
     */
    public long getDocId()
    {
        return this.docId;
    }

    /**
     * @param docId the id of the document to analyze
     * @return the current task
     */
    public TaskData setDocId(long docId)
    {
        this.docId = docId;
        return this;
    }

    /**
     * @return the kind of the task to execute
     */
    public String getKind()
    {
        return this.kind;
    }

    /**
     * @param kind the kind of the task to execute
     * @return the current task
     */
    public TaskData setKind(String kind)
    {
        this.kind = kind;
        return this;
    }

    /**
     * Increase the failed attempts counter.
     */
    public void increaseAttempts()
    {
        this.attempts++;
    }

    /**
     * @return {@code true} if the task is a stop task, {@code false} otherwise. The only instance retuning true of the
     *     {@link #STOP} single instance
     */
    public boolean isStop()
    {
        return this.stopFlag;
    }

    /**
     * @return TODO document...
     */
    public boolean isDeprecated()
    {
        return this.stopFlag && this != STOP;
    }

    /**
     * @return the identifier of the wiki where the task must be executed
     */
    public String getWikiId()
    {
        return this.wikiId;
    }

    /**
     * @param wikiId the identifier of the wiki where the task must be executed
     * @return the current task
     */
    public TaskData setWikiId(String wikiId)
    {
        this.wikiId = wikiId;
        return this;
    }

    /**
     * @return {@code true} when to many failed attempts have been made, {@code false} otherwise
     */
    public boolean tooManyAttempts()
    {
        return this.attempts > 10;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TaskData taskData = (TaskData) o;

        return new EqualsBuilder()
            .append(this.timestamp, taskData.timestamp)
            .append(this.version1, taskData.version1)
            .append(this.version2, taskData.version2)
            .append(this.attempts, taskData.attempts)
            .append(this.stopFlag, taskData.stopFlag)
            .append(this.docId, taskData.docId)
            .append(this.kind, taskData.kind)
            .append(this.wikiId, taskData.wikiId)
            .isEquals();
    }

    @Override
    public int hashCode()
    {
        return new HashCodeBuilder(17, 37)
            .append(this.timestamp)
            .append(this.version1)
            .append(this.version2)
            .append(this.docId)
            .append(this.kind)
            .append(this.attempts)
            .append(this.stopFlag)
            .append(this.wikiId)
            .toHashCode();
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this)
            .append("timestamp", this.timestamp)
            .append("docId", this.docId)
            .append("kind", this.kind)
            .append("attempts", this.attempts)
            .append("stop", this.stopFlag)
            .append("wikiId", this.wikiId)
            .append("version", getVersion())
            .toString();
    }
}
