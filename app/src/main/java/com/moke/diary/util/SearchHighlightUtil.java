package com.moke.diary.util;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

import androidx.core.content.ContextCompat;

import com.moke.diary.R;

import java.util.Locale;

/**
 * 在文本中将搜索关键词标记为指定颜色（用于列表搜索结果高亮）。
 */
public final class SearchHighlightUtil {

    private SearchHighlightUtil() {
    }

    /**
     * 将 {@code text} 中与 {@code keyword} 匹配的部分标红，匹配不区分大小写。
     */
    public static CharSequence highlight(Context context, String text, String keyword) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(keyword)) {
            return text != null ? text : "";
        }

        int highlightColor = ContextCompat.getColor(context, R.color.search_match_highlight);
        SpannableString spannable = new SpannableString(text);
        String lowerText = text.toLowerCase(Locale.getDefault());
        String lowerKeyword = keyword.toLowerCase(Locale.getDefault());
        int start = 0;
        while (start < lowerText.length()) {
            int index = lowerText.indexOf(lowerKeyword, start);
            if (index < 0) {
                break;
            }
            int end = index + keyword.length();
            spannable.setSpan(
                    new ForegroundColorSpan(highlightColor),
                    index,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = end;
        }
        return spannable;
    }
}
