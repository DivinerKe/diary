package com.moke.diary.model;

/**
 * 日记心情枚举，包含中文标签与 emoji 表情。
 * 持久化时使用枚举名，展示时通过 {@link #display()} 组合 emoji 与标签。
 */
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

    /** 返回 emoji 与中文标签的组合字符串，用于 UI 展示。 */
    public String display() {
        return emoji + " " + label;
    }

    /** 从持久化的枚举名解析心情，无效或空值时回退为 {@link #NEUTRAL}。 */
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
