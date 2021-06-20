package com.tterrag.iticker.util;

import java.util.regex.Pattern;

import com.tterrag.iticker.util.annotation.NonNullFields;

@NonNullFields
public class Patterns {

    public static final Pattern TICKER = Pattern.compile("(?:^|\\b|\\s)(?:\\$(?=[^^])|(?=\\^))(\\^?[A-Z0-9.=\\-]*[A-Z0-9])(\\+|\\b)");
}
