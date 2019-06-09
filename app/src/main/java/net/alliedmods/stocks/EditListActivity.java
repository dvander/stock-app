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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EditListActivity extends AppCompatActivity {
    private Settings settings_;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_list);

        settings_ = StockApplication.getSettings();
    }

    @Override
    protected void onStart() {
        super.onStart();

        final Context context = this;

        populate();

        View button = findViewById(R.id.add_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, AddStockActivity.class);
                startActivity(intent);
            }
        });
    }

    private void populate() {
        LinearLayoutManager layout = new LinearLayoutManager(this);

        RecyclerView container = (RecyclerView) findViewById(R.id.stock_list);
        container.setLayoutManager(layout);

        DividerItemDecoration decoration = new DividerItemDecoration(
                container.getContext(), layout.getOrientation());
        decoration.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider_line));
        container.addItemDecoration(decoration);

        Map<String, CachedQuote> cached_quotes = settings_.getCachedQuotes();
        List<CachedQuote> quotes = new ArrayList<CachedQuote>();
        String[] symbols = settings_.getStockSymbols();
        for (String symbol : symbols) {
            CachedQuote quote = cached_quotes.get(symbol);
            if (quote == null)
                quote = new CachedQuote(symbol);
            quotes.add(quote);
        }

        StockViewAdapter adapter = new StockViewAdapter(this, settings_, quotes);
        container.setAdapter(adapter);

        addTouchHelper(container, adapter);
    }

    private void addTouchHelper(RecyclerView container, final StockViewAdapter adapter) {
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                int swipeFlags = ItemTouchHelper.LEFT;
                return makeMovementFlags(dragFlags, swipeFlags);
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                adapter.reorder(viewHolder.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                if (direction == ItemTouchHelper.LEFT)
                    adapter.removeItem(viewHolder);
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
        });
        helper.attachToRecyclerView(container);
    }
}
