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
class ParallelHandle {
    private static final Logger log = Logger.getLogger(ParallelHandle.class.getName());

    private enum State {
        DONE,
        IN_PROGRESS,
        WAITING,
        ERROR
    }

    private volatile State state;
    private Throwable error;

    Throwable getError() {
        return error;
    }

    synchronized void init() {
        if (state == null) {
            state = State.IN_PROGRESS;
        }
    }

    synchronized void clear() {
        if (state == State.WAITING) {
            notifyAll(); // just to make sure, we don't somehow hang
        }
        state = null;
    }

    synchronized void doNotify(String info) {
        if (state == State.WAITING) {
            log.info(String.format("Notifying builds waiting on %s ...", info));
            notifyAll();
        } else {
            log.info(String.format("Build %s already done [%s].", info, state != null ? state : State.DONE));
        }
        state = State.DONE;
    }

    synchronized void doError(String info, Throwable error) {
        log.info(String.format("Error in %s build: %s", info, error));
        this.error = error;
        if (state == State.WAITING) {
            notifyAll();
        }
        state = State.ERROR;
    }

    synchronized void doWait(String info) {
        if (state == State.IN_PROGRESS) {
            log.info(String.format("Waiting for %s build to finish ...", info));
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
