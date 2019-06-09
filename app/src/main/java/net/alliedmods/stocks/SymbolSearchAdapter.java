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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import net.alliedmods.quotes.SymbolSuggestion;

public class SymbolSearchAdapter extends ArrayAdapter<SymbolSuggestion>
{
    private LayoutInflater inflater_;
    private SymbolSuggestion[] results_ =
            new SymbolSuggestion[0];

    public SymbolSearchAdapter(Context context, int resource) {
        super(context, resource);
        inflater_ = LayoutInflater.from(context);
    }

    public void update(SymbolSuggestion[] results) {
        results_ = results;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return results_.length;
    }

    @Override
    public SymbolSuggestion getItem(int position) {
        return results_[position];
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null)
            view = inflater_.inflate(R.layout.stock_suggestion, parent, false);

        SymbolSuggestion suggestion = getItem(position);

        TextView symbol_name = (TextView)view.findViewById(R.id.symbol_name);
        symbol_name.setText(suggestion.symbol);

        TextView company_name = (TextView)view.findViewById(R.id.company_name);
        company_name.setText(suggestion.companyName);

        return view;
    }
}
