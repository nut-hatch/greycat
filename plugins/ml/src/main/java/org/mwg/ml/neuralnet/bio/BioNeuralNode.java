package org.mwg.ml.neuralnet.bio;

import org.mwg.Graph;
import org.mwg.Type;
import org.mwg.ml.common.matrix.MatrixOps;
import org.mwg.plugin.AbstractNode;
import org.mwg.struct.LongLongMap;
import org.mwg.struct.Matrix;

import java.util.Random;

class BioNeuralNode extends AbstractNode {

    public static String NAME = "BioNeuralNode";

    public BioNeuralNode(long p_world, long p_time, long p_id, Graph p_graph) {
        super(p_world, p_time, p_id, p_graph);
    }

    @Override
    public void init() {
        setProperty(BioNeuralNetwork.BIAS, Type.DOUBLE, new Random().nextDouble() * 2 - 1);
    }

    //if buffer not full and total threshold < return absolute zero
    @SuppressWarnings("Duplicates")
    double learn(long sender, double value, int spikeLimit, double threshold) {
        //TODO atomic
        final Matrix spikeSum = (Matrix) get(BioNeuralNetwork.BUFFER_SPIKE_SUM);
        final Matrix spikeNb = (Matrix) get(BioNeuralNetwork.BUFFER_SPIKE_NB);
        final Matrix weights = (Matrix) get(BioNeuralNetwork.WEIGHTS);
        final LongLongMap reverse = (LongLongMap) get(BioNeuralNetwork.RELATION_INPUTS);
        final int senderIndex = (int) reverse.get(sender);
        //update neural content
        spikeSum.add(0, senderIndex, value);
        spikeNb.add(0, senderIndex, 1);
        //integrate all values
        double signal = MatrixOps.multiply(spikeSum, weights).get(0, 0);
        double bias = (double) get(BioNeuralNetwork.BIAS);
        double sigmoid = 1 / (1 + Math.exp(-(signal + bias)));
        //test if one spike limit is reached
        int spikeSumTot = 0;
        for (int i = 0; i < spikeNb.columns(); i++) {
            int loopSpikeNb = (int) spikeNb.get(0, i);
            if (loopSpikeNb >= spikeLimit) {
                spikeSum.fill(0d);
                spikeNb.fill(0d);
                return sigmoid;
            }
            spikeSumTot = spikeSumTot + loopSpikeNb;
        }
        //activate capacitor effect
        if ((spikeSumTot >= spikeSum.columns()) && (sigmoid > threshold)) {
            spikeSum.fill(0d);
            spikeNb.fill(0d);
            return sigmoid;
        }
        return 0d;
    }

}