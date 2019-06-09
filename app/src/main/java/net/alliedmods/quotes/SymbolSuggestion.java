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

import android.os.Bundle;

public class SymbolSuggestion {
    public String symbol;
    public String companyName;

    public static SymbolSuggestion deserialize(Bundle b) {
        SymbolSuggestion s = new SymbolSuggestion();
        s.companyName = b.getString("companyName");
        s.symbol = b.getString("symbol");
        return s;
    }

    public Bundle serialize() {
        Bundle b = new Bundle();
        b.putString("companyName", companyName);
        b.putString("symbol", symbol);
        return b;
    }
}
