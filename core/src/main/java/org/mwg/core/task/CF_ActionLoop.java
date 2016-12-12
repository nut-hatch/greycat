package org.mwg.core.task;

import org.mwg.Callback;
import org.mwg.plugin.SchedulerAffinity;
import org.mwg.task.Action;
import org.mwg.task.Task;
import org.mwg.task.TaskContext;
import org.mwg.task.TaskResult;

import java.util.concurrent.atomic.AtomicInteger;

class CF_ActionLoop implements Action {

    private final Task _subTask;

    private final String _lower;
    private final String _upper;

    CF_ActionLoop(final String p_lower, final String p_upper, final Task p_subTask) {
        this._subTask = p_subTask;
        this._lower = p_lower;
        this._upper = p_upper;
    }

    @Override
    public void eval(final TaskContext ctx) {

        final String lowerString = ctx.template(_lower);
        final String upperString = ctx.template(_upper);
        final int lower = (int) Double.parseDouble(ctx.template(lowerString));
        final int upper = (int) Double.parseDouble(ctx.template(upperString));
        final TaskResult previous = ctx.result();
        final CF_ActionLoop selfPointer = this;
        final AtomicInteger cursor = new AtomicInteger(lower);
        if ((upper - lower) >= 0) {
            final Callback[] recursiveAction = new Callback[1];
            recursiveAction[0] = new Callback<TaskResult>() {
                @Override
                public void on(final TaskResult res) {
                    final int current = cursor.getAndIncrement();
                    if (res != null) {
                        res.free();
                    }
                    if (current > upper) {
                        ctx.continueTask();
                    } else {
                        //recursive call
                        selfPointer._subTask.executeFromUsing(ctx, previous, SchedulerAffinity.SAME_THREAD, new Callback<TaskContext>() {
                            @Override
                            public void on(TaskContext result) {
                                result.defineVariable("i", current);
                            }
                        }, recursiveAction[0]);
                    }
                }
            };
            _subTask.executeFromUsing(ctx, previous, SchedulerAffinity.SAME_THREAD, new Callback<TaskContext>() {
                @Override
                public void on(TaskContext result) {
                    result.defineVariable("i", cursor.getAndIncrement());
                }
            }, recursiveAction[0]);
        } else {
            ctx.continueTask();
        }
    }

    @Override
    public String toString() {
        return "loop(\'" + _lower + "\',\'" + _upper + "\')";
    }

    @Override
    public void serialize(StringBuilder builder) {
        throw new RuntimeException("Not managed yet!");
    }

}
