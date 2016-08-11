package ml.regression;

import org.junit.Test;
import org.mwg.*;
import org.mwg.core.scheduler.NoopScheduler;
import org.mwg.ml.AbstractMLNode;
import org.mwg.mlx.MLXPlugin;
import org.mwg.mlx.algorithm.regression.AutoregressionBasedPeriodicityDetector;

import static org.junit.Assert.assertEquals;

/**
 * Created by andrey.boytsov on 09/08/16.
 */
public class AutoregressionBasedPeriodicityDetectorTest {
    double consumptionLondon[] = new double[]{64, 78, 96, 93, 92, 124, 87, 44, 25, 48, 87, 119, 78, 58, 60, 124, 279, 625, 115, 83, 1018, 1382, 950, 1266, 950, 2022,
            1181, 779, 245, 354, 192, 168, 137, 147, 1123, 915, 270, 861, 889, 258, 195, 185, 206, 194, 287, 155, 169, 92, 87, 56, 76, 42, 20, 58, 90, 67, 83, 45, 17,
            25, 93, 80, 71, 180, 116, 486, 202, 153, 116, 69, 63, 74, 129, 265, 103, 183, 126, 126, 85, 122, 68, 35, 30, 240, 1304, 741, 283, 283, 270, 271, 250, 228, 327,
            208, 188, 116, 87, 95, 89, 38, 18, 29, 59, 110, 102, 39, 18, 27, 56, 88, 93, 38, 33, 233, 432, 188, 111, 71, 83, 60, 172, 128, 269, 61, 64, 95, 65, 90, 92,
            166, 160, 343, 1087, 868, 671, 211, 306, 398, 638, 289, 312, 695, 583, 167, 107, 19, 22, 57, 55, 93, 104, 44, 18, 47, 54, 83, 76, 41, 29, 61, 280, 562, 181,
            521, 303, 272, 503, 530, 188, 118, 186, 131, 133, 133, 105, 309, 206, 183, 156, 172, 1237, 613, 200, 229, 364, 368, 233, 231, 346, 195, 68, 34, 58, 55, 56,
            101, 83, 39, 44, 57, 54, 53, 104, 44, 24, 57, 79, 53, 150, 161, 734, 179, 163, 690, 565, 182, 166, 199, 193, 169, 176, 144, 99, 125, 288, 125, 161, 299, 1173,
            606, 187, 239, 261, 190, 163, 199, 341, 203, 120, 66, 24, 17, 46, 98, 89, 78, 28, 18, 44, 86, 85, 76, 18, 18, 71, 239, 207, 451, 103, 94, 176, 111, 91, 237,
            229, 237, 563, 273, 567, 116, 149, 164, 278, 164, 280, 156, 165, 140, 142, 149, 196, 164, 160, 104, 125, 144, 166, 162, 126, 36, 21, 49, 56, 92, 105, 56, 19,
            32, 57, 86, 91, 52, 19, 38, 70, 321, 277, 468, 282, 595, 329, 123, 223, 74, 151, 179, 88, 58, 77, 79, 56, 30, 141, 123, 787, 1311, 490, 211, 170, 193, 247,
            282, 346, 168, 109, 60};

