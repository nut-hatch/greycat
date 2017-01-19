package org.mwg.internal.task;

import org.mwg.*;
import org.mwg.base.BaseTaskResult;
import org.mwg.internal.CoreConstants;
import org.mwg.plugin.*;
import org.mwg.struct.Buffer;
import org.mwg.task.*;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CoreTask implements org.mwg.task.Task {

    private int insertCapacity = Constants.MAP_INITIAL_CAPACITY;
    public Action[] actions = new Action[insertCapacity];
    public int insertCursor = 0;
    TaskHook[] _hooks = null;


    @Override
    public final Task addHook(final TaskHook p_hook) {
        if (_hooks == null) {
            _hooks = new TaskHook[1];
            _hooks[0] = p_hook;
        } else {
            TaskHook[] new_hooks = new TaskHook[_hooks.length + 1];
            System.arraycopy(_hooks, 0, new_hooks, 0, _hooks.length);
            new_hooks[_hooks.length] = p_hook;
            _hooks = new_hooks;
        }
        return this;
    }

    @Override
    public final Task then(Action nextAction) {
        if (insertCapacity == insertCursor) {
            Action[] new_actions = new Action[insertCapacity * 2];
            System.arraycopy(actions, 0, new_actions, 0, insertCapacity);
            actions = new_actions;
            insertCapacity = insertCapacity * 2;
        }
        actions[insertCursor] = nextAction;
        insertCursor++;
        return this;
    }

    @Override
    public final Task thenDo(ActionFunction nextActionFunction) {
        return then(new CF_ThenDo(nextActionFunction));
    }

    @Override
    public final Task doWhile(Task task, ConditionalFunction cond) {
        return then(new CF_DoWhile(task, cond, null));
    }

    @Override
    public final Task doWhileScript(Task task, String condScript) {
        return then(new CF_DoWhile(task, condFromScript(condScript), condScript));
    }

    @Override
    public final Task loop(String from, String to, Task subTask) {
        return then(new CF_Loop(from, to, subTask));
    }

    @Override
    public final Task loopPar(String from, String to, Task subTask) {
        return then(new CF_LoopPar(from, to, subTask));
    }

    @Override
    public final Task forEach(Task subTask) {
        return then(new CF_ForEach(subTask));
    }

    @Override
    public final Task forEachPar(Task subTask) {
        return then(new CF_ForEachPar(subTask));
    }

    @Override
    public final Task map(Task subTask) {
        return then(new CF_Map(subTask));
    }

    @Override
    public final Task mapPar(Task subTask) {
        return then(new CF_MapPar(subTask));
    }

    @Override
    public final Task ifThen(ConditionalFunction cond, Task then) {
        return then(new CF_IfThen(cond, then, null));
    }

    @Override
    public final Task ifThenScript(String condScript, Task then) {
        return then(new CF_IfThen(condFromScript(condScript), then, condScript));
    }

    @Override
    public final Task ifThenElse(ConditionalFunction cond, Task thenSub, Task elseSub) {
        return then(new CF_IfThenElse(cond, thenSub, elseSub, null));
    }

    @Override
    public final Task ifThenElseScript(String condScript, Task thenSub, Task elseSub) {
        return then(new CF_IfThenElse(condFromScript(condScript), thenSub, elseSub, condScript));
    }

    @Override
    public final Task whileDo(ConditionalFunction cond, Task task) {
        return then(new CF_WhileDo(cond, task, null));
    }

    @Override
    public final Task whileDoScript(String condScript, Task task) {
        return then(new CF_WhileDo(condFromScript(condScript), task, condScript));
    }

    @Override
    public final Task pipe(Task... subTasks) {
        return then(new CF_Pipe(subTasks));
    }

    @Override
    public final Task pipePar(Task... subTasks) {
        return then(new CF_PipePar(subTasks));
    }

    @Override
    public final Task pipeTo(Task subTask, String... vars) {
        return then(new CF_PipeTo(subTask, vars));
    }

    @Override
    public final Task atomic(Task protectedTask, String... variablesToLock) {
        return then(new CF_Atomic(protectedTask, variablesToLock));
    }

    @Override
    public final void execute(final Graph graph, final Callback<TaskResult> callback) {
        executeWith(graph, null, callback);
    }

    @Override
    public final TaskResult executeSync(final Graph graph) {
        DeferCounterSync waiter = graph.newSyncCounter(1);
        executeWith(graph, null, waiter.wrap());
        return (TaskResult) waiter.waitResult();
    }

    @Override
    public final void executeWith(final Graph graph, final Object initial, final Callback<TaskResult> callback) {
        if (insertCursor > 0) {
            final TaskResult initalRes;
            if (initial instanceof BaseTaskResult) {
                initalRes = ((TaskResult) initial).clone();
            } else {
                initalRes = new BaseTaskResult(initial, true);
            }
            final CoreTaskContext context = new CoreTaskContext(this, _hooks, null, initalRes, graph, callback);
            graph.scheduler().dispatch(SchedulerAffinity.SAME_THREAD, new Job() {
                @Override
                public void run() {
                    context.execute();
                }
            });
        } else {
            if (callback != null) {
                callback.on(Tasks.emptyResult());
            }
        }
    }

    @Override
    public final TaskContext prepare(Graph graph, Object initial, Callback<TaskResult> callback) {
        final TaskResult initalRes;
        if (initial instanceof BaseTaskResult) {
            initalRes = ((TaskResult) initial).clone();
        } else {
            initalRes = new BaseTaskResult(initial, true);
        }
        return new CoreTaskContext(this, _hooks, null, initalRes, graph, callback);
    }

    @Override
    public final void executeUsing(final TaskContext preparedContext) {
        if (insertCursor > 0) {
            preparedContext.graph().scheduler().dispatch(SchedulerAffinity.SAME_THREAD, new Job() {
                @Override
                public void run() {
                    ((CoreTaskContext) preparedContext).execute();
                }
            });
        } else {
            CoreTaskContext casted = (CoreTaskContext) preparedContext;
            if (casted._callback != null) {
                casted._callback.on(Tasks.emptyResult());
            }
        }
    }

    @Override
    public final void executeFrom(final TaskContext parentContext, final TaskResult initial, byte affinity, final Callback<TaskResult> callback) {
        if (insertCursor > 0) {
            TaskHook[] aggregatedHooks = null;
            if (parentContext != null) {
                aggregatedHooks = ((CoreTaskContext) parentContext)._hooks;
            }
            if (_hooks != null) {
                if (aggregatedHooks == null) {
                    aggregatedHooks = _hooks;
                } else {
                    TaskHook[] temp_hooks = new TaskHook[aggregatedHooks.length + _hooks.length];
                    System.arraycopy(aggregatedHooks, 0, temp_hooks, 0, aggregatedHooks.length);
                    System.arraycopy(_hooks, 0, temp_hooks, aggregatedHooks.length, _hooks.length);
                    aggregatedHooks = temp_hooks;
                }
            }
            final CoreTaskContext context = new CoreTaskContext(this, aggregatedHooks, parentContext, initial.clone(), parentContext.graph(), callback);
            parentContext.graph().scheduler().dispatch(affinity, new Job() {
                @Override
                public void run() {
                    context.execute();
                }
            });
        } else {
            if (callback != null) {
                callback.on(Tasks.emptyResult());
            }
        }
    }

    @Override
    public final void executeFromUsing(TaskContext parentContext, TaskResult initial, byte affinity, Callback<TaskContext> contextInitializer, Callback<TaskResult> callback) {
        if (insertCursor > 0) {
            TaskHook[] aggregatedHooks = null;
            if (parentContext != null) {
                aggregatedHooks = ((CoreTaskContext) parentContext)._hooks;
            }
            if (_hooks != null) {
                if (aggregatedHooks == null) {
                    aggregatedHooks = _hooks;
                } else {
                    TaskHook[] temp_hooks = new TaskHook[aggregatedHooks.length + _hooks.length];
                    System.arraycopy(aggregatedHooks, 0, temp_hooks, 0, aggregatedHooks.length);
                    System.arraycopy(_hooks, 0, temp_hooks, aggregatedHooks.length, _hooks.length);
                    aggregatedHooks = temp_hooks;
                }
            }
            final CoreTaskContext context = new CoreTaskContext(this, aggregatedHooks, parentContext, initial.clone(), parentContext.graph(), callback);
            if (contextInitializer != null) {
                contextInitializer.on(context);
            }
            parentContext.graph().scheduler().dispatch(affinity, new Job() {
                @Override
                public void run() {
                    context.execute();
                }
            });
        } else {
            if (callback != null) {
                callback.on(Tasks.emptyResult());
            }
        }
    }

    @Override
    public final Task loadFromBuffer(final Buffer buffer, final Graph graph) {
        return parse(org.mwg.utility.Base64.decodeToStringWithBounds(buffer, 0, buffer.length()), graph);
    }

    @Override
    public final Task saveToBuffer(Buffer buffer) {
        final String saved = toString();
        org.mwg.utility.Base64.encodeStringToBuffer(saved, buffer);
        return this;
    }

    @Override
    public final Task parse(final String flat, final Graph graph) {
        if (flat == null) {
            throw new RuntimeException("flat should not be null");
        }
        final Map<Integer, Task> contextTasks = new HashMap<Integer, Task>();
        sub_parse(new CoreTaskReader(flat, 0), graph, contextTasks);
        return this;
    }

    private void sub_parse(final CoreTaskReader reader, final Graph graph, final Map<Integer, Task> contextTasks) {
        final ActionRegistry registry = graph.actionRegistry();
        int cursor = 0;
        int flatSize = reader.available();
        int previous = 0;
        String actionName = null;
        boolean isClosed = false;
        boolean isEscaped = false;
        //Param storage
        int paramsCapacity = 0;
        String[] params = null;
        int paramsIndex = 0;
        String previousTaskId = null;
        boolean subTaskMode = false;
        while (cursor < flatSize) {
            final char current = reader.charAt(cursor);
            switch (current) {
                case '\"':
                case '\'':
                    isEscaped = true;
                    cursor++;
                    boolean previousBackS = false;
                    while (cursor < flatSize) {
                        char loopChar = reader.charAt(cursor);
                        if (loopChar == '\\') {
                            previousBackS = true;
                        } else if (current == loopChar && !previousBackS) {
                            break;
                        } else {
                            previousBackS = false;
                        }
                        cursor++;
                    }
                    break;
                case Constants.TASK_SEP:
                    if (!isClosed) {
                        final String getName = reader.extract(previous, cursor).trim();
                        then(new ActionTraverseOrAttribute(false, true, getName));//default action
                    }
                    actionName = null;
                    isEscaped = false;
                    previous = cursor + 1;
                    paramsCapacity = 0;
                    params = null;
                    paramsIndex = 0;
                    isClosed = false;
                    break;
                case Constants.SUB_TASK_DECLR:
                    if (!isClosed) {
                        final String getName = reader.extract(previous, cursor).trim();
                        then(new ActionTraverseOrAttribute(false, true, getName));//default action
                    }
                    subTaskMode = true;
                    actionName = null;
                    isEscaped = false;
                    previous = cursor + 1;
                    paramsCapacity = 0;
                    params = null;
                    paramsIndex = 0;
                    break;
                case Constants.TASK_PARAM_OPEN:
                    actionName = reader.extract(previous, cursor).trim();
                    previous = cursor + 1;
                    break;
                case Constants.TASK_PARAM_CLOSE:
                    //ADD LAST PARAM
                    String lastParamExtracted;
                    if (previousTaskId != null) {
                        lastParamExtracted = previousTaskId;
                        previousTaskId = null;
                    } else {
                        if (isEscaped) {
                            lastParamExtracted = reader.extract(previous + 1, cursor - 1);
                        } else {
                            lastParamExtracted = reader.extract(previous, cursor);
                        }
                    }
                    if (lastParamExtracted.length() > 0) {
                        if ((paramsIndex + 1) != paramsCapacity) {
                            String[] newParams = new String[paramsIndex + 1];
                            if (params != null) {
                                System.arraycopy(params, 0, newParams, 0, paramsIndex);
                            }
                            params = newParams;
                            paramsCapacity = paramsIndex + 1;
                        }
                        params[paramsIndex] = lastParamExtracted;
                        paramsIndex++;
                    } else {
                        //shrink params
                        if (paramsIndex < paramsCapacity) {
                            String[] shrinked = new String[paramsIndex];
                            System.arraycopy(params, 0, shrinked, 0, paramsIndex);
                            params = shrinked;
                        }
                    }
                    if (graph == null) {
                        then(new ActionNamed(actionName, params));
                    } else {
                        then(loadAction(registry, actionName, params, contextTasks));
                    }
                    actionName = null;
                    previous = cursor + 1;
                    isClosed = true;
                    //ADD TASK
                    break;
                case Constants.TASK_PARAM_SEP:
                    String paramExtracted;
                    if (previousTaskId != null) {
                        paramExtracted = previousTaskId;
                        previousTaskId = null;
                    } else {
                        if (isEscaped) {
                            paramExtracted = reader.extract(previous + 1, cursor - 1);
                        } else {
                            paramExtracted = reader.extract(previous, cursor);
                        }
                    }
                    if (paramExtracted.length() > 0) {
                        if (paramsIndex >= paramsCapacity) {
                            int newParamsCapacity = paramsCapacity * 2;
                            if (newParamsCapacity == 0) {
                                newParamsCapacity = CoreConstants.MAP_INITIAL_CAPACITY;
                            }
                            String[] newParams = new String[newParamsCapacity];
                            if (params != null) {
                                System.arraycopy(params, 0, newParams, 0, paramsCapacity);
                            }
                            params = newParams;
                            paramsCapacity = newParamsCapacity;
                        }
                        params[paramsIndex] = paramExtracted;
                        paramsIndex++;
                    }
                    previous = cursor + 1;
                    isEscaped = false;
                    break;
                case Constants.SUB_TASK_OPEN:
                    if (cursor > 0 && cursor + 1 < flatSize && reader.charAt(cursor + 1) != Constants.SUB_TASK_OPEN && reader.charAt(cursor - 1) != Constants.SUB_TASK_OPEN) {
                        CoreTaskReader subReader = reader.slice(cursor + 1);
                        CoreTask subTask;
                        if (subTaskMode) {
                            final String subTaskName = reader.extract(previous, cursor).trim();
                            final Integer subTaskID = TaskHelper.parseInt(subTaskName);
                            subTask = (CoreTask) contextTasks.get(subTaskID);
                        } else {
                            subTask = new CoreTask();
                        }
                        subTask.sub_parse(subReader, graph, contextTasks);
                        cursor = cursor + subReader.end() + 1;
                        previous = cursor + 1; //to skip the string param
                        Integer hash = subTask.hashCode();
                        contextTasks.put(hash, subTask);
                        previousTaskId = hash + "";
                    }
                    break;

                case Constants.SUB_TASK_CLOSE:
                    if (cursor > 0 && cursor + 1 < flatSize && reader.charAt(cursor + 1) != Constants.SUB_TASK_CLOSE && reader.charAt(cursor - 1) != Constants.SUB_TASK_CLOSE) {
                        reader.markend(cursor);
                        return;
                    }
                    break;
            }
            cursor++;
        }
        if (!isClosed) {
            final String getName = reader.extract(previous, flatSize);
            if (getName.length() > 0) {
                if (actionName != null) {
                    if (graph == null) {
                        then(new ActionNamed(actionName, params));
                    } else {
                        final String[] singleParam = new String[1];
                        singleParam[0] = getName;
                        then(loadAction(registry, actionName, singleParam, contextTasks));
                    }
                } else {
                    then(new ActionTraverseOrAttribute(false, true, getName.trim()));//default action
                }
            }
        }
    }

    static Action loadAction(final ActionRegistry registry, final String actionName, final String[] params, final Map<Integer, Task> contextTasks) {
        final ActionDeclaration declaration = registry.declaration(actionName);
        if (declaration == null || declaration.factory() == null) {
            final String[] varargs = params;
            return new ActionNamed(actionName, varargs);
        } else {
            final ActionFactory factory = declaration.factory();
            final byte[] declaredParams = declaration.params();
            if (declaredParams != null && params != null) {
                int resultSize = declaredParams.length;
                Object[] parsedParams = new Object[resultSize];
                int varargs_index = 0;
                for (int i = 0; i < params.length; i++) {
                    byte correspondingType;
                    if (i < resultSize) {
                        correspondingType = declaredParams[i];
                    } else {
                        correspondingType = declaredParams[resultSize - 1]; // varargs
                    }
                    switch (correspondingType) {
                        case Type.STRING:
                            parsedParams[i] = params[i];
                            break;
                        case Type.LONG:
                            parsedParams[i] = Long.parseLong(params[i]);
                            break;
                        case Type.DOUBLE:
                            parsedParams[i] = Double.parseDouble(params[i]);
                            break;
                        case Type.TASK:
                            parsedParams[i] = getOrCreate(contextTasks, params[i]);
                            break;
                        case Type.STRING_ARRAY:
                            if (varargs_index == 0) {
                                final String[] parsedSubParam = new String[resultSize - i];
                                parsedSubParam[varargs_index] = params[i];
                                varargs_index = 1;
                                parsedParams[i] = parsedSubParam;
                            } else {
                                ((String[]) parsedParams[resultSize - 1])[varargs_index] = params[i];
                                varargs_index++;
                            }
                            break;
                        case Type.DOUBLE_ARRAY:
                            if (varargs_index == 0) {
                                final double[] parsedSubParam = new double[resultSize - i];
                                parsedSubParam[varargs_index] = Double.parseDouble(params[i]);
                                varargs_index = 1;
                                parsedParams[i] = parsedSubParam;
                            } else {
                                ((double[]) parsedParams[resultSize - 1])[varargs_index] = Double.parseDouble(params[i]);
                                varargs_index++;
                            }
                            break;
                        case Type.TASK_ARRAY:
                            if (varargs_index == 0) {
                                final Task[] parsedSubParamTask = new Task[resultSize - i];
                                parsedSubParamTask[varargs_index] = getOrCreate(contextTasks, params[i]);
                                varargs_index = 1;
                                parsedParams[i] = parsedSubParamTask;
                            } else {
                                ((Task[]) parsedParams[resultSize - 1])[varargs_index] = getOrCreate(contextTasks, params[i]);
                                varargs_index++;
                            }
                            break;
                    }
                }
                if (resultSize > params.length) {
                    for (int i = params.length; i < resultSize; i++) {
                        parsedParams[i] = null;
                    }
                }
                return factory.create(parsedParams);
            } else {
                return factory.create(new Object[0]);
            }
        }
    }

    private static ConditionalFunction condFromScript(final String script) {
        return new ConditionalFunction() {
            @Override
            public boolean eval(TaskContext ctx) {
                return executeScript(script, ctx);
            }
        };
    }

    /**
     * @native ts
     * var print = console.log;
     * var println = console.log;
     * var ctx = context;
     * return eval(script);
     */
    private static boolean executeScript(String script, TaskContext context) {
        ScriptContext scriptCtx = new SimpleScriptContext();
        scriptCtx.setAttribute("ctx", context, ScriptContext.ENGINE_SCOPE);
        try {
            return (boolean) TaskHelper.SCRIPT_ENGINE.eval(script, scriptCtx);
        } catch (ScriptException | ClassCastException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void fillDefault(ActionRegistry registry) {
        registry.declaration(CoreActionNames.TRAVEL_IN_WORLD)
                .setParams(Type.STRING)
                .setDescription("Sets the task context to a particular world. Every nodes in current result will be switch ot new world.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionTravelInWorld((String) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.TRAVEL_IN_TIME)
                .setParams(Type.STRING)
                .setDescription("Switches the time of the task context, i.e. travels the task context in time. Every nodes in current result will be switch ot new time.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionTravelInTime((String) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.DEFINE_AS_GLOBAL_VAR)
                .setParams(Type.STRING)
                .setDescription("Stores the task result as a global variable in the task context and starts a new scope (for sub tasks).")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionDefineAsVar((String) params[0], true);
                    }
                });
        registry.declaration(CoreActionNames.DEFINE_AS_VAR)
                .setParams(Type.STRING)
                .setDescription("Stores the task result as a local variable in the task context and starts a new scope (for sub tasks).")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionDefineAsVar((String) params[0], false);
                    }
                });
        registry.declaration(CoreActionNames.DECLARE_GLOBAL_VAR)
                .setParams(Type.STRING)
                .setDescription("Stores the task result as a global variable in the task context and starts a new scope (for sub tasks).")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionDeclareVar(true, (String) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.DECLARE_VAR)
                .setParams(Type.STRING)
                .setDescription("Stores the task result as a local variable in the task context and starts a new scope (for sub tasks).")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionDeclareVar(false, (String) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.READ_VAR)
                .setParams(Type.STRING)
                .setDescription("Retrieves a stored variable. To reach a particular index, a default array notation can be used. Therefore, A[B] will be interpreted as: extract value stored at index B from the variable A.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionReadVar((String) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.SET_AS_VAR)
                .setParams(Type.STRING)
                .setDescription("Stores the current task result into a named variable without starting a new scope.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionSetAsVar((String) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.ADD_TO_VAR)
                .setParams(Type.STRING)
                .setDescription("Adds the current task result to the named variable.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionAddToVar((String) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.TRAVERSE)
                .setParams(Type.STRING, Type.STRING_ARRAY)
                .setDescription("Retrieves any nodes contained in a relations of the nodes present in the current result.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        final String[] varrags = (String[]) params[1];
                        if (varrags != null) {
                            return new ActionTraverseOrAttribute(false, false, (String) params[0], varrags);
                        } else {
                            return new ActionTraverseOrAttribute(false, false, (String) params[0]);
                        }
                    }
                });
        registry.declaration(CoreActionNames.ATTRIBUTE)
                .setParams(Type.STRING)
                .setDescription("Retrieves any attribute(s) contained in the nodes present in the current result.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionTraverseOrAttribute(false, false, (String) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.WITH)
                .setParams(Type.STRING, Type.STRING)
                .setDescription("Filters the previous result to keep nodes, which named attribute has a specific value.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionWith((String) params[0], (String) params[1]);
                    }
                });
        registry.declaration(CoreActionNames.WITHOUT)
                .setParams(Type.STRING, Type.STRING)
                .setDescription("Filters the previous result to keep nodes, which named attribute does not have a given value.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionWithout((String) params[0], (String) params[1]);
                    }
                });
        registry.declaration(CoreActionNames.SCRIPT)
                .setParams(Type.STRING)
                .setDescription("Execute a JS script; Current context is automatically injected as ctx variables. Other variables are directly reachable as JS vars. Execution is synchronous")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionScript((String) params[0], false);
                    }
                });
        registry.declaration(CoreActionNames.ASYNC_SCRIPT)
                .setParams(Type.STRING)
                .setDescription("Execute a JS script; Current context is automatically injected as ctx variables. Other variables are directly reachable as JS vars. Execution is asynchronous and script must contains a ctx.continueTask(); or ctx.continueWith(newResult).")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionScript((String) params[0], true);
                    }
                });
        registry.declaration(CoreActionNames.CREATE_NODE)
                .setParams()
                .setDescription("Creates a new node in the [world,time] of the current context.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionCreateNode(null);
                    }
                });
        registry.declaration(CoreActionNames.CREATE_TYPED_NODE)
                .setParams(Type.STRING)
                .setDescription("Creates a new typed node in the [world,time] of the current context.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionCreateNode((String) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.PRINT)
                .setParams(Type.STRING)
                .setDescription("Prints the action in a human readable format (without line breaks).")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionPrint((String) params[0], false);
                    }
                });
        registry.declaration(CoreActionNames.PRINTLN)
                .setParams(Type.STRING)
                .setDescription("Prints the action in a human readable format (with line breaks).")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionPrint((String) params[0], true);
                    }
                });
        registry.declaration(CoreActionNames.ATTRIBUTES)
                .setParams()
                .setDescription("Retrieves all attribute names of nodes present in the previous task result.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionAttributes(null);
                    }
                });
        registry.declaration(CoreActionNames.ATTRIBUTES)
                .setParams()
                .setDescription("Retrieves all attribute names of nodes present in the previous task result.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionAttributes(null);
                    }
                });
        registry.declaration(CoreActionNames.ATTRIBUTES_WITH_TYPE)
                .setParams(Type.STRING)
                .setDescription("Gets and filters all attribute names of nodes present in the previous result.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionAttributes((String) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.FLAT)
                .setParams()
                .setDescription("Flat a TaskResult containing TaskResult to a flat TaskResult.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionFlat();
                    }
                });
        registry.declaration(CoreActionNames.EXECUTE_EXPRESSION)
                .setParams(Type.STRING)
                .setDescription("Executes an expression on all nodes given from the previous step.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionExecuteExpression((String) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.READ_GLOBAL_INDEX)
                .setParams(Type.STRING, Type.STRING_ARRAY)
                .setDescription("Retrieves indexed nodes matching the query.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        final String[] varargs = (String[]) params[1];
                        if (varargs != null) {
                            return new ActionReadGlobalIndex((String) params[0], varargs);
                        } else {
                            return new ActionReadGlobalIndex((String) params[0]);
                        }
                    }
                });
        registry.declaration(CoreActionNames.GLOBAL_INDEX)
                .setParams(Type.STRING)
                .setDescription("Retrieve global index node")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionGlobalIndex((String) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.SELECT)
                .setParams(Type.STRING)
                .setDescription("Use a JS script to filter nodes. The task context is inject in the variable 'context'. The current node is inject in the variable 'node'.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionSelect((String) params[0], null);
                    }
                });
        registry.declaration(CoreActionNames.TIME_SENSITIVITY)
                .setParams(Type.STRING, Type.STRING)
                .setDescription("Adjust the time sensitivity of nodes present in current result.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionTimeSensitivity((String) params[0], (String) params[1]);
                    }
                });
        registry.declaration(CoreActionNames.SET_ATTRIBUTE)
                .setParams(Type.STRING, Type.STRING, Type.STRING)
                .setDescription("Sets the value of an attribute for all nodes present in the current result. If value is similar to the previously stored one, nodes will remain unmodified.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionSetAttribute((String) params[0], (String) params[1], (String) params[2], true);
                    }
                });
        registry.declaration(CoreActionNames.FORCE_ATTRIBUTE)
                .setParams(Type.STRING, Type.STRING, Type.STRING)
                .setDescription("Forces the value of an attribute for all nodes present in the current result. If value is similar to the previously stored one, nodes will still be modified and their timeline will be affected.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new ActionSetAttribute((String) params[0], (String) params[1], (String) params[2], true);
                    }
                });
        registry.declaration(CoreActionNames.LOOP)
                .setParams(Type.STRING, Type.STRING, Type.TASK)
                .setDescription("Executes a task in a range.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new CF_Loop((String) params[0], (String) params[1], (Task) params[2]);
                    }
                });
        registry.declaration(CoreActionNames.LOOP_PAR)
                .setParams(Type.STRING, Type.STRING, Type.TASK)
                .setDescription("Parallel version of loop(String, String, Task). Executes a task in a range. Steps can be executed in parallel. Creates as many threads as elements in the collection.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new CF_LoopPar((String) params[0], (String) params[1], (Task) params[2]);
                    }
                });
        registry.declaration(CoreActionNames.FOR_EACH)
                .setParams(Type.TASK)
                .setDescription("Iterates through a collection and calls the sub task for each element.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new CF_ForEach((Task) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.FOR_EACH_PAR)
                .setParams(Type.TASK)
                .setDescription("Parallel version of forEach(Task). All sub tasks can be called in parallel. Creates as many threads as elements in the collection.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new CF_ForEachPar((Task) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.MAP)
                .setParams(Type.TASK)
                .setDescription("Iterates through a collection and calls the sub task for each element in parallel and then aggregates all results in an array of array manner.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new CF_Map((Task) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.MAP_PAR)
                .setParams(Type.TASK)
                .setDescription("Parallel version of map(Task). Iterates through a collection and calls the sub task for each element in parallel and then aggregates all results in an array of array manner.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new CF_MapPar((Task) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.PIPE)
                .setParams(Type.TASK_ARRAY)
                .setDescription("Executes and waits for a number of given sub tasks. The result of these sub tasks is immediately enqueued and available in the next sub task in a array of array manner.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        final Task[] varargs = (Task[]) params[0];
                        return new CF_Pipe(varargs);
                    }
                });
        registry.declaration(CoreActionNames.PIPE_PAR)
                .setParams(Type.TASK_ARRAY)
                .setDescription("Parallel version of pipe(Tasks...). Executes and waits a number of given sub tasks. The result of these sub tasks is immediately enqueued and available in the next sub task in a array of array manner.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        final Task[] varargs = (Task[]) params[0];
                        return new CF_PipePar(varargs);
                    }
                });
        registry.declaration(CoreActionNames.DO_WHILE)
                .setParams(Type.STRING, Type.TASK)
                .setDescription("Executes a give task until a given condition evaluates to true.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new CF_DoWhile((Task) params[1], condFromScript((String) params[0]), (String) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.WHILE_DO)
                .setParams(Type.STRING, Type.TASK)
                .setDescription("Similar to doWhile(Task, ConditionalExpression) but the task is at least executed once.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new CF_WhileDo(condFromScript((String) params[0]), (Task) params[1], (String) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.PIPE_TO)
                .setParams(Type.TASK, Type.STRING_ARRAY)
                .setDescription("Executes a given sub task in an isolated environment and store result as variables.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        final String[] varargs = (String[]) params[1];
                        if (varargs != null) {
                            return new CF_PipeTo((Task) params[0], varargs);
                        } else {
                            return new CF_PipeTo((Task) params[0]);
                        }
                    }
                });
        registry.declaration(CoreActionNames.ATOMIC)
                .setParams(Type.TASK, Type.STRING_ARRAY)
                .setDescription("Atomically execute a subTask while blocking on nodes present in named variables")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        final String[] varargs = (String[]) params[1];
                        if (varargs != null) {
                            return new CF_Atomic((Task) params[0], varargs);
                        } else {
                            return new CF_Atomic((Task) params[0]);
                        }
                    }
                });
        registry.declaration(CoreActionNames.IF_THEN)
                .setParams(Type.STRING, Type.TASK)
                .setDescription("Executes a sub task if a given condition is evaluated to true.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new CF_IfThen(condFromScript((String) params[0]), (Task) params[1], (String) params[0]);
                    }
                });
        registry.declaration(CoreActionNames.IF_THEN_ELSE)
                .setParams(Type.STRING, Type.TASK, Type.TASK)
                .setDescription("Executes a sub task if a given condition is evaluated to true, another one otherwise.")
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new CF_IfThenElse(condFromScript((String) params[0]), (Task) params[1], (Task) params[2], (String) params[0]);
                    }
                });
    }

    private static Task getOrCreate(Map<Integer, Task> contextTasks, String param) {
        Integer taskId = TaskHelper.parseInt(param);
        Task previous = contextTasks.get(taskId);
        if (previous == null) {
            previous = new CoreTask();
            contextTasks.put(taskId, previous);
        }
        return previous;
    }

    /**
     * @native ts
     * if(this['hashCodeCache'] === undefined){
     * this['hashCodeCache'] = Math.floor((Math.random() * 1000000000) + 1);
     * }
     * return this['hashCodeCache'];
     */
    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    @Override
    public final String toString() {
        final StringBuilder res = new StringBuilder();
        final Map<Integer, Integer> dagCounters = new HashMap<Integer, Integer>();
        final Map<Integer, Task> dagCollector = new HashMap<Integer, Task>();
        deep_analyze(this, dagCounters, dagCollector);

        Set<Integer> keys = dagCounters.keySet();
        Integer[] flatKeys = keys.toArray(new Integer[keys.size()]);
        final Map<Integer, Integer> dagIDS = new HashMap<Integer, Integer>();
        for (int i = 0; i < flatKeys.length; i++) {
            Integer key = flatKeys[i];
            Integer counter = dagCounters.get(key);
            if (counter != null && counter > 1) {
                dagIDS.put(key, dagIDS.size());
            }
        }
        serialize(res, dagIDS);

        Set<Integer> set_dagIDS = dagIDS.keySet();
        Integer[] flatDagIDS = set_dagIDS.toArray(new Integer[set_dagIDS.size()]);
        for (int i = 0; i < flatDagIDS.length; i++) {
            Integer key = flatDagIDS[i];
            Integer index = dagIDS.get(key);
            final CoreTask dagTask = (CoreTask) dagCollector.get(key);
            res.append(Constants.SUB_TASK_DECLR);
            res.append("" + index);
            res.append(Constants.SUB_TASK_OPEN);
            dagTask.serialize(res, dagIDS);
            res.append(Constants.SUB_TASK_CLOSE);

        }
        return res.toString();
    }

    public final void serialize(StringBuilder builder, Map<Integer, Integer> dagCounters) {
        for (int i = 0; i < insertCursor; i++) {
            if (i != 0) {
                builder.append(Constants.TASK_SEP);
            }
            if (actions[i] instanceof CF_Action) {
                ((CF_Action) actions[i]).cf_serialize(builder, dagCounters);
            } else {
                actions[i].serialize(builder);
            }
        }
    }

    private static void deep_analyze(CoreTask t, Map<Integer, Integer> counters, Map<Integer, Task> dagCollector) {
        Integer tHash = t.hashCode();
        Integer previous = counters.get(tHash);
        if (previous == null) {
            counters.put(tHash, 1);
            dagCollector.put(tHash, t);
            for (int i = 0; i < t.insertCursor; i++) {
                if (t.actions[i] instanceof CF_Action) {
                    Task[] children = ((CF_Action) t.actions[i]).children();
                    for (int j = 0; j < children.length; j++) {
                        deep_analyze((CoreTask) children[j], counters, dagCollector);
                    }
                }
            }
        } else {
            counters.put(tHash, previous + 1);
        }
    }

    @Override
    public final Task travelInWorld(final String world) {
        return then(CoreActions.travelInWorld(world));
    }

    @Override
    public final Task travelInTime(final String time) {
        return then(CoreActions.travelInTime(time));
    }

    @Override
    public final Task inject(final Object input) {
        return then(CoreActions.inject(input));
    }

    @Override
    public final Task defineAsGlobalVar(final String name) {
        return then(CoreActions.defineAsGlobalVar(name));
    }

    @Override
    public final Task defineAsVar(final String name) {
        return then(CoreActions.defineAsVar(name));
    }

    @Override
    public final Task declareGlobalVar(final String name) {
        return then(CoreActions.declareGlobalVar(name));
    }

    @Override
    public final Task declareVar(final String name) {
        return then(CoreActions.declareVar(name));
    }

    @Override
    public final Task readVar(final String name) {
        return then(CoreActions.readVar(name));
    }

    @Override
    public final Task setAsVar(final String name) {
        return then(CoreActions.setAsVar(name));
    }

    @Override
    public final Task addToVar(final String name) {
        return then(CoreActions.addToVar(name));
    }

    @Override
    public final Task setAttribute(final String name, final byte type, final String value) {
        return then(CoreActions.setAttribute(name, type, value));
    }

    @Override
    public final Task timeSensitivity(final String delta, final String offset) {
        return then(CoreActions.timeSensitivity(delta, offset));
    }

    @Override
    public final Task forceAttribute(final String name, final byte type, final String value) {
        return then(CoreActions.forceAttribute(name, type, value));
    }

    @Override
    public final Task remove(final String name) {
        return then(CoreActions.remove(name));
    }

    @Override
    public final Task attributes() {
        return then(CoreActions.attributes());
    }

    @Override
    public Task timepoints(String from, String to) {
        return then(CoreActions.timepoints(from, to));
    }

    @Override
    public Task attributesWithType(byte filterType) {
        return then(CoreActions.attributesWithTypes(filterType));
    }

    @Override
    public final Task addVarToRelation(final String relName, final String varName, final String... attributes) {
        return then(CoreActions.addVarToRelation(relName, varName, attributes));
    }

    @Override
    public final Task removeVarFromRelation(final String relName, final String varFrom, final String... attributes) {
        return then(CoreActions.removeVarFromRelation(relName, varFrom, attributes));
    }

    @Override
    public final Task traverse(final String name, final String... params) {
        return then(CoreActions.traverse(name, params));
    }

    @Override
    public final Task attribute(final String name, final String... params) {
        return then(CoreActions.attribute(name, params));
    }

    @Override
    public final Task readGlobalIndex(final String name, final String... query) {
        return then(CoreActions.readGlobalIndex(name, query));
    }

    @Override
    public Task globalIndex(String indexName) {
        return then(CoreActions.globalIndex(indexName));
    }

    @Override
    public final Task addToGlobalIndex(final String name, final String... attributes) {
        return then(CoreActions.addToGlobalIndex(name, attributes));
    }

    @Override
    public final Task addToGlobalTimedIndex(final String name, final String... attributes) {
        return then(CoreActions.addToGlobalTimedIndex(name, attributes));
    }

    @Override
    public final Task removeFromGlobalIndex(final String name, final String... attributes) {
        return then(CoreActions.removeFromGlobalIndex(name, attributes));
    }

    @Override
    public final Task removeFromGlobalTimedIndex(final String name, final String... attributes) {
        return then(CoreActions.removeFromGlobalTimedIndex(name, attributes));
    }

    @Override
    public final Task indexNames() {
        return then(CoreActions.indexNames());
    }

    @Override
    public final Task selectWith(final String name, final String pattern) {
        return then(CoreActions.selectWith(name, pattern));
    }

    @Override
    public final Task selectWithout(final String name, final String pattern) {
        return then(CoreActions.selectWithout(name, pattern));
    }

    @Override
    public final Task select(final TaskFunctionSelect filterFunction) {
        return then(CoreActions.select(filterFunction));
    }

    @Override
    public final Task selectObject(final TaskFunctionSelectObject filterFunction) {
        return then(CoreActions.selectObject(filterFunction));
    }

    @Override
    public final Task selectScript(final String script) {
        return then(CoreActions.selectScript(script));
    }

    @Override
    public final Task print(final String name) {
        return then(CoreActions.print(name));
    }

    @Override
    public final Task println(final String name) {
        return then(CoreActions.println(name));
    }

    @Override
    public final Task executeExpression(final String expression) {
        return then(CoreActions.executeExpression(expression));
    }

    @Override
    public final Task createNode() {
        return then(CoreActions.createNode());
    }

    @Override
    public final Task createTypedNode(final String type) {
        return then(CoreActions.createTypedNode(type));
    }

    @Override
    public final Task save() {
        return then(CoreActions.save());
    }

    @Override
    public final Task script(final String script) {
        return then(CoreActions.script(script));
    }

    @Override
    public final Task asyncScript(final String ascript) {
        return then(CoreActions.asyncScript(ascript));
    }

    @Override
    public final Task lookup(final String nodeId) {
        return then(CoreActions.lookup(nodeId));
    }

    @Override
    public final Task lookupAll(final String nodeIds) {
        return then(CoreActions.lookupAll(nodeIds));
    }

    @Override
    public final Task clearResult() {
        return then(CoreActions.clearResult());
    }

    @Override
    public final Task action(final String name, final String... params) {
        return then(CoreActions.action(name, params));
    }

    @Override
    public final Task flipVar(final String name) {
        return then(CoreActions.flipVar(name));
    }

    @Override
    public final Task flat() {
        return then(CoreActions.flat());
    }

}
