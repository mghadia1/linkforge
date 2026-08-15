package com.mghadia.linkforge.link;

public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException(String code) {
        super("no link for code " + code);
    }
}
