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

package org.jboss.arquillian.ce.utils;

import java.util.logging.Logger;

/**
 * Parallel handle.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class ParallelHandle {
    private static final Logger log = Logger.getLogger(ParallelHandle.class.getName());

    private enum State {
        DONE,
        IN_PROGRESS,
        WAITING
    }

    private volatile State state;

    synchronized void init() {
        state = State.IN_PROGRESS;
    }

    synchronized void doNotify() {
        if (state == State.WAITING) {
            log.info("Notifying dependent waiting builds ...");
            notifyAll();
        }
        state = State.DONE;
    }

    synchronized void doWait() {
        if (state == State.IN_PROGRESS) {
            log.info("Waiting for main build to finish ...");
            state = State.WAITING;
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
