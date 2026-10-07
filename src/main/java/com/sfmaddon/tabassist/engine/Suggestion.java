package com.sfmaddon.tabassist.engine;

public record Suggestion(
        String ghostText,
        int replaceCharsBefore
) {
    public static Suggestion append(String ghostText) {
        return new Suggestion(ghostText, 0);
    }

    public static Suggestion replace(String ghostText, int replaceCharsBefore) {
        return new Suggestion(ghostText, replaceCharsBefore);
    }
}
