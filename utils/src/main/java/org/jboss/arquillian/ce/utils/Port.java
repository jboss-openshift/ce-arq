package org.jboss.arquillian.ce.utils;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class Port {
    private String name;
    private int containerPort;

    public Port() {
    }

    public Port(String name, int containerPort) {
        this.name = name;
        this.containerPort = containerPort;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getContainerPort() {
        return containerPort;
    }

    public void setContainerPort(int containerPort) {
        this.containerPort = containerPort;
    }
}
