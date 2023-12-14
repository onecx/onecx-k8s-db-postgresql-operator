package io.github.onecx.k8s.db.postgresql.operator;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class DatabaseControllerExceptionTest {

    @Inject
    DatabaseController controller;

    @Test
    void updateErrorStatusCustomExceptionTest() {
        Database pd = new Database();
        controller.updateErrorStatus(pd, null, new RuntimeException("Custom error"));

        Assertions.assertNotNull(pd.getStatus());
        DatabaseStatus status = pd.getStatus();
        Assertions.assertEquals(DatabaseStatus.Status.ERROR, status.getStatus());
        Assertions.assertEquals("Custom error", status.getMessage());
    }

}
