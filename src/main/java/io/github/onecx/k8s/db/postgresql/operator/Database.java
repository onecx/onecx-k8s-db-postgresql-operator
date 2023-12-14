package io.github.onecx.k8s.db.postgresql.operator;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("onecx.github.io")
@Version("v1")
public class Database extends CustomResource<DatabaseSpec, DatabaseStatus> implements Namespaced {
}