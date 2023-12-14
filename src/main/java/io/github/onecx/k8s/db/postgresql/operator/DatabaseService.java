package io.github.onecx.k8s.db.postgresql.operator;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalConnectionFactoryConfigurationSupplier;
import io.agroal.api.configuration.supplier.AgroalConnectionPoolConfigurationSupplier;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;

/**
 * Database service to access database and execute changes.
 */
@ApplicationScoped
public class DatabaseService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);

    /**
     * SQL to check if user exists
     */
    private static final String SQL_CHECK_USER = "SELECT true FROM pg_user WHERE usename = '%s'";
    /**
     * SQL to update user password
     */
    private static final String SQL_UPDATE_USER = "ALTER USER %s PASSWORD '%s'";
    /**
     * SQL to create a new user
     */
    private static final String SQL_CREATE_USER = "CREATE USER %s WITH ENCRYPTED PASSWORD '%s'";
    /**
     * SQL to update user search path.
     */
    private static final String SQL_USER_SEARCH_PATH = "ALTER USER %s SET SEARCH_PATH TO %s;";
    /**
     * SQL to create user extension.
     */
    private static final String SQL_USER_EXTENSION = "CREATE EXTENSION IF NOT EXISTS \"%s\"";
    /**
     * SQL to check if database exists.
     */
    private static final String SQL_CHECK_DB = "SELECT true FROM pg_catalog.pg_database WHERE datname = '%s'";
    /**
     * SQL to update database for the owner.
     */
    private static final String SQL_UPDATE_DB = "ALTER DATABASE %s OWNER TO %s";
    /**
     * SQL to create a database.
     */
    private static final String SQL_CREATE_DB = "CREATE DATABASE %s OWNER '%s'";
    /**
     * SQL to create a new schema for the user.
     */
    private static final String SQL_CREATE_SCHEMA = "CREATE SCHEMA IF NOT EXISTS %s AUTHORIZATION %s;";
    /**
     * SQL to grant user role to admin.
     */
    private static final String SQL_GRANT_ROLE_TO_ADMIN = "GRANT %s TO %s";
    /**
     * SQL to grant database to user.
     */
    private static final String SQL_GRANT_DB_TO_USER = "GRANT ALL ON DATABASE %s TO %s";

    @Inject
    AgroalDataSource dataSource;

    @ConfigProperty(name = "onecx.operator.db.postgresql.grant-user-role-to-admin", defaultValue = "true")
    boolean grantUserRoleToAdmin;

    @ConfigProperty(name = "quarkus.datasource.username")
    String databaseAdmin;

    public void update(DatabaseSpec spec, byte[] password) throws SQLException {

        try (Connection connection = dataSource.getConnection()) {

            log.info("Open database connection.");

            try (Statement statement = connection.createStatement()) {

                // check user
                boolean userExists = statement.executeQuery(String.format(SQL_CHECK_USER, spec.getUser())).next();
                log.info("Check user '{}' if exists '{}'.", spec.getUser(), userExists);

                // create or update user
                if (userExists) {
                    statement.execute(String.format(SQL_UPDATE_USER, spec.getUser(), new String(password)));
                    log.info("Update existing user '{}'", spec.getUser());
                } else {
                    statement.execute(String.format(SQL_CREATE_USER, spec.getUser(), new String(password)));
                    log.info("Create user '{}'", spec.getUser());
                }

                // check database
                boolean dbExists = statement.executeQuery(String.format(SQL_CHECK_DB, spec.getName())).next();
                log.info("Check database '{}' if exists '{}'", spec.getName(), dbExists);

                // create or update database
                if (dbExists) {
                    statement.execute(String.format(SQL_UPDATE_DB, spec.getName(), spec.getUser()));
                    log.info("Update database '{}'", spec.getName());
                } else {

                    // grant user role to admin
                    if (grantUserRoleToAdmin) {
                        statement.execute(String.format(SQL_GRANT_ROLE_TO_ADMIN, spec.getUser(), databaseAdmin));
                        log.info("Grant user role '{}' to admin '{}'", spec.getUser(), databaseAdmin);
                    }

                    // create database
                    statement.execute(String.format(SQL_CREATE_DB, spec.getName(), spec.getUser()));
                    log.info("Create database '{}'", spec.getName());

                    // grant database to user
                    statement.execute(String.format(SQL_GRANT_DB_TO_USER, spec.getName(), spec.getUser()));
                    log.info("Grant database '{}' to user '{}'", spec.getName(), spec.getUser());
                }

            }
        } finally {
            log.info("Close database connection.");
        }

        try (AgroalDataSource datasource = createUserDatasource(spec, password)) {
            try (Connection connection = datasource.getConnection()) {

                log.info("Open database '{}' user connection.", spec.getName());

                try (Statement statement = connection.createStatement()) {
                    // create schema if not exists
                    if (spec.getSchema() != null && !spec.getSchema().isBlank()) {
                        statement.execute(String.format(SQL_CREATE_SCHEMA, spec.getSchema(), spec.getUser()));
                        log.info("Create schema '{}'", spec.getSchema());
                    }

                    // update user search path
                    if (spec.getUserSearchPath() != null && !spec.getUserSearchPath().isBlank()) {
                        statement.execute(String.format(SQL_USER_SEARCH_PATH, spec.getUser(), spec.getUserSearchPath()));
                        log.info("Update user '{}' search path to '{}'", spec.getUser(),
                                spec.getUserSearchPath());
                    }

                    // create extension if not exists
                    if (spec.getExtensions() != null && !spec.getExtensions().isEmpty()) {
                        for (String extension : spec.getExtensions()) {
                            statement.execute(String.format(SQL_USER_EXTENSION, extension));
                        }
                        log.info("Create extensions '{}'", spec.getExtensions());
                    }
                }
            }
        } finally {
            log.info("Close database '{}' user connection.", spec.getName());
        }
    }

    private AgroalDataSource createUserDatasource(DatabaseSpec spec, byte[] password) throws SQLException {

        AgroalDataSourceConfigurationSupplier dataSourceConfiguration = new AgroalDataSourceConfigurationSupplier();

        dataSourceConfiguration.connectionPoolConfiguration(dataSource.getConfiguration().connectionPoolConfiguration());
        String jdbcUrl = dataSource.getConfiguration().connectionPoolConfiguration().connectionFactoryConfiguration().jdbcUrl();
        jdbcUrl = createJdbcUrl(jdbcUrl, spec.getName());

        AgroalConnectionPoolConfigurationSupplier poolConfiguration = dataSourceConfiguration.connectionPoolConfiguration();
        AgroalConnectionFactoryConfigurationSupplier connectionFactoryConfiguration = poolConfiguration
                .connectionFactoryConfiguration();

        connectionFactoryConfiguration.jdbcUrl(jdbcUrl);
        connectionFactoryConfiguration.credential(new NamePrincipal(spec.getUser()));
        connectionFactoryConfiguration.credential(new SimplePassword(new String(password)));
        return AgroalDataSource.from(dataSourceConfiguration.get());
    }

    static String createJdbcUrl(String jdbcUrl, String database) {
        int startIndex = jdbcUrl.lastIndexOf("/");
        int endIndex = jdbcUrl.lastIndexOf("?");
        String result = jdbcUrl.substring(0, startIndex + 1) + database;
        if (endIndex > -1) {
            result = result + jdbcUrl.substring(endIndex);
        }
        return result;
    }

}
