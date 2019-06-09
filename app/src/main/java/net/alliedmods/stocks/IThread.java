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

import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class IThread
{
    private static Map<Long, WeakReference<ICancelable>> CancelTask =
            new HashMap<Long, WeakReference<ICancelable>>();

    protected Thread thread_;
    protected Lock lock_ = new ReentrantLock();
    protected Condition cv_;
    protected boolean shutdown_ = false;

    public IThread() {
        cv_ = lock_.newCondition();
        thread_ = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    processThreadActions();
                } catch (InterruptedException e) {
                }
            }
        });
    }

    public IThread(Runnable runnable) {
        cv_ = lock_.newCondition();
        thread_ = new Thread(runnable);
        thread_.start();
    }

    protected void processThreadActions() throws InterruptedException {
    }

    public void shutdown() {
        lock_.lock();
        try {
            shutdown_ = true;
            interrupt();
        } finally {
            lock_.unlock();
        }

        CancelTask.remove(thread_.getId());

        try {
            thread_.join();
        } catch (InterruptedException e) {
        }
    }

    private void interrupt() {
        synchronized (CancelTask) {
            WeakReference<ICancelable> task_ref = CancelTask.get(thread_.getId());
            if (task_ref != null) {
                ICancelable task = task_ref.get();
                if (task != null)
                    task.onCancel();
            }
            CancelTask.remove(thread_.getId());
        }
        thread_.interrupt();
    }

    public static void addCancelTask(ICancelable task) {
        // Ignore the UI thread.
        if (Looper.getMainLooper().isCurrentThread())
            return;

        Thread current = Thread.currentThread();
        synchronized (CancelTask) {
            CancelTask.put(current.getId(), new WeakReference<ICancelable>(task));
        }
    }

    public static void removeCancelTask(ICancelable task) {
        // Ignore the UI thread.
        if (Looper.getMainLooper().isCurrentThread())
            return;

        Thread current = Thread.currentThread();
        synchronized (CancelTask) {
            CancelTask.remove(current.getId());
        }
    }
}
