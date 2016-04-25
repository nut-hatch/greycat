package org.mwg.core.task;

import org.mwg.Graph;
import org.mwg.Node;
import org.mwg.task.TaskAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class TaskContext implements org.mwg.task.TaskContext {

    private final Map<String, Object> _variables;
    private final Object[] _results;
    private final Graph _graph;
    private final TaskAction[] _actions;
    private final AtomicInteger _currentTaskId;
    private final org.mwg.task.TaskContext _parentContext;
    private final Object _initialResult;

    private long _world;
    private long _time;

    TaskContext(final org.mwg.task.TaskContext p_parentContext, final Object p_initialResult, final Graph p_graph, final TaskAction[] p_actions) {
        this._world = 0;
        this._time = 0;
        this._graph = p_graph;
        this._parentContext = p_parentContext;
        this._initialResult = p_initialResult;
        this._variables = new ConcurrentHashMap<String, Object>();
        this._results = new Object[p_actions.length];
        this._actions = p_actions;
        this._currentTaskId = new AtomicInteger(0);
    }

    @Override
    public final Graph graph() {
        return _graph;
    }

    @Override
    public final long getWorld() {
        return this._world;
    }

    @Override
    public final void setWorld(long p_world) {
        this._world = p_world;
    }

    @Override
    public final long getTime() {
        return this._time;
    }

    @Override
    public final void setTime(long p_time) {
        this._time = p_time;
    }

    @Override
    public final Object getVariable(String name) {
        Object result = this._variables.get(name);
        if (result != null) {
            return result;
        }
        if (_parentContext != null) {
            return this._parentContext.getVariable(name);
        }
        return null;
    }

    @Override
    public final void setVariable(String name, Object value) {
        this._variables.put(name, value);
    }

    @Override
    public final Object getPreviousResult() {
        int current = _currentTaskId.get();
        if (current == 0) {
            return _initialResult;
        } else {
            Object previousResult = _results[current - 1];
            if (previousResult != null && previousResult instanceof org.mwg.task.TaskContext) {
                return ((org.mwg.task.TaskContext) previousResult).getPreviousResult();
            } else if (previousResult != null && previousResult instanceof org.mwg.task.TaskContext[]) {
                org.mwg.task.TaskContext[] contexts = (org.mwg.task.TaskContext[]) previousResult;
                List<Object> result = new ArrayList<Object>();
                for (int i = 0; i < contexts.length; i++) {
                    Object currentLoop = contexts[i].getPreviousResult();
                    if (currentLoop != null) {
                        result.add(currentLoop);
                    }
                }
                return result.toArray(new Object[result.size()]);
            } else {
                return previousResult;
            }
        }
    }

    @Override
    public final void setResult(Object actionResult) {
        this._results[_currentTaskId.get()] = actionResult;
    }

    @Override
    public final void next() {
        TaskAction nextAction = _actions[_currentTaskId.incrementAndGet()];
        nextAction.eval(this);
    }

    @Override
    public final void clean() {
        for (int i = 0; i < _results.length; i++) {
            cleanObj(_results[i]);
        }
    }

    private void cleanObj(Object o){
        if (o instanceof Node) {
            ((Node) o).free();
        } else if (o instanceof org.mwg.task.TaskContext) {
            ((org.mwg.task.TaskContext)o).clean();
        } else if (o instanceof org.mwg.task.TaskContext[]) {
            org.mwg.task.TaskContext[] loop = (org.mwg.task.TaskContext[]) o;
            for (int j = 0; j < loop.length; j++) {
                loop[j].clean();
            }
        } else if (o instanceof Object[]) {
            Object[] loop = (Object[]) o;
            for (int j = 0; j < loop.length; j++) {
                cleanObj(loop[j]);
            }
        }
    }

}