package com.example.tempp.model;

import lombok.ToString;

@ToString
public class User {
    private String identity = "";
    private String token = "";

    public User(String identity, String token) {
        this.identity = identity;
        this.token = token;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
