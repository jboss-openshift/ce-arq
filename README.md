# Cloud Enablement Arquillian Support

To run things against management-exposed WildFly, I use this confuguration (within IntelliJ):

-Djava.io.tmpdir=$MODULE_DIR$/target

-Dfrom.name=alesj/wildfly

-Ddeployment.dir=/opt/jboss/wildfly/standalone/deployments/

-Dkubernetes.ignore.cleanup=true

-Dcontainer.mgmt.port=9990

Where you also need to set this env variables (use your numbers!):

DOCKER_URL=http://172.28.128.4:2375

KUBERNETES_MASTER=https://172.28.128.4:8443

KUBERNETES_TRUST_CERT=true

