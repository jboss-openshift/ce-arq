# Cloud Enablement Arquillian Support

# EAP

To run tests against EAP, one **must** set these system properties / env vars (use your numbers / paths!):

-Ddocker.username=[OpenShift username]

-Ddocker.password=[OpenShift login token]

DOCKER_URL=http://172.28.128.4:2375

KUBERNETES_MASTER=https://172.28.128.4:8443

KUBERNETES_CERTS_CLIENT_FILE=/Users/alesj/projects/xPaaS/certs/os3/cert.crt

KUBERNETES_CERTS_CLIENT_KEY_FILE=/Users/alesj/projects/xPaaS/certs/os3/key.key

KUBERNETES_CERTS_CA_FILE=/Users/alesj/projects/xPaaS/certs/os3/root.crt

Additional properties you **can** set / change.

-Dfrom.name=[Docker parent / from image name]

-Ddeployment.dir=[EAP deployment directory], default is "/opt/eap/standalone/deployments/"

-Dkubernetes.ignore.cleanup=(true|false) -- do we leave the test image, pod and services still running after the test is finished, default is "false"

-Dcontainer.mgmt.port=[EAP container management port], default is 9990

-Ddocker.test.namespace=[Docker namespace], default is "default"

-Dkubernetes.namespace=[K8s/OpenShift namespace], default is "default"

-Darquillian.startup.timeout=[boot timeout in seconds], default is 60sec
