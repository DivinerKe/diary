package com.moke.diary.model;

public enum Mood {
    HAPPY("开心", "😊"),
    SAD("难过", "😢"),
    CALM("平静", "😌"),
    EXCITED("兴奋", "🤩"),
    ANGRY("愤怒", "😠"),
    NEUTRAL("一般", "😐");

    public final String label;
    public final String emoji;

    Mood(String label, String emoji) {
        this.label = label;
        this.emoji = emoji;
    }

    public String display() {
        return emoji + " " + label;
    }

    public static Mood fromName(String name) {
        if (name == null || name.isEmpty()) {
            return NEUTRAL;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return NEUTRAL;
        }
    }
}
