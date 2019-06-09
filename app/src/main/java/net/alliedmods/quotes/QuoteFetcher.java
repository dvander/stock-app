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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuoteFetcher {
    private IQuoteService service_;

    public QuoteFetcher(IQuoteService service) {
        service_ = service;
    }

    public interface OnQuery {
        public void onQuotes(List<QuoteResult> result, Exception e, int task_id);
    }

    private class FetchTask implements Runnable {
        private TaskToken token_;
        private List<QuoteRequest> requests_;
        private OnQuery callback_;

        public FetchTask(TaskToken token, List<QuoteRequest> requests, OnQuery callback) {
            token_ = token;
            requests_ = requests;
            callback_ = callback;
        }

        @Override
        public void run() {
            List<QuoteResult> results = null;
            Exception ex = null;
            try {
                results = service_.query(requests_);
            } catch (Exception e) {
                ex = e;
                results = new ArrayList<QuoteResult>();
            }
            callback_.onQuotes(results, ex, token_.getTaskId());
        }
    }

    public TaskToken fetch(List<QuoteRequest> symbols, OnQuery callback) {
        int batch_size = service_.getBatchSize();
        int num_batches = getNumBatches(symbols.size(), batch_size);

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(num_batches, 4));
        TaskToken token = new TaskToken(pool, num_batches);

        for (int i = 0; i < num_batches; i++) {
            int start = i * batch_size;
            int end = Math.min(i * batch_size + batch_size, symbols.size());
            List<QuoteRequest> batch = symbols.subList(start, end);
            FetchTask task = new FetchTask(token, batch, callback);
            pool.submit(task);
        }
        return token;
    }

    private int getNumBatches(int symbols, int batch_size) {
        if (symbols % batch_size != 0)
            symbols += batch_size;
        return symbols / batch_size;
    }

    public void shutdown() {
        service_.shutdown();
    }
}
