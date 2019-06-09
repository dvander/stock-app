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
package net.alliedmods.quotes;

import net.alliedmods.stocks.UrlBuilder;
import net.alliedmods.stocks.Utilities;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class WorldTradingData implements IQuoteService
{
    private static final String TAG = "WorldTradingData";
    private static final String BASE_URL = "https://api.worldtradingdata.com/api/v1/";

    private String api_key_;
    private DateTimeFormatter datetime_format_;

    public WorldTradingData(String api_key) {
        api_key_ = api_key;
        datetime_format_ = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }

    @Override
    public void shutdown() {
    }

    @Override
    public List<QuoteResult> query(List<QuoteRequest> requests) throws Exception {
        List<QuoteResult> results = new ArrayList<QuoteResult>();

        List<String> symbols = new ArrayList<String>();
        for (QuoteRequest request : requests)
            symbols.add(request.symbol);

        UrlBuilder builder = new UrlBuilder(BASE_URL + "stock");
        builder.addParam("api_token", api_key_);
        builder.addParam("symbol", String.join(",", symbols));
        URL url = builder.getUrl();

        JSONObject object = UrlBuilder.downloadUrlAsJson(url);
        if (!object.has("data") && object.has("Message"))
            throw new Exception(object.getString("message"));

        JSONArray list = object.getJSONArray("data");
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.getJSONObject(i);
            QuoteResult result = new QuoteResult();
            result.success = true;
            result.prevDayQuote = item.getString("close_yesterday");
            result.recentQuote = item.getString("price");
            result.companyName = item.getString("name");
            result.symbol = item.getString("symbol");

            LocalDate date = getLastTradeDate(item);
            result.lastTradeDate = Utilities.toYearMonthDay(date);

            results.add(result);
        }
        return results;
    }

    private LocalDate getLastTradeDate(JSONObject object) throws Exception {
        String last_trade_date = object.getString("last_trade_time");
        LocalDateTime dt = LocalDateTime.parse(last_trade_date, datetime_format_);
        ZoneId zone = Utilities.getZone(
                object.getString("timezone"),
                object.getString("timezone_name"));
        if (zone == null)
            return dt.toLocalDate();
        ZonedDateTime zdt = dt.atZone(zone);
        ZonedDateTime utc = zdt.withZoneSameInstant(Utilities.UTC);
        return utc.toLocalDate();
    }

    @Override
    public int getBatchSize() {
        // This is the limit of the free API.
        return 5;
    }

    @Override
    public void prefetchSearchData() throws InterruptedException {
    }

    @Override
    public SymbolSuggestion[] suggestSymbols(String prefix) {
        try {
            UrlBuilder builder = new UrlBuilder(BASE_URL + "stock_search");
            builder.addParam("api_token", api_key_);
            builder.addParam("search_term", prefix);
            builder.addParam("search_by", "symbol");
            builder.addParam("limit", "10");
            URL url = builder.getUrl();

            JSONObject object = UrlBuilder.downloadUrlAsJson(url);
            if (!object.has("data") && object.has("Message"))
                throw new Exception(object.getString("message"));

            JSONArray data = object.getJSONArray("data");
            SymbolSuggestion[] suggestions = new SymbolSuggestion[data.length()];
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                SymbolSuggestion suggestion = new SymbolSuggestion();
                suggestion.symbol = item.getString("symbol");
                suggestion.companyName = item.getString("name");
                suggestions[i] = suggestion;
            }
            return suggestions;
        } catch (Exception e) {
            return new SymbolSuggestion[0];
        }
    }
}
