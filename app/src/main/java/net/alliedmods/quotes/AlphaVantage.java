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

import android.util.Log;

import net.alliedmods.stocks.UrlBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class AlphaVantage implements IQuoteService {
    private static String TAG = "AlphaVantage";
    private static String BASE_URL = "https://www.alphavantage.co/query";

    private String api_key_;

    public AlphaVantage(String api_key) {
        api_key_ = api_key;
    }

    @Override
    public void shutdown() {}

    @Override
    public int getBatchSize() {
        return 1;
    }

    @Override
    public SymbolSuggestion[] suggestSymbols(String prefix) {
        try {
            JSONObject json = search(prefix);
            JSONArray array = json.getJSONArray("bestMatches");
            SymbolSuggestion[] suggestions = new SymbolSuggestion[array.length()];
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                SymbolSuggestion suggestion = new SymbolSuggestion();
                suggestion.symbol = item.getString("1. symbol");
                suggestion.companyName = item.getString("2. name");
                suggestions[i] = suggestion;
            }
            return suggestions;
        } catch (Exception e) {
            Log.e(TAG, "Could not search for prefix", e);
        }
        return new SymbolSuggestion[0];
    }

    @Override
    public List<QuoteResult> query(List<QuoteRequest> requests) throws Exception {
        ArrayList<QuoteResult> results = new ArrayList<QuoteResult>();
        for (QuoteRequest request : requests) {
            QuoteResult result;
            try {
                result = query(request.symbol);
            } catch (Exception e) {
                result = new QuoteResult();
                result.success = false;
                result.error = e.getMessage();
            }
            if (result.success && request.fetchName)
                result.companyName = findCompanyName(result.symbol);
            results.add(result);
        }
        return results;
    }

    private QuoteResult query(String symbol) throws Exception {
        QuoteResult result = new QuoteResult();
        result.symbol = symbol;

        UrlBuilder builder = new UrlBuilder(BASE_URL);
        builder.addParam("function", "GLOBAL_QUOTE");
        builder.addParam("symbol", result.symbol);
        builder.addParam("apikey", api_key_);
        URL url = builder.getUrl();
        JSONObject object = UrlBuilder.downloadUrlAsJson(url);
        if (object.has("Information")) {
            result.error = object.getString("Information");
            return result;
        }
        if (object.has("Error Message")) {
            result.error = object.getString("Error Message");
            return result;
        }
        if (!object.has("Global Quote")) {
            if (!object.has("Note"))
                throw new Exception("Unexpected object format");
            result.error = object.getString("Note");
            return result;
        }
        JSONObject quote = object.getJSONObject("Global Quote");
        if (!quote.getString("01. symbol").equals(result.symbol)) {
            result.error = "Fetch returned wrong symbol";
            return result;
        }
        result.recentQuote = quote.getString("05. price");
        result.prevDayQuote = quote.getString("08. previous close");
        result.lastTradeDate = quote.getString("07. latest trading day");
        result.success = true;
        return result;
    }

    private String findCompanyName(String symbol) {
        try {
            JSONObject obj = search(symbol);
            JSONArray array = obj.getJSONArray("bestMatches");
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.getJSONObject(i);
                String test_symbol = entry.getString("1. symbol");
                if (test_symbol.equals(symbol))
                    return entry.getString("2. name");
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private JSONObject search(String keyword) throws Exception {
        UrlBuilder builder = new UrlBuilder(BASE_URL);
        builder.addParam("function", "SYMBOL_SEARCH");
        builder.addParam("keywords", keyword);
        builder.addParam("apikey", api_key_);
        URL url = builder.getUrl();
        JSONObject object = UrlBuilder.downloadUrlAsJson(url);
        if (!object.has("bestMatches")) {
            String error = object.has("Information")
                    ? object.getString("Information")
                    : "Object has unknown format";
            throw new JSONException(error);
        }
        return object;
    }

    @Override
    public void prefetchSearchData() throws InterruptedException {
        // Not supported.
    }
}
