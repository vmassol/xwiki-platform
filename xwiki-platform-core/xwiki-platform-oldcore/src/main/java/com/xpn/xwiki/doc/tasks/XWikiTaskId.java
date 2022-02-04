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
package com.xpn.xwiki.doc.tasks;

import java.io.Serializable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.suigeneris.jrcs.rcs.Version;

/**
 * Hold the information about the compound id of a queued task.
 *
 * @version $Id$
 * @see XWikiTask
 * @since 14.1RC1
 */
public class XWikiTaskId implements Serializable
{
    private long docId;

    private int versionMinor;

    private int versionMajor;

    private String kind;

    private String instanceId;

    /**
     * @return the document id
     */
    public long getDocId()
    {
        return this.docId;
    }

    /**
     * @param docId the id of the document to be processed 
     */
    public void setDocId(long docId)
    {
        this.docId = docId;
    }

    /**
     * @return the major version of the document (e.g., 1 in 1.2)
     */
    public int getVersionMajor()
    {
        return this.versionMajor;
    }

    /**
     * @param versionMajor the major version of the document (e.g., 1 in 1.2)
     */
    public void setVersionMajor(int versionMajor)
    {
        this.versionMajor = versionMajor;
    }

    /**
     * @return the minor version of the document (e.g., 2 in 1.2)
     */
    public int getVersionMinor()
    {
        return this.versionMinor;
    }

    /**
     * @param versionMinor the minor version of the document (e.g., 2 in 1.2)
     */
    public void setVersionMinor(int versionMinor)
    {
        this.versionMinor = versionMinor;
    }

    /**
     * @return the version to of the document to be processed
     */
    public Version getVersion()
    {
        return new Version(this.versionMajor, this.versionMinor);
    }

    /**
     * @param version the version of the document to be processed
     */
    public void setVersion(Version version)
    {
        this.versionMajor = version.at(0);
        this.versionMinor = version.at(1);
    }

    /**
     * @return the kind of the task to do on the document
     */
    public String getKind()
    {
        return this.kind;
    }

    /**
     * @param kind the kind of the task to do on the document
     */
    public void setKind(String kind)
    {
        this.kind = kind;
    }

    /**
     * @return the identifier of the instance that queued the task
     */
    public String getInstanceId()
    {
        return this.instanceId;
    }

    /**
     * @param instanceId the identifier of the instance that queued the task
     */
    public void setInstanceId(String instanceId)
    {
        this.instanceId = instanceId;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this)
            .append("docId", this.docId)
            .append("kind", this.kind)
            .append("version", getVersion())
            .append("instanceId", getInstanceId())
            .toString();
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

        XWikiTaskId that = (XWikiTaskId) o;

        return new EqualsBuilder()
            .append(this.versionMinor, that.versionMinor)
            .append(this.versionMajor, that.versionMajor)
            .append(this.docId, that.docId)
            .append(this.kind, that.kind)
            .append(this.instanceId, that.instanceId)
            .isEquals();
    }

    @Override
    public int hashCode()
    {
        return new HashCodeBuilder(17, 37)
            .append(this.docId)
            .append(this.versionMinor)
            .append(this.versionMajor)
            .append(this.kind)
            .append(this.instanceId)
            .toHashCode();
    }
}
