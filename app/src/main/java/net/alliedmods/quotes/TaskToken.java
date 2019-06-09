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

import java.util.concurrent.ExecutorService;

// This allows us to check whether a batch of requests completed.
public class TaskToken {
    private static int IdCounter = 0;

    private ExecutorService service_;
    private int tasks_added_ = 0;
    private int tasks_completed_ = 0;
    private boolean cancelled_ = false;
    private int task_id_ = 0;

    public TaskToken(ExecutorService service, int num_tasks) {
        service_ = service;
        task_id_ = GetNextTaskId();
        tasks_added_ = num_tasks;
    }

    public void cancel() {
        synchronized (this) {
            cancelled_ = true;
        }
        service_.shutdownNow();
        service_ = null;
    }

    public boolean done() {
        boolean done = false;
        synchronized (this) {
            done = cancelled_ || tasks_added_ == tasks_completed_;
        }
        if (done && service_ != null) {
            service_.shutdown();
            service_ = null;
        }
        return done;
    }

    public void receivedTaskCompletion() {
        synchronized (this) {
            tasks_completed_++;
        }
    }

    public int getTaskId() {
        return task_id_;
    }

    private static synchronized int GetNextTaskId() {
        return ++IdCounter;
    }
}
