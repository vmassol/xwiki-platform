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
    private String docName;

    private int versionMinor;

    private int versionMajor;

    private String kind;

    /**
     * @return the document name to be processed
     */
    public String getDocName()
    {
        return this.docName;
    }

    /**
     * @param docName the document nname to be processed (e.g., XWiki.Space.Page)
     */
    public void setDocName(String docName)
    {
        this.docName = docName;
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

    @Override
    public String toString()
    {
        return new ToStringBuilder(this)
            .append("docName", this.docName)
            .append("kind", this.kind)
            .append("version", getVersion())
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
            .append(this.docName, that.docName)
            .append(this.kind, that.kind)
            .isEquals();
    }

    @Override
    public int hashCode()
    {
        return new HashCodeBuilder(17, 37)
            .append(this.docName)
            .append(this.versionMinor)
            .append(this.versionMajor)
            .append(this.kind)
            .toHashCode();
    }
}
