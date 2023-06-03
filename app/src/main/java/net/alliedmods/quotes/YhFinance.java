package net.alliedmods.quotes;

import net.alliedmods.stocks.UrlBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class YhFinance implements IQuoteService {
    private String api_key_;

    private final String BASE_URL = "https://yfapi.net";
    private final String QUOTE_ENDPOINT = "/v6/finance/quote";
    private final String AUTOCOMPLETE_ENDPOINT = "/v6/finance/autocomplete";

    public YhFinance(String api_key) {
        super();
        api_key_ = api_key;
    }

    @Override
    public void shutdown() {
    }

    @Override
    public List<QuoteResult> query(List<QuoteRequest> requests) throws Exception {
        UrlBuilder builder = new UrlBuilder(BASE_URL + QUOTE_ENDPOINT);
        builder.addParam("region", "US");
        builder.addParam("lang", "en");

        List<String> symbols = new ArrayList<String>();
        for (QuoteRequest request : requests)
            symbols.add(request.symbol);
        builder.addParam("symbols", String.join(",", symbols));
        URL url = builder.getUrl();
        String text = UrlBuilder.downloadUrl(url, getHeaders());
        JSONObject response = new JSONObject(text);
        JSONObject obj = response.getJSONObject("quoteResponse");
        if (obj.getString("error") != null && !obj.getString("error").equals("null"))
            throw new Exception(obj.getString("error"));

        ArrayList<QuoteResult> results = new ArrayList<>();

        JSONArray array = obj.getJSONArray("result");
        for (int i = 0; i < array.length(); i++) {
            JSONObject qr_obj = array.getJSONObject(i);
            QuoteResult qr = new QuoteResult();
            qr.success = true;
            qr.symbol = qr_obj.getString("symbol");

            long ts = qr_obj.getLong("regularMarketTime");
            qr.lastTradeDate = QuoteResult.normalizeTimestamp(ts);

            qr.prevDayQuote = Double.toString(qr_obj.getDouble("regularMarketPreviousClose"));
            qr.recentQuote = Double.toString(qr_obj.getDouble("regularMarketPrice"));
            qr.companyName = qr_obj.getString("shortName");
            results.add(qr);
        }
        return results;
    }

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("accept", "application/json");
        headers.put("X-API-KEY", api_key_);
        return headers;
    }

    @Override
    public int getBatchSize() {
        return 10;
    }

    @Override
    public void prefetchSearchData() throws InterruptedException {
    }

    private SymbolSuggestion[] suggestSymbolsImpl(String prefix) throws Exception {
        UrlBuilder builder = new UrlBuilder(BASE_URL + AUTOCOMPLETE_ENDPOINT);
        builder.addParam("region", "US");
        builder.addParam("lang", "en");
        builder.addParam("query", prefix);

        URL url = builder.getUrl();
        String text = UrlBuilder.downloadUrl(url, getHeaders());
        JSONObject response = new JSONObject(text);
        JSONObject result_set = response.getJSONObject("ResultSet");
        JSONArray results = result_set.getJSONArray("Result");

        SymbolSuggestion[] suggestions = new SymbolSuggestion[results.length()];
        for (int i = 0; i < results.length(); i++) {
            JSONObject result = results.getJSONObject(i);
            SymbolSuggestion s = new SymbolSuggestion();
            s.companyName = result.getString("name");
            s.symbol = result.getString("symbol");
            suggestions[i] = s;
        }
        return suggestions;
    }

    @Override
    public SymbolSuggestion[] suggestSymbols(String prefix) {
        try {
            return suggestSymbolsImpl(prefix);
        } catch (Exception e) {
            return new SymbolSuggestion[0];
        }
    }
}
