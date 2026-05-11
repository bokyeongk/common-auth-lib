package com.hubilon.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DuplicateCheckResponse {
    @JsonProperty("exists")
    private final boolean exists;

    public DuplicateCheckResponse(boolean exists) { this.exists = exists; }
    public boolean isExists() { return exists; }
}
