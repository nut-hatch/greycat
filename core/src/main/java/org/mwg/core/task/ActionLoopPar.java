package org.mwg.core.task;

import org.mwg.Callback;
import org.mwg.DeferCounter;
import org.mwg.plugin.AbstractTaskAction;
import org.mwg.plugin.Job;
import org.mwg.plugin.SchedulerAffinity;
import org.mwg.task.Task;
import org.mwg.task.TaskContext;
import org.mwg.task.TaskResult;

class ActionLoopPar extends AbstractTaskAction {

    private final Task _subTask;

    private final String _lower;
    private final String _upper;

    ActionLoopPar(final String p_lower, final String p_upper, final Task p_subTask) {
        super();
        this._subTask = p_subTask;
        this._lower = p_lower;
        this._upper = p_upper;
    }

    @Override
    public void eval(final TaskContext context) {
        final int lower = TaskHelper.parseInt(context.template(_lower));
        final int upper = TaskHelper.parseInt(context.template(_upper));
        final TaskResult previous = context.result();
        final TaskResult next = context.newResult();
        if ((upper - lower) > 0) {
            DeferCounter waiter = context.graph().newCounter((upper - lower) + 1);
            for (int i = lower; i <= upper; i++) {
                final int finalI = i;
                _subTask.executeFromUsing(context, previous, SchedulerAffinity.ANY_LOCAL_THREAD, new Callback<TaskContext>() {
                    @Override
                    public void on(TaskContext result) {
                        result.defineVariable("i", finalI);
                    }
                }, new Callback<TaskResult>() {
                    @Override
                    public void on(TaskResult result) {
                        if (result != null && result.size() > 0) {
                            for (int i = 0; i < result.size(); i++) {
                                next.add(result.get(i));
                            }
                        }
                        waiter.count();
                    }
                });
            }
            waiter.then(new Job() {
                @Override
                public void run() {
                    context.continueWith(next);
                }
            });
        } else {
            context.continueWith(next);
        }
    }

    @Override
    public String toString() {
        return "loopPar(\'" + _lower + "\',\'" + _upper + "\')";
    }

}