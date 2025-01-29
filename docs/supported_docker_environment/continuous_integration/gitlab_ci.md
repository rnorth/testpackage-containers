# GitLab CI

## Example using Docker socket
This applies if you have your own GitlabCI runner installed, use the Docker executor and you have `/var/run/docker.sock` mounted in the runner configuration.

See below for an example runner configuration: 
```toml
[[runners]]
  name = "MACHINE_NAME"
  url = "https://gitlab.com/"
  token = "GENERATED_GITLAB_RUNNER_TOKEN"
  executor = "docker"
  [runners.docker]
    tls_verify = false
    image = "docker:latest"
    privileged = false
    disable_entrypoint_overwrite = false
    oom_kill_disable = false
    disable_cache = false
    volumes = ["/var/run/docker.sock:/var/run/docker.sock", "/cache"]
    shm_size = 0
```

Please also include the following in your GitlabCI pipeline definitions (`.gitlab-ci.yml`) that use Testcontainers:
```yaml
variables:
  TESTCONTAINERS_HOST_OVERRIDE: "<ip-docker-host>"
```

The environment variable `TESTCONTAINERS_HOST_OVERRIDE` needs to be configured, otherwise, a wrong IP address would be used to resolve the Docker host, which will likely lead to failing tests. For Windows and MacOS, use `host.docker.internal`.

## Example using DinD (Docker-in-Docker)

In order to use Testcontainers in a Gitlab CI pipeline, you need to run the job as a Docker container (see [Patterns for running inside Docker](dind_patterns.md)).
So edit your `.gitlab-ci.yml` to include the [Docker-In-Docker service](https://docs.gitlab.com/ee/ci/docker/using_docker_build.html#use-docker-in-docker-workflow-with-docker-executor) (`docker:dind`) and set the `DOCKER_HOST` variable to `tcp://docker:2375` and `DOCKER_TLS_CERTDIR` to empty string. 

Caveat: Current docker releases (verified for 20.10.9) intentionally delay the startup, if the docker api is bound to a network address but not TLS protected. To avoid this delay, the docker process needs to be started with the argument `--tls=false`.  Otherwise jobs which access the docker api at the very beginning might fail.

Here is a sample `.gitlab-ci.yml` that executes test with gradle:

```yaml
# DinD service is required for Testcontainers
services:
  - name: docker:dind
    # explicitly disable tls to avoid docker startup interruption
    command: ["--tls=false"]

variables:
  # Instruct Testcontainers to use the daemon of DinD, use port 2375 for non-tls connections.
  DOCKER_HOST: "tcp://docker:2375"
  # Instruct Docker not to start over TLS.
  DOCKER_TLS_CERTDIR: ""
  # Improve performance with overlayfs.
  DOCKER_DRIVER: overlay2

test:
 image: gradle:5.0
 stage: test
 script: ./gradlew test
```

## Example using Kubedock

This applies if your executor is `kubernetes` and you don't want to use DinD. One option is to use [kubedock](https://github.com/joyrex2001/kubedock). This library is a minimal implementation of the Docker API that will orchestrate containers on a Kubernetes cluster.

Here is the example Kubernetes configuration you must create:

```yaml
# ServiceAccount for Kubedock
apiVersion: v1
kind: ServiceAccount
metadata:
  name: kubedock
  namespace: gitlab-runner

# Role for Kubedock
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: kubedock-role
  namespace: gitlab-runner
rules:
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["create", "get", "list", "delete", "watch"]
  - apiGroups: [""]
    resources: ["pods/log"]
    verbs: ["list", "get"]
  - apiGroups: [""]
    resources: ["pods/exec"]
    verbs: ["create"]
  - apiGroups: [""]
    resources: ["services"]
    verbs: ["create", "get", "list", "delete"]
  - apiGroups: [""]
    resources: ["configmaps"]
    verbs: ["create", "get", "list", "delete"]

# RoleBinding for Kubedock
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: kubedock-rolebinding
  namespace: gitlab-runner
subjects:
  - kind: User
    name: system:serviceaccount:gitlab-runner:kubedock
    apiGroup: rbac.authorization.k8s.io
roleRef:
  kind: Role
  name: kubedock-role
  apiGroup: rbac.authorization.k8s.io

# Deployment for Kubedock Server
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kubedock-server
  namespace: gitlab-runner
spec:
  replicas: 1
  selector:
    matchLabels:
      app: kubedock-server
  template:
    metadata:
      labels:
        app: kubedock-server
    spec:
      serviceAccountName: kubedock
      containers:
        - name: kubedock-server
          image: joyrex2001/kubedock:0.17.0
          resources:
            limits:
              memory: "4Gi"
              cpu: "1000m"
            requests:
              memory: "2Gi"
              cpu: "200m"
          ports:
            - containerPort: 2475
          args: [
              # Configuration options described here:
              # https://github.com/joyrex2001/kubedock/blob/master/config.md
              "server",
              "--namespace=gitlab-runner",
              "--service-account=kubedock",
              "--timeout=20m0s",
              "--request-cpu=1",
              "--request-memory=2Gi",
              "--disable-dind",
              "--reverse-proxy",
              "--reapmax=60m",
            ]

# Service for Kubedock
apiVersion: v1
kind: Service
metadata:
  name: kubedock-service
  namespace: gitlab-runner
spec:
  selector:
    app: kubedock-server
  type: ClusterIP
  clusterIP: None
```


Here is a sample `.gitlab-ci.yml` that executes go test:

```yaml
variables:
  # Instruct Testcontainers to use the daemon of kubedock to create containers in kubernetes
  DOCKER_HOST: "tcp://kubedock-service:2475"
test:
  image: golang:1.22
  stage: test
  script: go test ./... -v
```