    double correctAutoregressionCoefs[] = new double[]{1, 0.63183242007981899, 0.39742449381945105,0.331997656566068, 0.30378943172955392, 0.1991113010522407,
            0.097581654794358916, 0.045533430281026639, 0.083018277317790268, 0.087386966400881672, 0.034402746271035768, -0.00029518711781846595,
            0.0074485585105999634, 0.0056780069789143109, 0.0068334939519373201, -0.033558982098773595, -0.070836633930822068, -0.032967530673629883,
            -0.025148575317837017, -0.042836140064440774, -0.090670401019243799, -0.11970960544219468, -0.13646547197320819, -0.1495842429490587,
            -0.15870448521423589, -0.14795614902435258, -0.14828943125385871, -0.128029346008007,  -0.10071667813362851, -0.069156266458362259,
            -0.078309569359931289, -0.12176767621469772, -0.1465473272566237, -0.12447895694687711, -0.13659846156440067, -0.14084597005912855,
            -0.13740325228235914, -0.10808959751812197, -0.027534994639553102, 0.0059995895542790025, 0.021311594756000161, -0.0014226189750919088,
            0.023858483414347015, 0.055737585918404546, 0.064511731166736336, 0.037495007291768911, 0.11558114068787789, 0.21985445903779025,
            0.26562236159038988, 0.20528447661150737, 0.16522182774646599, 0.071616514134571749, 0.061857151017235448, 0.053915942457553358,
            0.012479506412149297, -0.024474191241125074, -0.021089183299394795, 0.019044102960473586, 0.052214388235170328, 0.050081339305815091,
            -0.012720743199959991, -0.023827700666297406, -0.026298225425204543, -0.013170469409636969, -0.016341780197346559, -0.062322128748259265,
            -0.065300673447362304, -0.014036516772721041, -0.022862755422364905, -0.055710951932155417, -0.10073242490660658, -0.10612509068526661,
            -0.10229524665570337, -0.12920867232792735, -0.15142612737883351, -0.12219655050172716, -0.066050633065199857, -0.054507977113944554,
            -0.094528907580530688, -0.089047988190937566, -0.099721456312321113, -0.094861596808538187, -0.11234226458051531, -0.14838158231993426,
            -0.17563542783305083, -0.15596742772512934, -0.087868438618896857, -0.048363665145052227, 0.001178757679768337, -0.039216284614529591,
            -0.014789222244044106, 0.0076161375561863134, 0.0099217424896611318, 0.037156685792452265, 0.11533246796024282, 0.20253201217163969,
            0.32214665089311939, 0.24861592375061903, 0.11904617838213374, 0.088958979618708509, 0.057119327628260642, 0.0067331644253282924,
            -0.021695040365967581, -0.0045280508233851062, 0.017712681743116659, -0.002150204580516301, 0.0087601892601187857, 0.090832667423680513,
            0.044310536601389443, 0.006059237618125061, 0.0026087033483833161, 0.081919209289736006, 0.089470729289027565, 0.03434829119259776,
            0.016956194966511254, 0.091467630033587405, 0.061948737988001636, 0.021427617612846121, -0.042785798789719255, -0.077900051212460783,
            -0.10424779508071984, -0.094639726045507119, -0.11952314674532095, -0.11373925694213616, -0.090285628034750223, -0.051377095653626179,
            -0.028575485603981116, -0.086542513058216863, -0.098152411631125425, -0.031214079434904569, -0.080328631870448503, -0.14190432301604852,
            -0.11699846579926573, -0.11306414931758386, -0.065501927487875172, -0.033824115486886706, 0.019074189208942095, 0.01396121552535831,
            0.058235029509210987, 0.080076390246017537, 0.15935421488194948, 0.16821500231568684, 0.21071426876783464, 0.26907617895116753};

    protected  class RegressionJumpCallback{
        //TODO redevelop accordingly

        final String features[];

        public RegressionJumpCallback(String featureNames[]){
            this.features = featureNames;
        }

        public double value[];

        public int periods[][] =  new int[0][0];

        Callback<int[][]> cb = new Callback<int[][]>() {
            @Override
            public void on(int[][] result) {
                periods = result;
            }
        };

        public void on(AutoregressionBasedPeriodicityDetector result) {
            for (int i=0;i<features.length;i++){
                result.set(features[i], value[i]);
            }
            result.learn(cb);

            result.free();
        }
    };

    protected RegressionJumpCallback runThroughLondonConsumptionData(AutoregressionBasedPeriodicityDetector lrNode, boolean swapResponse, double periods[], double sinPhaseShifts[], double amplitudes[]) {
        RegressionJumpCallback rjc = new RegressionJumpCallback(new String[]{AbstractLinearRegressionTest.FEATURE});
        for (int i = 0; i < consumptionLondon.length; i++) {
            //assertTrue(rjc.bootstrapMode);
            rjc.value = new double[]{consumptionLondon[i]};
            lrNode.jump(i, new Callback<Node>() {
                @Override
                public void on(Node result) {
                    rjc.on((AutoregressionBasedPeriodicityDetector) result);
                }
            });
        }
        //assertFalse(rjc.bootstrapMode);
        return rjc;
    }


    @Test
    public void testLondonconsumptionAutoregression() {
        final Graph graph = new GraphBuilder().withPlugin(new MLXPlugin()).withScheduler(new NoopScheduler()).build();
        graph.connect(new Callback<Boolean>() {
            @Override
            public void on(Boolean result) {
                AutoregressionBasedPeriodicityDetector lrNode = (AutoregressionBasedPeriodicityDetector) graph.newTypedNode(0, 0, AutoregressionBasedPeriodicityDetector.NAME);
                lrNode.setProperty(AutoregressionBasedPeriodicityDetector.BUFFER_SIZE_KEY, Type.INT, consumptionLondon.length);
                lrNode.setProperty(AutoregressionBasedPeriodicityDetector.AUTOREGRESSION_LAG_KEY, Type.INT, 48*3);
                lrNode.set(AbstractMLNode.FROM, "f1");

                RegressionJumpCallback rjc = runThroughLondonConsumptionData(lrNode, false, new double[]{5.0}, new double[]{0.0}, new double[]{10.0});
                lrNode.free();
                graph.disconnect(null);

                assertEquals(1, rjc.periods.length);
                assertEquals(4, rjc.periods[0].length);
                assertEquals(1, rjc.periods[0][0]);
                assertEquals(48, rjc.periods[0][1]);
                assertEquals(96, rjc.periods[0][2]);
                assertEquals(143, rjc.periods[0][3]);
            }
        });

    }

}
