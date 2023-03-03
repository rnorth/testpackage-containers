package org.testcontainers.containers;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.TransactionBody;
import org.bson.Document;
import org.junit.Test;
import org.testcontainers.containers.output.OutputFrame.OutputType;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.MongoDBContainer.*;
import static org.testcontainers.utility.DockerImageName.*;

public class MongoDBContainerTest {

    private static final DockerImageName DOCKER_IMAGE_NAME = DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG);

    /**
     * Taken from <a href="https://docs.mongodb.com/manual/core/transactions/">https://docs.mongodb.com</a>
     */
    @Test
    public void shouldExecuteTransactions() {
        try (
            // creatingMongoDBContainer {
            final MongoDBContainer mongoDBContainer = new MongoDBContainer(parse("mongo:" + DEFAULT_TAG))
            // }
        ) {
            // startingMongoDBContainer {
            mongoDBContainer.start();
            // }
            executeTx(mongoDBContainer);
        }
    }

    private void executeTx(MongoDBContainer mongoDBContainer) {
        final String mongoRsUrl = mongoDBContainer.getReplicaSetUrl();
        assertThat(mongoRsUrl).isNotNull();
        final String connectionString = mongoDBContainer.getConnectionString();
        final MongoClient mongoSyncClientBase = MongoClients.create(connectionString);
        final MongoClient mongoSyncClient = MongoClients.create(mongoRsUrl);
        mongoSyncClient
            .getDatabase("mydb1")
            .getCollection("foo")
            .withWriteConcern(WriteConcern.MAJORITY)
            .insertOne(new Document("abc", 0));
        mongoSyncClient
            .getDatabase("mydb2")
            .getCollection("bar")
            .withWriteConcern(WriteConcern.MAJORITY)
            .insertOne(new Document("xyz", 0));
        mongoSyncClientBase
            .getDatabase("mydb3")
            .getCollection("baz")
            .withWriteConcern(WriteConcern.MAJORITY)
            .insertOne(new Document("def", 0));

        final ClientSession clientSession = mongoSyncClient.startSession();
        final TransactionOptions txnOptions = TransactionOptions
            .builder()
            .readPreference(ReadPreference.primary())
            .readConcern(ReadConcern.LOCAL)
            .writeConcern(WriteConcern.MAJORITY)
            .build();

        final String trxResult = "Inserted into collections in different databases";

        TransactionBody<String> txnBody = () -> {
            final MongoCollection<Document> coll1 = mongoSyncClient.getDatabase("mydb1").getCollection("foo");
            final MongoCollection<Document> coll2 = mongoSyncClient.getDatabase("mydb2").getCollection("bar");

            coll1.insertOne(clientSession, new Document("abc", 1));
            coll2.insertOne(clientSession, new Document("xyz", 999));
            return trxResult;
        };

        try {
            final String trxResultActual = clientSession.withTransaction(txnBody, txnOptions);
            assertThat(trxResultActual).isEqualTo(trxResult);
        } catch (RuntimeException re) {
            throw new IllegalStateException(re.getMessage(), re);
        } finally {
            clientSession.close();
            mongoSyncClient.close();
        }
    }
    private void evaluateCommand(MongoDBContainer mongoDBContainer, Document command) {
        try(final MongoClient client = MongoClients.create(mongoDBContainer.getConnectionString())) {
            Document admin = client.getDatabase("admin").runCommand(command);
            System.out.println("admin.toJson() = " + admin.toJson());
        } catch (RuntimeException re) {
            throw new IllegalStateException(re.getMessage(), re);
        }
    }

    @Test
    public void supportsMongoDB_4_4() {
        try (final MongoDBContainer mongoDBContainer = new MongoDBContainer(parse("mongo:4.4"))) {
            mongoDBContainer.start();
        }
    }

    @Test
    public void shouldTestDatabaseName() {
        try (final MongoDBContainer mongoDBContainer = new MongoDBContainer(DOCKER_IMAGE_NAME)) {
            mongoDBContainer.start();
            final String databaseName = "my-db";
            assertThat(mongoDBContainer.getReplicaSetUrl(databaseName)).endsWith(databaseName);
        }
    }

    @Test
    public void testShouldSupportSharding() {
        try (final MongoDBContainer mongoDBContainer = new MongoDBContainer(DOCKER_IMAGE_NAME)
                                                           .withSharding()) {
            mongoDBContainer.start();
            try {
                executeTx(mongoDBContainer);
            } finally {
                System.out.println("logs = " + mongoDBContainer.getLogs(OutputType.values()));
            }
        }
    }

}
