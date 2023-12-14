package io.github.onecx.k8s.db.postgresql.operator;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration(onAddFilter = DatabaseController.AddFilter.class, onUpdateFilter = DatabaseController.UpdateFilter.class)
public class DatabaseController implements Reconciler<Database>, ErrorStatusHandler<Database>,
        EventSourceInitializer<Database> {

    private static final Logger log = LoggerFactory.getLogger(DatabaseController.class);

    @Inject
    DatabaseConfig config;

    @Inject
    DatabaseService databaseService;

    @Override
    public Map<String, EventSource> prepareEventSources(EventSourceContext<Database> context) {
        final SecondaryToPrimaryMapper<Secret> webappsMatchingTomcatName = (Secret t) -> context.getPrimaryCache()
                .list(db -> {
                    if (db.getSpec() != null) {
                        return db.getSpec().getPasswordSecrets().equals(t.getMetadata().getName());
                    }
                    return false;
                })
                .map(ResourceID::fromResource)
                .collect(Collectors.toSet());

        InformerConfiguration<Secret> configuration = InformerConfiguration.from(Secret.class, context)
                .withSecondaryToPrimaryMapper(webappsMatchingTomcatName)
                .withPrimaryToSecondaryMapper(
                        (Database primary) -> Set.of(new ResourceID(primary.getSpec().getPasswordSecrets(),
                                primary.getMetadata().getNamespace())))
                .build();
        return EventSourceInitializer
                .nameEventSources(new InformerEventSource<>(configuration, context));
    }

    @Override
    public UpdateControl<Database> reconcile(Database database, Context<Database> context)
            throws Exception {

        if (!config.host().equals(database.getSpec().getHost())) {
            return UpdateControl.noUpdate();
        }

        Optional<Secret> secret = context.getSecondaryResource(Secret.class);
        if (secret.isPresent()) {

            String name = database.getMetadata().getName();
            String namespace = database.getMetadata().getNamespace();

            log.info("Reconcile postgresql database: {} namespace: {}", name, namespace);
            byte[] password = createRequestData(database.getSpec(), secret.get());
            databaseService.update(database.getSpec(), password);

            updateStatusPojo(database);
            log.info("Database '{}' reconciled - updating status", database.getMetadata().getName());
            return UpdateControl.updateStatus(database);
        }
        return UpdateControl.noUpdate();
    }

    private static byte[] createRequestData(DatabaseSpec spec, Secret secret) throws MissingMandatoryKeyException {
        Map<String, String> data = secret.getData();

        String key = spec.getPasswordKey();
        if (key == null) {
            throw new MissingMandatoryKeyException("Secret key is mandatory. No key found!");
        }
        if (!data.containsKey(key)) {
            throw new MissingMandatoryKeyException("Secret key is mandatory. No key secret found!");
        }
        String value = data.get(key);
        if (value.isEmpty()) {
            throw new MissingMandatoryKeyException("Secret key '" + key + "' is mandatory. No value found!");
        }
        return Base64.getDecoder().decode(value);
    }

    public static class MissingMandatoryKeyException extends Exception {

        public MissingMandatoryKeyException(String msg) {
            super(msg);
        }
    }

    @Override
    public ErrorStatusUpdateControl<Database> updateErrorStatus(Database resource,
            Context<Database> context, Exception e) {

        var message = e.getMessage();
        if (e.getCause() instanceof MissingMandatoryKeyException me) {
            message = me.getMessage();
        }

        log.error("Error reconcile resource", e);
        DatabaseStatus status = new DatabaseStatus();
        status.setUrl(null);
        status.setUser(null);
        status.setPasswordSecrets(null);
        status.setStatus(DatabaseStatus.Status.ERROR);
        status.setMessage(message);
        resource.setStatus(status);
        return ErrorStatusUpdateControl.updateStatus(resource);
    }

    private void updateStatusPojo(Database database) {
        DatabaseStatus status = new DatabaseStatus();
        DatabaseSpec spec = database.getSpec();
        status.setUrl(spec.getName());
        status.setUser(spec.getUser());
        status.setPasswordSecrets(spec.getPasswordSecrets());
        status.setStatus(DatabaseStatus.Status.CREATED);
        status.setMessage(null);
        database.setStatus(status);
    }

    public static class AddFilter implements OnAddFilter<Database> {

        @Override
        public boolean accept(Database resource) {
            return resource.getSpec() != null;
        }
    }

    public static class UpdateFilter implements OnUpdateFilter<Database> {

        @Override
        public boolean accept(Database newResource, Database oldResource) {
            return newResource.getSpec() != null;
        }
    }
}
