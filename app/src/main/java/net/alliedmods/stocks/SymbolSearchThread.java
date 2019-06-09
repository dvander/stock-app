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

import android.util.Log;

import net.alliedmods.quotes.IQuoteService;
import net.alliedmods.quotes.SymbolSuggestion;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SymbolSearchThread extends IThread
{
    private static final String TAG = "SymbolSearchThread";

    private IQuoteService quote_service_;
    private String search_string_;
    private String last_search_string_;
    private OnSearchResults callback_;

    public SymbolSearchThread(IQuoteService service, OnSearchResults callback) {
        quote_service_ = service;
        callback_ = callback;
        thread_.start();
    }

    public interface OnSearchResults {
        public void onSearchResults(SymbolSuggestion[] suggestions);
    }

    public void beginSearch(String text) {
        lock_.lock();
        try {
            search_string_ = text;
            cv_.signal();
        } finally {
            lock_.unlock();
        }
    }

    @Override
    protected void processThreadActions() {
        try {
            quote_service_.prefetchSearchData();
        } catch (InterruptedException e) {
        }

        lock_.lock();
        while (true) {
            try {
                if (shutdown_)
                    return;

                // Note: non-compare is intentional here, because either could
                // be null.
                if (search_string_ == last_search_string_) {
                    cv_.await();
                    continue;
                }
            } catch (InterruptedException e) {
                // Interrupted, we need to go back and check the condition
                // again.
                continue;
            }

            // Acquire the search string, then release the lock so we can
            // process the text asynchronously.
            String search = search_string_;
            lock_.unlock();

            try {
                callback_.onSearchResults(quote_service_.suggestSymbols(search));
            } catch (Exception e) {
                Log.e(TAG, "Failed to search symbols", e);
            } finally {
                // We have to acquire the lock before checking the condition again.
                lock_.lock();
            }
            last_search_string_ = search;
        }
    }
}
