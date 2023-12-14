package io.github.onecx.k8s.db.postgresql.operator;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "onecx.k8s.db.postgresql.operator")
public interface DatabaseConfig {

    @WithName("host")
    @WithDefault("postgresql")
    String host();
}
