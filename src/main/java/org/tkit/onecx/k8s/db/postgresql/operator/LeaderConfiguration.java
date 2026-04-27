package org.tkit.onecx.k8s.db.postgresql.operator;

import jakarta.inject.Singleton;

import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;

@Singleton
public class LeaderConfiguration extends LeaderElectionConfiguration {

    @SuppressWarnings("java:S5738")
    public LeaderConfiguration(DatabaseConfig config) {
        super(config.leaderElectionConfig().leaseName());
    }
}
