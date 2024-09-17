package org.tkit.onecx.k8s.db.postgresql.operator;

import io.quarkus.runtime.annotations.ConfigDocFilename;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigDocFilename("onecx-k8s-db-postgresql-operator.adoc")
@ConfigMapping(prefix = "onecx.k8s.db.postgresql.operator")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface DatabaseConfig {

    /**
     * Server host configuration.
     */
    @WithName("host")
    @WithDefault("postgresql")
    String host();
}
