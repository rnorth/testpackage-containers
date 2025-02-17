# Azure Module

!!! note
This module is INCUBATING. While it is ready for use and operational in the current version of Testcontainers, it is possible that it may receive breaking changes in the future. See [our contributing guidelines](/contributing/#incubating-modules) for more information on our incubating modules policy.

Testcontainers module for the Microsoft Azure's [SDK](https://github.com/Azure/azure-sdk-for-java).

Currently, the module supports `Azurite`, `Azure Event Hubs`, `Azure Service Bus` and `CosmosDB` emulators. In order to use them, you should use the following classes:

Class | Container Image
-|-
AzuriteContainer | [mcr.microsoft.com/azure-storage/azurite](https://github.com/microsoft/containerregistry)
AzureEventHubsContainer | [mcr.microsoft.com/azure-messaging/eventhubs-emulator](https://github.com/microsoft/containerregistry)
AzureServiceBusEmulatorContainer | [mcr.microsoft.com/azure-messaging/servicebus-emulator](https://github.com/microsoft/containerregistry)
AzureServiceBusContainer | [mcr.microsoft.com/azure-messaging/servicebus-emulator](https://github.com/microsoft/containerregistry)
CosmosDBEmulatorContainer | [mcr.microsoft.com/cosmosdb/linux/azure-cosmos-emulator](https://github.com/microsoft/containerregistry)

## Usage example

### Azurite Storage Emulator

Start Azurite Emulator during a test:

<!--codeinclude-->
[Starting an Azurite container](../../modules/azure/src/test/java/org/testcontainers/azure/AzuriteContainerTest.java) inside_block:emulatorContainer
<!--/codeinclude-->

!!! note
    SSL configuration is possible using the `withSsl(MountableFile, String)` and  `withSsl(MountableFile, MountableFile)` methods.

If the tested application needs to use more than one set of credentials, the container can be configured to use custom credentials.
Please see some examples below.

<!--codeinclude-->
[Starting an Azurite Blob container with one account and two keys](../../modules/azure/src/test/java/org/testcontainers/azure/AzuriteContainerTest.java) inside_block:withTwoAccountKeys
<!--/codeinclude-->

<!--codeinclude-->
[Starting an Azurite Blob container with more accounts and keys](../../modules/azure/src/test/java/org/testcontainers/azure/AzuriteContainerTest.java) inside_block:withMoreAccounts
<!--/codeinclude-->

#### Using with Blob

Build Azure Blob client:

<!--codeinclude-->
[Build Azure Blob Service client](../../modules/azure/src/test/java/org/testcontainers/azure/AzuriteContainerTest.java) inside_block:createBlobClient
<!--/codeinclude-->

In case the application needs to use custom credentials, we can obtain them with a different method:

<!--codeinclude-->
[Obtain connection string with non-default credentials](../../modules/azure/src/test/java/org/testcontainers/azure/AzuriteContainerTest.java) inside_block:useNonDefaultCredentials
<!--/codeinclude-->

#### Using with Queue

Build Azure Queue client:

<!--codeinclude-->
[Build Azure Queue Service client](../../modules/azure/src/test/java/org/testcontainers/azure/AzuriteContainerTest.java) inside_block:createQueueClient
<!--/codeinclude-->

!!! note
    We can use custom credentials the same way as defined in the Blob section.

#### Using with Table

Build Azure Table client:

<!--codeinclude-->
[Build Azure Table Service client](../../modules/azure/src/test/java/org/testcontainers/azure/AzuriteContainerTest.java) inside_block:createTableClient
<!--/codeinclude-->

!!! note
    We can use custom credentials the same way as defined in the Blob section.

### Azure Event Hubs Emulator

<!--codeinclude-->
[Configuring the Azure Event Hubs Emulator container](../../modules/azure/src/test/resources/eventhubs_config.json)
<!--/codeinclude-->

Start Azure Event Hubs Emulator during a test:

<!--codeinclude-->
[Setting up a network](../../modules/azure/src/test/java/org/testcontainers/azure/AzureEventHubsContainerTest.java) inside_block:network
<!--/codeinclude-->

<!--codeinclude-->
[Starting an Azurite container as dependency](../../modules/azure/src/test/java/org/testcontainers/azure/AzureEventHubsContainerTest.java) inside_block:azuriteContainer
<!--/codeinclude-->

<!--codeinclude-->
[Starting an Azure Event Hubs Emulator container](../../modules/azure/src/test/java/org/testcontainers/azure/AzureEventHubsContainerTest.java) inside_block:emulatorContainer
<!--/codeinclude-->

#### Using Azure Event Hubs clients

Configure the consumer and the producer clients:

<!--codeinclude-->
[Configuring the clients](../../modules/azure/src/test/java/org/testcontainers/azure/AzureEventHubsContainerTest.java) inside_block:createProducerAndConsumer
<!--/codeinclude-->

### Azure Service Bus Emulator

<!--codeinclude-->
[Configuring the Azure Service Bus Emulator container](../../modules/azure/src/test/resources/service-bus-config.json)
<!--/codeinclude-->

Start Azure Service Bus Emulator during a test:

<!--codeinclude-->
[Setting up a network](../../modules/azure/src/test/java/org/testcontainers/azure/AzureServiceBusContainerTest.java) inside_block:network
<!--/codeinclude-->

<!--codeinclude-->
[Starting a SQL Server container as dependency](../../modules/azure/src/test/java/org/testcontainers/azure/AzureServiceBusContainerTest.java) inside_block:sqlContainer
<!--/codeinclude-->

<!--codeinclude-->
[Starting a Service Bus Emulator container](../../modules/azure/src/test/java/org/testcontainers/azure/AzureServiceBusContainerTest.java) inside_block:emulatorContainer
<!--/codeinclude-->

#### Using Azure Service Bus clients

Configure the sender and the processor clients:

<!--codeinclude-->
[Configuring the sender client](../../modules/azure/src/test/java/org/testcontainers/azure/AzureServiceBusContainerTest.java) inside_block:senderClient
<!--/codeinclude-->

<!--codeinclude-->
[Configuring the processor client](../../modules/azure/src/test/java/org/testcontainers/azure/AzureServiceBusContainerTest.java) inside_block:processorClient
<!--/codeinclude-->

### CosmosDB

Start Azure CosmosDB Emulator during a test:

<!--codeinclude-->
[Starting an Azure CosmosDB Emulator container](../../modules/azure/src/test/java/org/testcontainers/containers/CosmosDBEmulatorContainerTest.java) inside_block:emulatorContainer
<!--/codeinclude-->

Prepare KeyStore to use for SSL.

<!--codeinclude-->
[Building KeyStore from certificate within container](../../modules/azure/src/test/java/org/testcontainers/containers/CosmosDBEmulatorContainerTest.java) inside_block:buildAndSaveNewKeyStore
<!--/codeinclude-->

Set system trust-store parameters to use already built KeyStore:

<!--codeinclude-->
[Set system trust-store parameters](../../modules/azure/src/test/java/org/testcontainers/containers/CosmosDBEmulatorContainerTest.java) inside_block:setSystemTrustStoreParameters
<!--/codeinclude-->

Build Azure CosmosDB client:

<!--codeinclude-->
[Build Azure CosmosDB client](../../modules/azure/src/test/java/org/testcontainers/containers/CosmosDBEmulatorContainerTest.java) inside_block:buildClient
<!--/codeinclude-->

Test against the Emulator:

<!--codeinclude-->
[Testing against Azure CosmosDB Emulator container](../../modules/azure/src/test/java/org/testcontainers/containers/CosmosDBEmulatorContainerTest.java) inside_block:testWithClientAgainstEmulatorContainer
<!--/codeinclude-->

## Adding this module to your project dependencies

Add the following dependency to your `pom.xml`/`build.gradle` file:

=== "Gradle"
    ```groovy
    testImplementation "org.testcontainers:azure:{{latest_version}}"
    ```

=== "Maven"
    ```xml
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>azure</artifactId>
        <version>{{latest_version}}</version>
        <scope>test</scope>
    </dependency>
    ```

