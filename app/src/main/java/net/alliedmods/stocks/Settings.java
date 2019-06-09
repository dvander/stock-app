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
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import net.alliedmods.quotes.AlphaVantage;
import net.alliedmods.quotes.IQuoteService;
import net.alliedmods.quotes.IexCloud;
import net.alliedmods.quotes.Intrinio;
import net.alliedmods.quotes.WorldTradingData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Settings {
    static final private String TAG = "Settings";
    private Context cx_;
    private Map<String, CachedQuote> quote_cache_ = new HashMap<String, CachedQuote>();
    private Properties quote_props_ = new Properties();

    public Settings(Context cx) {
        cx_ = cx;

        loadCachedQuotes();
    }

    public Map<String, CachedQuote> getCachedQuotes() {
        return quote_cache_;
    }

    public IQuoteService createQuoteService() {
        String quote_api = getApiServiceName();
        return createQuoteService(quote_api);
    }

    private IQuoteService createQuoteService(String quote_api) {
        if (quote_api.equals("alphavantage")) {
            String api_key = getSharedPref("alphavantage_api_key", "");
            if (api_key.isEmpty())
                return null;
            return new AlphaVantage(api_key);
        }
        if (quote_api.equals("iexcloud")) {
            String api_key = getSharedPref("iexcloud_api_key", "");
            if (api_key.isEmpty())
                return null;
            return new IexCloud(getCacheDir(), api_key);
        }
        if (quote_api.equals("intrinio")) {
            String api_key = getSharedPref("intrinio_api_key", "");
            if (api_key.isEmpty())
                return null;
            return new Intrinio(api_key);
        }
        if (quote_api.equals("worldtradingdata")) {
            String api_key = getSharedPref("wtd_api_key", "");
            if (api_key.isEmpty())
                return null;
            return new WorldTradingData(api_key);
        }

        Log.e(TAG, "Quote service not recognized: " + quote_api);
        return null;
    }

    public String getApiServiceName() {
        return getSharedPref("api_kind", "");
    }

    public String[] getStockSymbols() {
        if (!quote_props_.containsKey("symbols")) {
            String[] defaults = getDefaultSymbols();
            quote_props_.setProperty("symbols", String.join(",", defaults));
        }
        String symbols = quote_props_.getProperty("symbols");
        if (symbols.length() == 0)
            return new String[]{};
        return symbols.split(",");
    }

    public void loadCachedQuotes() {
        File file = getLocalFile("cached_quotes");
        try (FileInputStream fs = new FileInputStream(file)) {
            quote_props_.load(fs);
        } catch (FileNotFoundException e) {
        } catch (IOException e) {
            Log.e(TAG, "Could not load properties file: " + e.getMessage());
        }

        String symbols = quote_props_.getProperty("symbols", null);
        if (symbols == null)
            return;
        String[] symbol_list = symbols.split(",");
        for (String symbol : symbol_list) {
            String prefix = symbol.replace("=", "_");

            CachedQuote quote = new CachedQuote(symbol);
            quote.companyName = quote_props_.getProperty(prefix + ".companyName", null);
            quote.cacheDate = quote_props_.getProperty(prefix + ".cacheDate", null);
            quote.prevDayQuote = quote_props_.getProperty(prefix + ".prevDayQuote", null);
            quote.recentQuote = quote_props_.getProperty(prefix + ".recentQuote", null);
            quote.lastTradeDate = quote_props_.getProperty(prefix + ".lastTradeDate", null);
            quote_cache_.put(symbol, quote);
        }
    }

    public void addSymbol(String raw_symbol, String company_name) {
        String symbol = raw_symbol.toUpperCase();
        List<String> symbols = new ArrayList<String>(Arrays.asList(getStockSymbols()));
        if (symbols.contains(symbol))
            return;
        symbols.add(symbol);

        String mangled = mangleSymbol(raw_symbol);

        String new_list = String.join(",", symbols);
        synchronized (quote_props_) {
            quote_props_.setProperty("symbols", new_list);
            if (company_name != null)
                setProperty(quote_props_, mangled + ".companyName", company_name);
        }
        writeCachedQuotes();
        loadCachedQuotes();
    }

    public void removeSymbol(String symbol) {
        quote_cache_.remove(symbol);

        String[] symbols = getStockSymbols();
        String[] new_symbols = Utilities.removeString(symbols, symbol);
        String new_list = String.join(",", new_symbols);

        String prefix = mangleSymbol(symbol);
        List<String> to_remove = new ArrayList<String>();
        synchronized (quote_props_) {
            for (String key : quote_props_.stringPropertyNames()) {
                if (key.startsWith(prefix + "."))
                    to_remove.add(key);
            }
            for (String key : to_remove)
                quote_props_.remove(key);
            quote_props_.setProperty("symbols", new_list);
        }
        writeCachedQuotes();
    }

    public void reorderSymbols(String symbol1, String symbol2) {
        String[] symbols = getStockSymbols();

        int index1 = Utilities.findString(symbols, symbol1);
        int index2 = Utilities.findString(symbols, symbol2);
        symbols[index1] = symbol2;
        symbols[index2] = symbol1;

        String new_list = String.join(",", symbols);
        synchronized (quote_props_) {
            quote_props_.setProperty("symbols", new_list);
        }
        writeCachedQuotes();
    }

    public void saveCachedQuote(CachedQuote quote) {
        quote_cache_.put(quote.symbol, quote);

        String prefix = mangleSymbol(quote.symbol);
        synchronized (quote_props_) {
            setProperty(quote_props_, prefix + ".companyName", quote.companyName);
            setOrDeleteProperty(quote_props_, prefix + ".cacheDate", quote.cacheDate);
            setOrDeleteProperty(quote_props_, prefix + ".prevDayQuote", quote.prevDayQuote);
            setOrDeleteProperty(quote_props_, prefix + ".recentQuote", quote.recentQuote);
            setOrDeleteProperty(quote_props_, prefix + ".lastTradeDate", quote.lastTradeDate);
        }

        // This is not terribly efficient, but whatever for now.
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                writeCachedQuotes();
            }
        });
        thread.start();
    }

    private String mangleSymbol(String symbol) {
        String s = symbol;
        s = s.replace("=", "_");
        s = s.replace(".", "#");
        return s;
    }

    private void setOrDeleteProperty(Properties props, String key, String value) {
        if (value != null)
            props.setProperty(key, value);
        else
            props.remove(key);
    }

    private void setProperty(Properties props, String key, String value) {
        if (value != null)
            props.setProperty(key, value);
    }

    private void writeCachedQuotes() {
        File file = getLocalFile("cached_quotes");
        try (FileOutputStream fs = new FileOutputStream(file)) {
            synchronized (quote_props_) {
                quote_props_.store(fs, "");
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not write properties: " + e.getMessage());
        }
    }

    private String[] getDefaultSymbols() {
        return new String[]{
                "AMZN",
                "GOOG",
                "FB",
                "MSFT",
                "AAPL",
        };
    }

    public File getCacheDir() {
        return cx_.getFilesDir();
    }

    private File getLocalFile(String file) {
        return Paths.get(cx_.getFilesDir().getPath(), file).toFile();
    }

    private String getSharedPref(String key, String defaultValue) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(cx_);
        return prefs.getString(key, defaultValue);
    }
}
