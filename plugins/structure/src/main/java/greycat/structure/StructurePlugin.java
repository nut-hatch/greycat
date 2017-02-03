package greycat.structure;

import greycat.Graph;
import greycat.Node;
import greycat.Type;
import greycat.plugin.ActionFactory;
import greycat.plugin.NodeFactory;
import greycat.plugin.Plugin;
import greycat.structure.action.NearestNWithinRadius;
import greycat.structure.trees.KDTree;
import greycat.structure.trees.NDTree;
import greycat.task.Action;

public class StructurePlugin implements Plugin {

    @Override
    public void start(Graph graph) {
        graph.actionRegistry()
                .declaration(NearestNWithinRadius.NAME)
                .setParams(Type.INT, Type.DOUBLE, Type.DOUBLE_ARRAY)
                .setFactory(new ActionFactory() {
                    @Override
                    public Action create(Object[] params) {
                        return new NearestNWithinRadius((int) params[0], (double) params[1], (double[]) params[2], (boolean) params[3]);
                    }
                });
        graph.nodeRegistry().declaration(KDTree.NAME).setFactory(new NodeFactory() {
            @Override
            public Node create(long world, long time, long id, Graph graph) {
                return new KDTree(world, time, id, graph);
            }
        });
        graph.nodeRegistry().declaration(NDTree.NAME).setFactory(new NodeFactory() {
            @Override
            public Node create(long world, long time, long id, Graph graph) {
                return new NDTree(world, time, id, graph);
            }
        });
    }

    @Override
    public void stop() {

    }


}