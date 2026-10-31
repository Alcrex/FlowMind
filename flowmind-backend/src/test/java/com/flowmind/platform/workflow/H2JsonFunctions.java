package com.flowmind.platform.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class H2JsonFunctions {
    private H2JsonFunctions() {}
    public static boolean contains(String array, String candidate) throws Exception {
        if (array == null) return false;
        var json = new ObjectMapper(); var value = json.readTree(candidate);
        for (var member : json.readTree(array)) if (member.equals(value)) return true;
        return false;
    }
}
