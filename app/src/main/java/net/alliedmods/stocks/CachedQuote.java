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

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CachedQuote {
    public String symbol;
    public String cacheDate;
    public String lastTradeDate;
    public String prevDayQuote;
    public String recentQuote;
    public String companyName;

    public CachedQuote(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getPrice() {
        if (recentQuote == null)
            return null;
        return new BigDecimal(recentQuote);
    }
    public BigDecimal getPrevPrice() {
        if (prevDayQuote == null)
            return null;
        return new BigDecimal(prevDayQuote);
    }

    public static String getCompanyName(CachedQuote quote) {
        if (quote == null)
            return null;
        return quote.companyName;
    }

    public static BigDecimal getPrice(CachedQuote quote) {
        if (quote == null)
            return null;
        return quote.getPrice();
    }

    public static BigDecimal getPriceChange(CachedQuote quote) {
        if (quote == null || quote.getPrice() == null || quote.getPrevPrice() == null)
            return null;
        return quote.getPrice().subtract(quote.getPrevPrice());
    }

    public static BigDecimal getPercentChange(CachedQuote quote) {
        BigDecimal change = getPriceChange(quote);
        if (change == null)
            return null;
        change = change.divide(quote.getPrevPrice(), 4, RoundingMode.HALF_UP);
        return change.multiply(new BigDecimal(100));
    }
}
