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

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import net.alliedmods.quotes.IQuoteService;
import net.alliedmods.quotes.QuoteFetcher;
import net.alliedmods.quotes.QuoteRequest;
import net.alliedmods.quotes.QuoteResult;
import net.alliedmods.quotes.TaskToken;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    static final private String TAG = "MainActivity";

    private Settings settings_;
    private IQuoteService quote_service_;
    private QuoteFetcher quote_fetcher_;

    // Map symbol to view rows.
    private Map<String, View> stock_rows_ = new HashMap<String, View>();
    private TaskToken refresh_task_;
    private SwipeRefreshLayout refresh_view_;

    enum ChangeBoxMode {
        Value,
        Percent
    };
    ChangeBoxMode changebox_mode_ = ChangeBoxMode.Value;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings_ = StockApplication.getSettings();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!initializeQuoteService())
            return;
        populate();

        View view = findViewById(R.id.settings_button);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnClickSettingsButton(v);
            }
        });

        view = findViewById(R.id.list_button);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OnClickReorderButton(v);
            }
        });

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("LLLL d");
        TextView text = findViewById(R.id.subtitle);
        text.setText(LocalDate.now().format(formatter));

        refresh_view_ = (SwipeRefreshLayout)findViewById(R.id.swipe_refresh);
        refresh_view_.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (quote_fetcher_ != null)
            quote_fetcher_.shutdown();
    }

    private boolean initializeQuoteService() {
        quote_service_ = settings_.createQuoteService();
        if (quote_service_ == null) {
            LaunchSettingsActivity();
            return false;
        }
        quote_fetcher_ = new QuoteFetcher(quote_service_);
        return true;
    }

    private void populate() {
        Map<String, CachedQuote> cachedQuotes = settings_.getCachedQuotes();

        LinearLayout container = (LinearLayout)findViewById(R.id.stock_list);
        container.removeAllViews();

        LayoutInflater inflater = getLayoutInflater();
        String[] symbols = settings_.getStockSymbols();
        for (String symbol : symbols) {
            CachedQuote quote = cachedQuotes.get(symbol);
            View view = addStockRow(inflater, container, symbol);
            stock_rows_.put(symbol, view);
            updateStockRow(symbol, quote);
            container.addView(inflater.inflate(R.layout.divider_line, null));
        }

        refresh();
    }

    private View addStockRow(LayoutInflater inflater, LinearLayout container, String symbol) {
        View root = inflater.inflate(R.layout.stock_row, null);

        TextView symbol_name = (TextView)root.findViewById(R.id.symbol_name);
        symbol_name.setText(symbol);

        View changebox = root.findViewById(R.id.change_box);
        changebox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flipChangeBoxMode();
            }
        });

        container.addView(root);
        return root;
    }

    private void refresh() {
        cancelRefresh();

        List<QuoteRequest> requests = new ArrayList<QuoteRequest>();
        for (Map.Entry<String, View> entry : stock_rows_.entrySet()) {
            CachedQuote cache = settings_.getCachedQuotes().get(entry.getKey());
            String lastTradeDate = cache != null ? cache.lastTradeDate : null;
            boolean fetchName = (cache == null || cache.companyName == null);
            requests.add(new QuoteRequest(entry.getKey(), lastTradeDate, fetchName));
        }

        final MainActivity activity = this;

        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                activity.handleQuoteMessage(msg.getData());
            }
        };

        QuoteFetcher.OnQuery callback = new QuoteFetcher.OnQuery() {
            @Override
            public void onQuotes(List<QuoteResult> results, Exception e, int task_id) {
                Message msg = new Message();
                msg.setAsynchronous(true);

                Bundle b = new Bundle();
                b.putInt("count", results.size());
                for (int i = 0; i < results.size(); i++)
                    b.putBundle(Integer.toString(i), results.get(i).serialize());
                if (e != null)
                    b.putString("exception", e.getMessage());
                b.putInt("task_id", task_id);
                msg.setData(b);
                handler.sendMessage(msg);
            }
        };

        refresh_task_ = quote_fetcher_.fetch(requests, callback);
    }

    private void cancelRefresh() {
        if (refresh_task_ == null)
            return;
        refresh_task_.cancel();
        refresh_task_ = null;

        refresh_view_.setRefreshing(false);
    }

    private void handleQuoteMessage(Bundle b) {
        int task_id = b.getInt("task_id");
        if (refresh_task_ == null || refresh_task_.getTaskId() != task_id)
            return;

        refresh_task_.receivedTaskCompletion();
        if (refresh_task_.done()) {
            refresh_task_ = null;
            refresh_view_.setRefreshing(false);
        }

        if (b.containsKey("exception")) {
            Log.e(TAG, "Failed to query quotes: " + b.getString("exception"));
            return;
        }

        int count = b.getInt("count");
        for (int i = 0; i < count; i++) {
            Bundle child = b.getBundle(Integer.toString(i));
            QuoteResult result = QuoteResult.deserialize(child);
            CachedQuote quote = null;
            if (result.success) {
                quote = updateCache(result);
            } else {
                Log.e(TAG, "Could not query symbol " + result.symbol + ": " +
                        result.error);
                quote = maybeFetchCache(result.symbol);
            }
            updateStockRow(result.symbol, quote);
        }
    }

    private void updateStockRow(String symbol, CachedQuote quote) {
        View view = stock_rows_.get(symbol);
        if (view == null) {
            Log.e(TAG, "Could not find View for symbol: " + symbol);
            return;
        }

        BigDecimal price = CachedQuote.getPrice(quote);
        TextView quote_box = (TextView) view.findViewById(R.id.quote);
        if (price != null)
            quote_box.setText(Utilities.formatPrice(price));
        else
            quote_box.setText("");

        TextView name_box = (TextView)view.findViewById(R.id.company_name);
        if (quote != null && quote.companyName != null)
            name_box.setText(quote.companyName);

        TextView changebox = (TextView)view.findViewById(R.id.change_box);
        updateChangeBox(changebox, quote);
    }

    // CachedQuote here can be null.
    private void updateChangeBox(TextView changebox, CachedQuote quote) {
        String value;
        BigDecimal change = null;
        String change_text = null;
        if (changebox_mode_ == ChangeBoxMode.Value) {
            change = CachedQuote.getPriceChange(quote);
            if (change != null)
                change_text = Utilities.formatPrice(change);
        } else if (changebox_mode_ == ChangeBoxMode.Percent) {
            change = CachedQuote.getPercentChange(quote);
            if (change != null)
                change_text = Utilities.formatPercent(change);
        }

        int bgcolor_id;
        int textcolor_id;
        if (change == null) {
            bgcolor_id = R.color.white;
            textcolor_id = R.color.black;
        } else {
            if (change.signum() >= 0) {
                change_text = "+" + change_text;
                bgcolor_id = R.color.green;
            } else {
                bgcolor_id = R.color.red;
            }
            textcolor_id = R.color.almost_white;
        }

        int bgcolor = ResourcesCompat.getColor(getResources(), bgcolor_id, null);
        Drawable pill_box = Utilities.getPillBox(this, bgcolor);
        if (change_text != null) {
            changebox.setText(change_text);
            changebox.setGravity(Gravity.RIGHT);
        } else {
            changebox.setText("    -    ");
            changebox.setGravity(Gravity.CENTER);
        }
        changebox.setBackground(pill_box);
        changebox.setTextColor(ResourcesCompat.getColor(getResources(), textcolor_id, null));
    }

    private void flipChangeBoxMode() {
        if (changebox_mode_ == ChangeBoxMode.Percent)
            changebox_mode_ = ChangeBoxMode.Value;
        else
            changebox_mode_ = ChangeBoxMode.Percent;

        Map<String, CachedQuote> quotes = settings_.getCachedQuotes();
        for (Map.Entry<String, View> entry : stock_rows_.entrySet()) {
            String symbol = entry.getKey();
            View view = entry.getValue();
            TextView changebox = (TextView)view.findViewById(R.id.change_box);
            updateChangeBox(changebox, quotes.get(symbol));
        }
    }

    private CachedQuote updateCache(QuoteResult result) {
        CachedQuote quote = new CachedQuote(result.symbol);
        quote.lastTradeDate = result.lastTradeDate;
        quote.recentQuote = result.recentQuote;

        // If the service didn't return a quote for the previous day, use the cached result. This
        // can happen if we're trying to avoid an extra query on APIs that have separate endpoints
        // for realtime queries vs historical.
        CachedQuote prev_quote = settings_.getCachedQuotes().get(result.symbol);
        if (result.prevDayQuote != null)
            quote.prevDayQuote = result.prevDayQuote;
        else if (prev_quote != null && prev_quote.lastTradeDate.equals(result.lastTradeDate))
            quote.prevDayQuote = prev_quote.prevDayQuote;

        // Fill the company name in, if present.
        if (result.companyName != null)
            quote.companyName = result.companyName;
        else
            quote.companyName = prev_quote.companyName;

        quote.cacheDate = LocalDate.now(Utilities.UTC).toString();
        settings_.saveCachedQuote(quote);
        return quote;
    }

    private CachedQuote maybeFetchCache(String symbol) {
        CachedQuote quote = settings_.getCachedQuotes().get(symbol);
        if (quote == null || quote.lastTradeDate == null)
            return null;

        LocalDate now = LocalDate.now(Utilities.UTC);
        LocalDate then = Utilities.parseYearMonthDay(quote.lastTradeDate);
        long days = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(now, then));
        if (days > 1)
            return null;
        return quote;
    }

    private void OnClickSettingsButton(View v) {
        LaunchSettingsActivity();
    }
    private void OnClickReorderButton(View v) {
        Intent intent = new Intent(this, EditListActivity.class);
        startActivity(intent);
    }

    private void LaunchSettingsActivity() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
}
