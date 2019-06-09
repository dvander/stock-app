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

import java.util.List;

public interface IQuoteService
{
    public abstract void shutdown();
    public abstract List<QuoteResult> query(List<QuoteRequest> requests) throws Exception;
    public abstract int getBatchSize();
    public abstract void prefetchSearchData() throws InterruptedException;
    public abstract SymbolSuggestion[] suggestSymbols(String prefix);
}
