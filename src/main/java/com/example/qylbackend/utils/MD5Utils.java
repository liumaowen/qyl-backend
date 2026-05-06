package com.example.qylbackend.utils;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
 
public class MD5Utils {
    /**
     * 使用md5的算法进行加密
     */
    public static String md5(String plainText) {
        byte[] secretBytes = null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            secretBytes = md.digest(plainText.getBytes());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("没有md5这个算法！");
        }

        StringBuilder md5code = new StringBuilder();
        for (byte b : secretBytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                md5code.append('0');
            }
            md5code.append(hex);
        }

        return md5code.toString();
    }
}