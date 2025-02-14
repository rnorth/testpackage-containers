# CircleCI (Cloud, Server v3.x, and Server v4.x)

Your CircleCI configuration needs to enable Docker environment for Testcontainers to work. You can achieve this by adding the `setup_remote_docker` step to your `.circleci/config.yml`.

>When you use the remote Docker environment for a job, any Docker commands you run in your job will be executed locally on the virtual machine used to spin up your primary Docker container. The term remote used here is left over from when remote Docker usage would spin up a separate, remote environment for running Docker commands.

See [Run Docker commands](https://circleci.com/docs/building-docker-images/) for more info. 

Here is a sample CircleCI configuration:

```yml
jobs:
  build:
    # Check https://circleci.com/docs/circleci-images/ for more details
    docker:
      - image: cimg/openjdk:17.0
    steps:
      - checkout
      - setup_remote_docker
      - run: mvn -B clean install
```
