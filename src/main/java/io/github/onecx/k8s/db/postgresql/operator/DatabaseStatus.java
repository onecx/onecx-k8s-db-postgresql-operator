package io.github.onecx.k8s.db.postgresql.operator;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.javaoperatorsdk.operator.api.ObservedGenerationAwareStatus;

public class DatabaseStatus extends ObservedGenerationAwareStatus {

    @JsonProperty("url")
    private String url;

    @JsonProperty("status")
    private Status status;

    @JsonProperty("message")
    private String message;

    @JsonProperty("user")
    private String user;

    @JsonProperty("password-secrets")
    private String passwordSecrets;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPasswordSecrets() {
        return passwordSecrets;
    }

    public void setPasswordSecrets(String passwordSecrets) {
        this.passwordSecrets = passwordSecrets;
    }

    public enum Status {

        ERROR,

        CREATED,

        UPDATED,

        UNDEFINED;
    }
}
