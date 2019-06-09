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

import net.alliedmods.stocks.FastCsvParser;
import net.alliedmods.stocks.IThread;
import net.alliedmods.stocks.UrlBuilder;
import net.alliedmods.stocks.Utilities;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class IexCloud implements IQuoteService {
    private final static String TAG = "IexCloud";
    private final static String BASE_URL = "https://cloud.iexapis.com/v1/";
    private final static String SYMBOL_CACHE_PATH = "iexcloud-symbols.json";

    private File cache_dir_;
    private String api_key_;
    private FastCsvParser symbol_cache_ = null;
    private BackgroundRefresh refresh_thread_ = null;

    public IexCloud(File cache_dir, String api_key) {
        cache_dir_ = cache_dir;
        api_key_ = api_key;
    }

    @Override
    public void shutdown() {
        if (refresh_thread_ != null) {
            refresh_thread_.shutdown();
            refresh_thread_ = null;
        }
    }

    @Override
    public List<QuoteResult> query(List<QuoteRequest> requests) throws Exception {
        List<QuoteResult> results = query_impl(requests);
        if (results != null)
            return results;

        // Unfortunately, IEXCloud will abort the whole query if one symbol is not found.
        // If this happens, find which symbols are invalid.
        prefetchSearchData();

        // Re-query with invalid symbols removed.
        if (symbol_cache_ != null) {
            List<QuoteRequest> new_requests = new ArrayList<QuoteRequest>();
            List<QuoteResult> not_found = new ArrayList<QuoteResult>();
            for (QuoteRequest request : requests) {
                if (symbol_cache_.hasItem("symbol", request.symbol)) {
                    new_requests.add(request);
                } else {
                    QuoteResult result = new QuoteResult();
                    result.success = false;
                    result.symbol = request.symbol;
                    result.error = QuoteResult.SYMBOL_NOT_FOUND;
                    not_found.add(result);
                }
            }

            if ((results = query_impl(new_requests)) != null) {
                for (QuoteResult bad : not_found)
                    results.add(bad);
                return results;
            }
        }

        // If that failed, query each symbol one by one.
        results = new ArrayList<QuoteResult>();
        for (QuoteRequest request : requests) {
            List<QuoteRequest> container = new ArrayList<QuoteRequest>();
            container.add(request);

            List<QuoteResult> single_result = query_impl(container);
            if (single_result != null) {
                results.add(single_result.get(0));
            } else {
                QuoteResult result = new QuoteResult();
                result.success = false;
                result.symbol = request.symbol;
                result.error = QuoteResult.SYMBOL_NOT_FOUND;
                results.add(result);
            }
        }
        return results;
    }

    private List<QuoteResult> query_impl(List<QuoteRequest> requests) throws Exception {
        // We don't have to fetch the company name separately here, since the API gives it to us
        // automatically.
        String[] symbols = new String[requests.size()];
        for (int i = 0; i < requests.size(); i++)
            symbols[i] = requests.get(i).symbol;

        UrlBuilder builder = new UrlBuilder(BASE_URL + "stock/market/quote");
        builder.addParam("token", api_key_);
        builder.addParam("symbols", String.join(",", symbols));
        URL url = builder.getUrl();

        JSONArray array = null;
        try {
            array = UrlBuilder.downloadUrlAsJsonArray(url);
        } catch (HttpCodeException exception) {
            if (exception.code() == 404)
                return null;
            throw exception;
        }
        List<QuoteResult> results = new ArrayList<QuoteResult>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            QuoteResult result = new QuoteResult();
            result.success = true;
            result.symbol = obj.getString("symbol");
            result.recentQuote = obj.getString("latestPrice");
            result.prevDayQuote = obj.getString("previousClose");
            result.companyName = obj.getString("companyName");

            long ts = obj.getLong("latestUpdate");
            result.lastTradeDate = QuoteResult.normalizeTimestamp(ts);
            results.add(result);
        }
        return results;
    }

    @Override
    public int getBatchSize() {
        return 10;
    }

    @Override
    public SymbolSuggestion[] suggestSymbols(String prefix) {
        if (symbol_cache_ == null)
            return new SymbolSuggestion[0];

        // :TODO: search by company name as well.
        List<Map<String, String>> results =
                symbol_cache_.findWithPrefix("symbol", prefix, 10);
        SymbolSuggestion[] suggestions = new SymbolSuggestion[results.size()];
        for (int i = 0; i < results.size(); i++) {
            SymbolSuggestion s = new SymbolSuggestion();
            s.companyName = results.get(i).get("name");
            s.symbol = results.get(i).get("symbol");
            suggestions[i] = s;
        }
        return suggestions;
    }

    @Override
    public void prefetchSearchData() throws InterruptedException {
        if (symbol_cache_ != null)
            return;

        File f = new File(SYMBOL_CACHE_PATH);
        if (f.exists()) {
            LocalDate then = Utilities.normalize(new Date(f.lastModified()));
            LocalDate now = LocalDate.now(Utilities.UTC);
            long days = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(now, then));
            if (days >= 14)
                refresh_thread_ = new BackgroundRefresh(this);
        }

        try {
            // Synchronized so we don't try to read and write the file at same time.
            synchronized (this) {
                byte[] data = Files.readAllBytes(getSymbolCachePath());
                String contents = Utilities.fastDecodeUtf8(data);
                symbol_cache_ = new FastCsvParser(contents);
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not load symbol cache", e);
            refreshSearchData();
        }
    }

    private boolean refreshSearchData() throws InterruptedException {
        UrlBuilder builder = new UrlBuilder(BASE_URL + "ref-data/symbols");
        URL url = null;
        try {
            builder.addParam("token", api_key_);
            builder.addParam("format", "csv");
            url = builder.getUrl();
        } catch (Exception e) {
            Log.e(TAG, "Could not build URL", e);
            return false;
        }

        String contents = null;
        try {
            contents = UrlBuilder.downloadUrl(url);
        } catch (Exception e) {
            Log.e(TAG, "Could not download symbols", e);
            return false;
        }

        try {
            synchronized (this) {
                symbol_cache_ = new FastCsvParser(contents);
                Files.write(getSymbolCachePath(), Collections.singleton(contents));
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not save symbol cache", e);
        }
        return true;
    }

    private Path getSymbolCachePath() {
        return Paths.get(cache_dir_.getPath(), SYMBOL_CACHE_PATH);
    }

    private class BackgroundRefresh extends IThread {
        private IexCloud service_;
        BackgroundRefresh(IexCloud service) {
            service_ = service;
        }

        @Override
        protected void processThreadActions() throws InterruptedException {
            service_.refreshSearchData();
        }
    }
}
