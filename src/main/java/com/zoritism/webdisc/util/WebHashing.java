package com.zoritism.webdisc.util;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class WebHashing {

    private WebHashing() {}

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(input.getBytes(StandardCharsets.UTF_8));
            BigInteger num = new BigInteger(1, dig);
            StringBuilder hex = new StringBuilder(num.toString(16));
            while (hex.length() < 64) {
                hex.insert(0, '0');
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }
}