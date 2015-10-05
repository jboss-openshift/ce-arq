/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
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

package org.jboss.arquillian.ce.template;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Date;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.CredentialsProvider;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class GitAdapter implements Closeable {

    private final Git git;
    private final File dir;
    private CredentialsProvider credentials;

    private GitAdapter(Git git, File dir) throws IOException {
        this.git = git;
        this.dir = dir;
    }

    public GitAdapter setCredentials(CredentialsProvider credentials) {
        this.credentials = credentials;
        return this;
    }

    public void close() throws IOException {
        if (git != null) {
            git.close();
        }
    }

    public static GitAdapter cloneRepository(File gitDir, String gitRepository) throws Exception {
        CloneCommand clone = Git.cloneRepository();
        clone.setDirectory(gitDir);
        clone.setURI(gitRepository);
        return new GitAdapter(clone.call(), gitDir);
    }

    public static GitAdapter init(File gitDir) throws Exception {
        InitCommand init = Git.init();
        init.setDirectory(gitDir);
        return new GitAdapter(init.call(), gitDir);
    }

    public GitAdapter pull() throws Exception {
        return pull(Constants.DEFAULT_REMOTE_NAME);
    }

    public GitAdapter pull(String remote) throws Exception {
        PullCommand pull = git.pull();
        pull.setRemote(remote);
        pull.call();
        return this;
    }

    public GitAdapter prepare(String path) throws Exception {
        File existing = new File(dir, path);
        if (existing.exists() && existing.delete() == false) {
            throw new IllegalStateException("Cannot delete: " + existing);
        }
        File parent = existing.getParentFile();
        if (parent.exists() == false) {
            if (parent.mkdirs() == false) {
                throw new IllegalStateException("Cannot create dirs: " + parent);
            }
        }
        return this;
    }

    public GitAdapter add(String pattern) throws Exception {
        AddCommand add = git.add();
        add.addFilepattern(pattern);
        add.call();
        return this;
    }

    public GitAdapter commit() throws Exception {
        CommitCommand commit = git.commit();
        commit.setMessage(String.format("Testing ... %s", new Date()));
        commit.call();
        return this;
    }

    public GitAdapter push() throws Exception {
        return push(Constants.DEFAULT_REMOTE_NAME);
    }

    public GitAdapter push(String remote) throws Exception {
        PushCommand push = git.push();
        push.setRemote(remote);
        push.setCredentialsProvider(credentials);
        push.call();
        return this;
    }

}
