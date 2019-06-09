// vim: set sts=4 sw=4 tw=99 et:
//
// Copyright (C) 2019 AlliedModders LLC
// Copyright (C) 2019 David Anderson
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
package net.alliedmods.stocks;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.nfc.tech.IsoDep;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Utilities {
    public static ZoneId UTC = ZoneId.of("UTC").normalized();

    private static String TAG = "Utilities";
    private static DecimalFormat PriceFormat;
    private static DecimalFormat PercentFormat;
    private static Map<Integer, Drawable> PillBoxCache = new HashMap<Integer, Drawable>();
    private static DateTimeFormatter DateFormat;
    private static DateTimeFormatter IsoDateFormat;

    public static String formatPrice(BigDecimal price) {
        if (PriceFormat == null) {
            DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(Locale.getDefault());
            PriceFormat = new DecimalFormat("#0.00", dfs);
        }
        return PriceFormat.format(price);
    }

    public static String formatPercent(BigDecimal change) {
        if (PercentFormat == null) {
            DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(Locale.getDefault());
            PercentFormat = new DecimalFormat("#0.00", dfs);
        }
        return PercentFormat.format(change) + "%";
    }

    public static Drawable getPillBox(Context cx, int color) {
        if (PillBoxCache.containsKey(color))
            return PillBoxCache.get(color);

        Drawable shape = ContextCompat.getDrawable(cx, R.drawable.pill_box);
        shape.mutate();
        shape.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
        PillBoxCache.put(color, shape);
        return shape;
    }

    public static LocalDate parseYearMonthDay(String s) {
        if (DateFormat == null)
            DateFormat = DateTimeFormatter.ofPattern("yyyy-M-d");
        return LocalDate.parse(s, DateFormat);
    }

    public static String toYearMonthDay(LocalDate d) {
        return String.format("%d-%02d-%02d", d.getYear(), d.getMonthValue(), d.getDayOfMonth());
    }

    public static LocalDate normalize(Date d) {
        return d.toInstant().atZone(UTC).toLocalDate();
    }

    public static int findString(String[] list, String item) {
        for (int i = 0; i < list.length; i++) {
            if (list[i].equals(item))
                return i;
        }
        return -1;
    }

    public static<T> T[] slice(T[] array, int start, int end) {
        return Arrays.copyOfRange(array, start, end);
    }

    public static String[] removeString(String[] list, String item) {
        int symbol_pos = Utilities.findString(list, item);
        if (symbol_pos != -1) {
            int pos = symbol_pos;
            for (int i = symbol_pos + 1; i < list.length; i++)
                list[pos++] = list[i];
        }
        return slice(list, 0, list.length - 1);
    }

    public static LocalDate parseIsoDateTime(String s) throws ParseException {
        return LocalDateTime.parse(s, getIsoDateFormatter()).toLocalDate();
    }

    private static DateTimeFormatter getIsoDateFormatter() {
        if (IsoDateFormat == null) {
            IsoDateFormat = new DateTimeFormatterBuilder()
                    .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .optionalStart().appendOffset("+HH:MM", "+00:00").optionalEnd()
                    .optionalStart().appendOffset("+HHMM", "+0000").optionalEnd()
                    .optionalStart().appendOffset("+HH", "Z").optionalEnd()
                    .toFormatter();
        }
        return IsoDateFormat;
    }

    public static ZoneId getZone(String short_name, String long_name) {
        if (ZoneId.getAvailableZoneIds().contains(long_name))
            return ZoneId.of(long_name);
        if (ZoneId.SHORT_IDS.containsKey(short_name))
            return ZoneId.of(ZoneId.SHORT_IDS.get(short_name));
        return null;
    }

    // This is a fast and dirty utf-8 decoder since String(byte[]) is incredibly slow, and blocks
    // the IexCloud reference data download for almost 20 seconds.
    public native static String fastDecodeUtf8(byte[] buffer);

    // Same; this replaces the very slow String.split() since the IexCloud reference data is so
    // large.
    public native static String[] fastSplitLines(String text);
}
