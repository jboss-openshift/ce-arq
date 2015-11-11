# Cloud Enablement Arquillian Support

# EAP

To run tests against EAP, one **must** set these system properties / env vars (use your numbers / paths!):

KUBERNETES_MASTER=https://172.28.128.4:8443

When using Docker you **must** set this

DOCKER_URL=http://172.28.128.4:2375

And **one way** of authentication, either username/password:

-Dopenshift.username=[OpenShift username], default is "admin"

-Dopenshift.password=[OpenShift password], default is "admin"

Or auth token:

-Dkubernetes.auth.token=[OpenShift OAuth token; oc whoami -t]

Additional properties you **can** set / change.

-Dfrom.name=[Docker parent / from image name]

-Ddeployment.dir=[EAP deployment directory], default is "/opt/eap/standalone/deployments/"

-Dkubernetes.ignore.cleanup=(true|false) -- do we leave the test image, pod and services still running after the test is finished, default is "false"

-Dcontainer.mgmt.port=[EAP container management port], default is 9990

-Ddocker.username=[OpenShift username]

-Ddocker.password=[OpenShift login token]

-Ddocker.test.image=[Docker test image name], default is "cetestimage"

-Ddocker.test.tag=[Docker test image tag], default is "latest"

-Ddocker.test.pull.policy=[Docker test image pull policy], default is "Always"

-Dkubernetes.namespace=[K8s/OpenShift namespace], is none is specified, one is generated

-Dkubernetes.trust.certs=[Trust server certificates], default is "true"

-Dkubernetes.registry.namespace=[K8s/OpenShift docker-registry namespace], default is "default"

-Dkubernetes.registry.service.name=[K8s/OpenShift docker-registry service name], default is "docker-registry"

-Darquillian.startup.timeout=[boot timeout in seconds], default is 60sec

-Dkubernetes.api.version=[K8s API version], default is "v1"

-Dkubernetes.container.pre-stop-hook-type=[Pre-stop hook type], default is "HTTP_GET";

-Dkubernetes.container.pre-stop=[Pre-stop path], default is "/pre-stop/_hook"

-Dkubernetes.container.pre-stop-ignore=(true|false), default is "false"
 
-Ddocker.email=[Email]
 
-Ddocker.address=[Address]
