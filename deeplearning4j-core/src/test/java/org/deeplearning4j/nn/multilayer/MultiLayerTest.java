/*-
 *
 *  * Copyright 2015 Skymind,Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package org.deeplearning4j.nn.multilayer;

import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.datasets.iterator.impl.IrisDataSetIterator;
import org.deeplearning4j.datasets.iterator.impl.MnistDataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.exception.DL4JException;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.*;
import org.deeplearning4j.nn.conf.distribution.NormalDistribution;
import org.deeplearning4j.nn.conf.distribution.UniformDistribution;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.conf.preprocessor.CnnToFeedForwardPreProcessor;
import org.deeplearning4j.nn.conf.preprocessor.FeedForwardToRnnPreProcessor;
import org.deeplearning4j.nn.conf.preprocessor.RnnToCnnPreProcessor;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.layers.BaseOutputLayer;
import org.deeplearning4j.nn.params.DefaultParamInitializer;
import org.deeplearning4j.nn.params.PretrainParamInitializer;
import org.deeplearning4j.nn.transferlearning.TransferLearning;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.junit.Ignore;
import org.junit.Test;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.SplitTestAndTrain;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.heartbeat.Heartbeat;
import org.nd4j.linalg.heartbeat.reports.Environment;
import org.nd4j.linalg.heartbeat.reports.Event;
import org.nd4j.linalg.heartbeat.reports.Task;
import org.nd4j.linalg.heartbeat.utils.EnvironmentUtils;
import org.nd4j.linalg.heartbeat.utils.TaskUtils;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by agibsonccc on 12/27/14.
 */
public class MultiLayerTest {

    private static final Logger log = LoggerFactory.getLogger(MultiLayerTest.class);

    @Test
    public void testSetParams() {
        Nd4j.MAX_ELEMENTS_PER_SLICE = Integer.MAX_VALUE;
        Nd4j.MAX_SLICES_TO_PRINT = Integer.MAX_VALUE;

        MultiLayerConfiguration conf =
                        new NeuralNetConfiguration.Builder()
                                        .list().layer(0,
                                                        new RBM.Builder(RBM.HiddenUnit.RECTIFIED,
                                                                        RBM.VisibleUnit.GAUSSIAN).nIn(4).nOut(3)
                                                                                        .activation(Activation.TANH)
                                                                                        .build())
                                        .layer(1, new RBM.Builder(RBM.HiddenUnit.GAUSSIAN, RBM.VisibleUnit.GAUSSIAN)
                                                        .nIn(3).nOut(2).build())
                                        .build();

        MultiLayerNetwork network3 = new MultiLayerNetwork(conf);
        network3.init();

        INDArray params = network3.params();
        INDArray weights = network3.getLayer(0).getParam(DefaultParamInitializer.WEIGHT_KEY).dup();
        INDArray bias = network3.getLayer(0).getParam(DefaultParamInitializer.BIAS_KEY).dup();
        network3.setParameters(params);
        assertEquals(weights, network3.getLayer(0).getParam(DefaultParamInitializer.WEIGHT_KEY));
        assertEquals(bias, network3.getLayer(0).getParam(DefaultParamInitializer.BIAS_KEY));
        INDArray params4 = network3.params();
        assertEquals(params, params4);
    }

    @Test
    public void testBatchNorm() {
        Nd4j.getRandom().setSeed(123);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                        .optimizationAlgo(OptimizationAlgorithm.LINE_GRADIENT_DESCENT).iterations(5).seed(123).list()
                        .layer(0, new DenseLayer.Builder().nIn(4).nOut(3).weightInit(WeightInit.XAVIER)
                                        .activation(Activation.TANH).build())
                        .layer(1, new DenseLayer.Builder().nIn(3).nOut(2).weightInit(WeightInit.XAVIER)
                                        .activation(Activation.TANH).build())
                        .layer(2, new BatchNormalization.Builder().nOut(2).build())
                        .layer(3, new org.deeplearning4j.nn.conf.layers.OutputLayer.Builder(
                                        LossFunctions.LossFunction.MCXENT).weightInit(WeightInit.XAVIER)
                                                        .activation(Activation.SOFTMAX).nIn(2).nOut(3).build())
                        .backprop(true).pretrain(false).build();


        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.init();
        network.setListeners(new ScoreIterationListener(1));

        DataSetIterator iter = new IrisDataSetIterator(150, 150);

        DataSet next = iter.next();
        next.normalizeZeroMeanZeroUnitVariance();
        SplitTestAndTrain trainTest = next.splitTestAndTrain(110);
        network.setLabels(trainTest.getTrain().getLabels());
        network.init();
        network.fit(trainTest.getTrain());

    }

    @Test
    public void testBackProp() {
        Nd4j.getRandom().setSeed(123);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                        .optimizationAlgo(OptimizationAlgorithm.LINE_GRADIENT_DESCENT).iterations(5).seed(123).list()
                        .layer(0, new DenseLayer.Builder().nIn(4).nOut(3).weightInit(WeightInit.XAVIER)
                                        .activation(Activation.TANH).build())
                        .layer(1, new DenseLayer.Builder().nIn(3).nOut(2).weightInit(WeightInit.XAVIER)
                                        .activation(Activation.TANH).build())
                        .layer(2, new org.deeplearning4j.nn.conf.layers.OutputLayer.Builder(
                                        LossFunctions.LossFunction.MCXENT).weightInit(WeightInit.XAVIER)
                                                        .activation(Activation.SOFTMAX).nIn(2).nOut(3).build())
                        .backprop(true).pretrain(false).build();


        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.init();
        network.setListeners(new ScoreIterationListener(1));

        DataSetIterator iter = new IrisDataSetIterator(150, 150);

        DataSet next = iter.next();
        next.normalizeZeroMeanZeroUnitVariance();
        SplitTestAndTrain trainTest = next.splitTestAndTrain(110);
        network.setInput(trainTest.getTrain().getFeatureMatrix());
        network.setLabels(trainTest.getTrain().getLabels());
        network.init();
        network.fit(trainTest.getTrain());

        DataSet test = trainTest.getTest();
        Evaluation eval = new Evaluation();
        INDArray output = network.output(test.getFeatureMatrix());
        eval.eval(test.getLabels(), output);
        log.info("Score " + eval.stats());
    }

    @Test
    public void testDbn() throws Exception {
        Nd4j.MAX_SLICES_TO_PRINT = -1;
        Nd4j.MAX_ELEMENTS_PER_SLICE = -1;
        MultiLayerConfiguration conf =
                        new NeuralNetConfiguration.Builder().iterations(100).momentum(0.9)
                                        .optimizationAlgo(OptimizationAlgorithm.LBFGS).regularization(true).l2(2e-4)
                                        .list().layer(0,
                                                        new RBM.Builder(RBM.HiddenUnit.GAUSSIAN,
                                                                        RBM.VisibleUnit.GAUSSIAN).nIn(4).nOut(3)
                                                                                        .weightInit(WeightInit.DISTRIBUTION)
                                                                                        .dist(new UniformDistribution(0,
                                                                                                        1))
                                                                                        .activation(Activation.TANH)
                                                                                        .lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE)
                                                                                        .build())
                                        .layer(1, new org.deeplearning4j.nn.conf.layers.OutputLayer.Builder(
                                                        LossFunctions.LossFunction.MCXENT).nIn(3).nOut(3)
                                                                        .weightInit(WeightInit.DISTRIBUTION)
                                                                        .dist(new UniformDistribution(0, 1))
                                                                        .activation(Activation.SOFTMAX).build())
                                        .build();


        MultiLayerNetwork d = new MultiLayerNetwork(conf);

        DataSetIterator iter = new IrisDataSetIterator(150, 150);

        DataSet next = iter.next();

        Nd4j.writeTxt(next.getFeatureMatrix(), "iris.txt", "\t");

        next.normalizeZeroMeanZeroUnitVariance();

        SplitTestAndTrain testAndTrain = next.splitTestAndTrain(110);
        DataSet train = testAndTrain.getTrain();

        d.fit(train);

        DataSet test = testAndTrain.getTest();

        Evaluation eval = new Evaluation();
        INDArray output = d.output(test.getFeatureMatrix());
        eval.eval(test.getLabels(), output);
        log.info("Score " + eval.stats());

    }



    @Test
    public void testGradientWithAsList() {
        MultiLayerNetwork net1 = new MultiLayerNetwork(getConf());
        MultiLayerNetwork net2 = new MultiLayerNetwork(getConf());
        net1.init();
        net2.init();

        DataSet x1 = new IrisDataSetIterator(1, 150).next();
        DataSet all = new IrisDataSetIterator(150, 150).next();
        DataSet x2 = all.asList().get(0);

        //x1 and x2 contain identical data
        assertArrayEquals(asFloat(x1.getFeatureMatrix()), asFloat(x2.getFeatureMatrix()), 0.0f);
        assertArrayEquals(asFloat(x1.getLabels()), asFloat(x2.getLabels()), 0.0f);
        assertEquals(x1, x2);

        //Set inputs/outputs so gradient can be calculated:
        net1.feedForward(x1.getFeatureMatrix());
        net2.feedForward(x2.getFeatureMatrix());
        ((BaseOutputLayer) net1.getLayer(1)).setLabels(x1.getLabels());
        ((BaseOutputLayer) net2.getLayer(1)).setLabels(x2.getLabels());

        net1.gradient();
        net2.gradient();
    }

    /**
     *  This test intended only to test activateSelectedLayers method, it does not involves fully-working AutoEncoder.
     */
    @Test
    public void testSelectedActivations() {
        // Train DeepAutoEncoder on very limited trainset
        final int numRows = 28;
        final int numColumns = 28;
        int seed = 123;
        int numSamples = 3;
        int iterations = 1;
        int listenerFreq = iterations / 5;

        log.info("Load data....");

        float[][] trainingData = new float[numSamples][numColumns * numRows];
        Arrays.fill(trainingData[0], 0.95f);
        Arrays.fill(trainingData[1], 0.5f);
        Arrays.fill(trainingData[2], 0.05f);



        log.info("Build model....");
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(seed).iterations(iterations)
                        .optimizationAlgo(OptimizationAlgorithm.LINE_GRADIENT_DESCENT).list()
                        .layer(0, new RBM.Builder().nIn(numRows * numColumns).nOut(1000)
                                        .lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).build())
                        .layer(1, new RBM.Builder().nIn(1000).nOut(500)
                                        .lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).build())
                        .layer(2, new RBM.Builder().nIn(500).nOut(250)
                                        .lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).build())
                        .layer(3, new RBM.Builder().nIn(250).nOut(100)
                                        .lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).build())
                        .layer(4, new RBM.Builder().nIn(100).nOut(30)
                                        .lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).build()) //encoding stops
                        .layer(5, new RBM.Builder().nIn(30).nOut(100)
                                        .lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).build()) //decoding starts
                        .layer(6, new RBM.Builder().nIn(100).nOut(250)
                                        .lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).build())
                        .layer(7, new RBM.Builder().nIn(250).nOut(500)
                                        .lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).build())
                        .layer(8, new RBM.Builder().nIn(500).nOut(1000)
                                        .lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).build())
                        .layer(9, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).nIn(1000)
                                        .nOut(numRows * numColumns).build())
                        .pretrain(true).backprop(true).build();

        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();

        model.setListeners(Arrays.asList((IterationListener) new ScoreIterationListener(listenerFreq)));

        log.info("Train model....");
        int cnt = 0;
        while (cnt < numSamples) {
            INDArray input = Nd4j.create(trainingData[cnt]);
            model.fit(new DataSet(input, input));
            cnt++;
        }
        // Make two separate selective calls

        log.info("Testing full cycle...");

        List<INDArray> comparableResult = model.feedForward(Nd4j.create(trainingData[0]));

        INDArray encodeResult = model.activateSelectedLayers(0, 4, Nd4j.create(trainingData[0]));

        log.info("Compare feedForward results with selectedActivation");

        assertEquals(comparableResult.get(5), encodeResult);

        INDArray decodeResults = model.activateSelectedLayers(5, 9, encodeResult);


        log.info("Decode results: " + decodeResults.columns() + " " + decodeResults);
        log.info("Comparable  results: " + comparableResult.get(10).columns() + " " + comparableResult.get(10));

        assertEquals(comparableResult.get(10), decodeResults);
    }

    private static MultiLayerConfiguration getConf() {
        MultiLayerConfiguration conf =
                        new NeuralNetConfiguration.Builder().seed(12345L)
                                        .list().layer(0,
                                                        new RBM.Builder(RBM.HiddenUnit.RECTIFIED,
                                                                        RBM.VisibleUnit.GAUSSIAN).nIn(4).nOut(3)
                                                                                        .weightInit(WeightInit.DISTRIBUTION)
                                                                                        .dist(new NormalDistribution(0,
                                                                                                        1))
                                                                                        .build())
                                        .layer(1, new org.deeplearning4j.nn.conf.layers.OutputLayer.Builder(
                                                        LossFunctions.LossFunction.MCXENT)
                                                                        .activation(Activation.SOFTMAX).nIn(3).nOut(3)
                                                                        .weightInit(WeightInit.DISTRIBUTION)
                                                                        .dist(new NormalDistribution(0, 1)).build())
                                        .build();
        return conf;
    }

    public static float[] asFloat(INDArray arr) {
        int len = arr.length();
        float[] f = new float[len];
        for (int i = 0; i < len; i++)
            f[i] = arr.getFloat(i);
        return f;
    }

    @Test
    public void testFeedForwardToLayer() {

        int nIn = 30;
        int nOut = 25;

        MultiLayerConfiguration conf =
                        new NeuralNetConfiguration.Builder().optimizationAlgo(OptimizationAlgorithm.CONJUGATE_GRADIENT)
                                        .iterations(5).learningRate(1e-3)
                                        .list().layer(
                                                        0, new RBM.Builder(RBM.HiddenUnit.RECTIFIED,
                                                                        RBM.VisibleUnit.GAUSSIAN).nIn(nIn)
                                                                                        .nOut(600)
                                                                                        .weightInit(WeightInit.DISTRIBUTION)
                                                                                        .dist(new NormalDistribution(0,
                                                                                                        1e-5))
                                                                                        .lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                                                                                        .build())
                                        .layer(1, new RBM.Builder(RBM.HiddenUnit.RECTIFIED, RBM.VisibleUnit.GAUSSIAN)
                                                        .nIn(600).nOut(250).weightInit(WeightInit.DISTRIBUTION)
                                                        .dist(new NormalDistribution(0, 1e-5))
                                                        .lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                                                        .build())
                                        .layer(2, new RBM.Builder(RBM.HiddenUnit.RECTIFIED, RBM.VisibleUnit.GAUSSIAN)
                                                        .nIn(250).nOut(100).weightInit(WeightInit.DISTRIBUTION)
                                                        .dist(new NormalDistribution(0, 1e-5))
                                                        .lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                                                        .build())
                                        .layer(3, new org.deeplearning4j.nn.conf.layers.OutputLayer.Builder(
                                                        LossFunctions.LossFunction.MCXENT).nIn(100).nOut(25)
                                                                        .weightInit(WeightInit.DISTRIBUTION)
                                                                        .dist(new NormalDistribution(0, 1e-5)).build())
                                        .build();

        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.init();


        INDArray input = Nd4j.rand(5, nIn);

        List<INDArray> activations = network.feedForward(input);
        assertEquals(5, activations.size()); //4 layers + input

        List<INDArray> activationsAll = network.feedForwardToLayer(3, input);
        assertEquals(activations, activationsAll);

        for (int i = 3; i >= 0; i--) {
            List<INDArray> activationsPartial = network.feedForwardToLayer(i, input);
            assertEquals(i + 2, activationsPartial.size()); //i+2: for layer 3: input + activations of {0,1,2,3} -> 5 total = 3+2
            for (int j = 0; j <= i; j++) {
                INDArray exp = activationsAll.get(j);
                INDArray act = activationsPartial.get(j);
                assertEquals(exp, act);
            }
        }
    }


    @Test
    public void testBackpropGradient() {
        //Testing: MultiLayerNetwork.backpropGradient()
        //i.e., specifically without an output layer

        int nIn = 10;
        int nOut = 40;
        int miniBatch = 5;

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                        .updater(org.deeplearning4j.nn.conf.Updater.SGD).learningRate(0.1).list()
                        .layer(0, new DenseLayer.Builder().nIn(nIn).nOut(20).activation(Activation.RELU)
                                        .weightInit(WeightInit.XAVIER).build())
                        .layer(1, new DenseLayer.Builder().nIn(20).nOut(30).activation(Activation.RELU)
                                        .weightInit(WeightInit.XAVIER).build())
                        .layer(2, new DenseLayer.Builder().nIn(30).nOut(nOut).activation(Activation.RELU)
                                        .weightInit(WeightInit.XAVIER).build())
                        .build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        Nd4j.getRandom().setSeed(12345);
        INDArray eps = Nd4j.rand(miniBatch, nOut);
        INDArray input = Nd4j.rand(miniBatch, nIn);

        net.feedForward(input); //Need to feed forward before backprop

        Pair<Gradient, INDArray> pair = net.backpropGradient(eps);
        INDArray epsOut = pair.getSecond();
        assertNotNull(epsOut);
        assertArrayEquals(new int[] {miniBatch, nIn}, epsOut.shape());

        Gradient g = pair.getFirst();
        Map<String, INDArray> gradMap = g.gradientForVariable();
        assertEquals(6, gradMap.size()); //3 layers, weight + bias gradients for each

        String[] expKeys = {"0_" + DefaultParamInitializer.WEIGHT_KEY, "0_" + DefaultParamInitializer.BIAS_KEY,
                        "1_" + DefaultParamInitializer.WEIGHT_KEY, "2_" + DefaultParamInitializer.BIAS_KEY,
                        "2_" + DefaultParamInitializer.WEIGHT_KEY, "2_" + DefaultParamInitializer.BIAS_KEY};
        Set<String> keys = gradMap.keySet();
        for (String s : expKeys) {
            assertTrue(keys.contains(s));
        }

        /*
        System.out.println(pair);
        
        //Use updater to go from raw gradients -> updates
        //Apply learning rate, gradient clipping, adagrad/momentum/rmsprop etc
        Updater updater = UpdaterCreator.getUpdater(net);
        updater.update(net, g, 0, miniBatch);
        
        StepFunction stepFunction = new NegativeGradientStepFunction();
        INDArray params = net.params();
        System.out.println(Arrays.toString(params.get(NDArrayIndex.all(), NDArrayIndex.interval(0, 10)).dup().data().asFloat()));
        stepFunction.step(params, g.gradient());
        net.setParams(params);    //params() may not be in-place
        System.out.println(Arrays.toString(params.get(NDArrayIndex.all(), NDArrayIndex.interval(0, 10)).dup().data().asFloat()));
        */
    }

    @Test
    public void testLayerNames() {
        int nIn = 10;
        int nOut = 40;

        List<String> layerNameList = new ArrayList<>();
        layerNameList.add("dnn1");
        layerNameList.add("dnn2");
        layerNameList.add("dnn3");

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                        .updater(org.deeplearning4j.nn.conf.Updater.SGD).learningRate(0.1).list()
                        .layer(0, new DenseLayer.Builder().name("dnn1").nIn(nIn).nOut(20).activation(Activation.RELU)
                                        .weightInit(WeightInit.XAVIER).build())
                        .layer(1, new DenseLayer.Builder().name("dnn2").nIn(20).nOut(30).activation(Activation.RELU)
                                        .weightInit(WeightInit.XAVIER).build())
                        .layer(2, new DenseLayer.Builder().name("dnn3").nIn(30).nOut(nOut)
                                        .activation(Activation.SOFTMAX).weightInit(WeightInit.XAVIER).build())
                        .build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        assertEquals(layerNameList.get(0), net.getLayer(0).conf().getLayer().getLayerName());
        assertEquals(layerNameList, net.getLayerNames());
        assertEquals("softmax", net.getLayer(layerNameList.get(2)).conf().getLayer().getActivationFn().toString());
    }

    @Test
    public void testTranspose() {
        MultiLayerConfiguration conf =
                        new NeuralNetConfiguration.Builder().iterations(100).momentum(0.9).regularization(true).l2(2e-4)
                                        .list().layer(0,
                                                        new RBM.Builder(RBM.HiddenUnit.GAUSSIAN,
                                                                        RBM.VisibleUnit.GAUSSIAN).nIn(4).nOut(3)
                                                                                        .weightInit(WeightInit.DISTRIBUTION)
                                                                                        .dist(new UniformDistribution(0,
                                                                                                        1))
                                                                                        .activation(Activation.TANH)
                                                                                        .lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                                                                                        .build())
                                        .layer(1, new org.deeplearning4j.nn.conf.layers.OutputLayer.Builder(
                                                        LossFunctions.LossFunction.MCXENT).nIn(3).nOut(3)
                                                                        .weightInit(WeightInit.DISTRIBUTION)
                                                                        .dist(new UniformDistribution(0, 1))
                                                                        .activation(Activation.SOFTMAX).build())
                                        .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        Layer layer = net.getLayer(0);
        int nParamsBackprop = layer.numParams(true);
        int nParamsBoth = layer.numParams(false);
        Layer transposed = layer.transpose();

        assertArrayEquals(new int[] {4, 3}, layer.getParam(DefaultParamInitializer.WEIGHT_KEY).shape());
        assertArrayEquals(new int[] {1, 3}, layer.getParam(DefaultParamInitializer.BIAS_KEY).shape());
        assertArrayEquals(new int[] {1, 4}, layer.getParam(PretrainParamInitializer.VISIBLE_BIAS_KEY).shape());

        assertArrayEquals(new int[] {3, 4}, transposed.getParam(DefaultParamInitializer.WEIGHT_KEY).shape());
        assertArrayEquals(new int[] {1, 4}, transposed.getParam(DefaultParamInitializer.BIAS_KEY).shape());
        assertArrayEquals(new int[] {1, 3}, transposed.getParam(PretrainParamInitializer.VISIBLE_BIAS_KEY).shape());


        INDArray origWeights = layer.getParam(DefaultParamInitializer.WEIGHT_KEY);
        INDArray transposedWeights = transposed.getParam(DefaultParamInitializer.WEIGHT_KEY);
        assertEquals(origWeights.transpose(), transposedWeights);
        assertEquals(layer.getParam(PretrainParamInitializer.VISIBLE_BIAS_KEY),
                        transposed.getParam(DefaultParamInitializer.BIAS_KEY));
        assertEquals(layer.getParam(DefaultParamInitializer.BIAS_KEY),
                        transposed.getParam(PretrainParamInitializer.VISIBLE_BIAS_KEY));

        assertEquals(3, ((FeedForwardLayer) transposed.conf().getLayer()).getNIn());
        assertEquals(4, ((FeedForwardLayer) transposed.conf().getLayer()).getNOut());
    }


    @Test
    public void testScoreExamples() {
        Nd4j.getRandom().setSeed(12345);
        int nIn = 5;
        int nOut = 6;
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().seed(12345).regularization(true).l1(0.01)
                        .l2(0.01).learningRate(0.1).activation(Activation.TANH).weightInit(WeightInit.XAVIER).list()
                        .layer(0, new DenseLayer.Builder().nIn(nIn).nOut(20).build())
                        .layer(1, new DenseLayer.Builder().nIn(20).nOut(30).build()).layer(2, new OutputLayer.Builder()
                                        .lossFunction(LossFunctions.LossFunction.MSE).nIn(30).nOut(nOut).build())
                        .build();

        MultiLayerConfiguration confNoReg = new NeuralNetConfiguration.Builder().seed(12345).regularization(false)
                        .learningRate(0.1).activation(Activation.TANH).weightInit(WeightInit.XAVIER).list()
                        .layer(0, new DenseLayer.Builder().nIn(nIn).nOut(20).build())
                        .layer(1, new DenseLayer.Builder().nIn(20).nOut(30).build()).layer(2, new OutputLayer.Builder()
                                        .lossFunction(LossFunctions.LossFunction.MSE).nIn(30).nOut(nOut).build())
                        .build();


        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        MultiLayerNetwork netNoReg = new MultiLayerNetwork(confNoReg);
        netNoReg.init();
        netNoReg.setParameters(net.params().dup());

        //Score single example, and compare to scoreExamples:
        INDArray input = Nd4j.rand(3, nIn);
        INDArray output = Nd4j.rand(3, nOut);
        DataSet ds = new DataSet(input, output);

        INDArray scoresWithRegularization = net.scoreExamples(ds, true);
        INDArray scoresNoRegularization = net.scoreExamples(ds, false);

        assertArrayEquals(new int[] {3, 1}, scoresWithRegularization.shape());
        assertArrayEquals(new int[] {3, 1}, scoresNoRegularization.shape());

        for (int i = 0; i < 3; i++) {
            DataSet singleEx = new DataSet(input.getRow(i), output.getRow(i));
            double score = net.score(singleEx);
            double scoreNoReg = netNoReg.score(singleEx);

            double scoreUsingScoreExamples = scoresWithRegularization.getDouble(i);
            double scoreUsingScoreExamplesNoReg = scoresNoRegularization.getDouble(i);
            assertEquals(score, scoreUsingScoreExamples, 1e-4);
            assertEquals(scoreNoReg, scoreUsingScoreExamplesNoReg, 1e-4);
            assertTrue(scoreUsingScoreExamples > scoreUsingScoreExamplesNoReg); //Regularization term increases score

            //            System.out.println(score + "\t" + scoreUsingScoreExamples + "\t|\t" + scoreNoReg + "\t" + scoreUsingScoreExamplesNoReg);
        }
    }

    @Test
    public void testDataSetScore() {

        Nd4j.getRandom().setSeed(12345);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().regularization(false).learningRate(1.0)
                        .weightInit(WeightInit.XAVIER).seed(12345L).list()
                        .layer(0, new DenseLayer.Builder().nIn(4).nOut(3).activation(Activation.SIGMOID).build())
                        .layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                                        .activation(Activation.SOFTMAX).nIn(3).nOut(3).build())
                        .pretrain(false).backprop(true).build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        INDArray in = Nd4j.create(new double[] {1.0, 2.0, 3.0, 4.0});
        INDArray out = Nd4j.create(new double[] {1, 0, 0});

        double score = net.score(new DataSet(in, out));
    }

    @Test
    public void testDataSetScoreCNN() {

        int miniBatch = 3;
        int depth = 2;
        int width = 3;
        int height = 3;
        int nOut = 2;

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().regularization(false).learningRate(1.0)
                        .seed(12345L).list().layer(0, new ConvolutionLayer.Builder(2, 2).nOut(1).build())
                        .layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                                        .activation(Activation.SOFTMAX).nOut(2).build())
                        .setInputType(InputType.convolutionalFlat(height, width, depth)).pretrain(false).backprop(true)
                        .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        Nd4j.getRandom().setSeed(12345);
        Random r = new Random(12345);
        INDArray input = Nd4j.rand(miniBatch, depth * width * height);
        INDArray labels = Nd4j.create(miniBatch, nOut);
        for (int i = 0; i < miniBatch; i++) {
            labels.putScalar(new int[] {i, r.nextInt(nOut)}, 1.0);
        }

        double score = net.score(new DataSet(input, labels));
    }

    @Test
    public void testPredict() throws Exception {

        Nd4j.getRandom().setSeed(12345);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().regularization(false).learningRate(1.0)
                        .weightInit(WeightInit.XAVIER).seed(12345L).list()
                        .layer(0, new DenseLayer.Builder().nIn(784).nOut(50).activation(Activation.RELU).build())
                        .layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                                        .activation(Activation.SOFTMAX).nIn(50).nOut(10).build())
                        .pretrain(false).backprop(true).setInputType(InputType.convolutional(28, 28, 1)).build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        DataSetIterator ds = new MnistDataSetIterator(10, 10);
        net.fit(ds);

        DataSetIterator testDs = new MnistDataSetIterator(1, 1);
        DataSet testData = testDs.next();
        testData.setLabelNames(Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"));
        String actualLables = testData.getLabelName(0);
        List<String> prediction = net.predict(testData);
        assertTrue(actualLables != null);
        assertTrue(prediction.get(0) != null);
    }

    @Test
    @Ignore
    public void testCid() throws Exception {
        System.out.println(EnvironmentUtils.buildCId());

        Environment environment = EnvironmentUtils.buildEnvironment();
        environment.setSerialVersionID(EnvironmentUtils.buildCId());

        Task task = TaskUtils.buildTask(Nd4j.create(new double[] {1, 2, 3, 4, 5, 6}));

        Heartbeat.getInstance().reportEvent(Event.STANDALONE, environment, task);

        Thread.sleep(25000);
    }

    @Test
    public void testOutput() throws Exception {
        Nd4j.getRandom().setSeed(12345);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().regularization(false).learningRate(1.0)
                        .weightInit(WeightInit.XAVIER).seed(12345L).list()
                        .layer(0, new DenseLayer.Builder().nIn(784).nOut(50).activation(Activation.RELU).build())
                        .layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                                        .activation(Activation.SOFTMAX).nIn(50).nOut(10).build())
                        .pretrain(false).backprop(true).setInputType(InputType.convolutional(28, 28, 1)).build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        DataSetIterator fullData = new MnistDataSetIterator(1, 2);
        net.fit(fullData);


        fullData.reset();
        DataSet expectedSet = fullData.next(2);
        INDArray expectedOut = net.output(expectedSet.getFeatureMatrix(), false);

        fullData.reset();

        INDArray actualOut = net.output(fullData);

        assertEquals(expectedOut, actualOut);
    }

    @Test
    public void testGradientUpdate() throws Exception {
        DataSetIterator iter = new IrisDataSetIterator(1, 1);

        Gradient expectedGradient = new DefaultGradient();
        expectedGradient.setGradientFor("0_W", Nd4j.ones(4, 5));
        expectedGradient.setGradientFor("0_b", Nd4j.ones(1, 5));
        expectedGradient.setGradientFor("1_W", Nd4j.ones(5, 3));
        expectedGradient.setGradientFor("1_b", Nd4j.ones(1, 3));

        MultiLayerConfiguration conf =
                        new NeuralNetConfiguration.Builder().updater(org.deeplearning4j.nn.conf.Updater.SGD)
                                        .learningRate(1).activation(Activation.RELU).weightInit(WeightInit.XAVIER)
                                        .list().layer(0, new DenseLayer.Builder().name("dnn1").nIn(4).nOut(5).build())
                                        .layer(1, new OutputLayer.Builder().name("output").nIn(5).nOut(3)
                                                        .activation(Activation.SOFTMAX).weightInit(WeightInit.XAVIER)
                                                        .build())
                                        .backprop(true).pretrain(false).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        net.fit(iter.next());
        // TODO validate actual layer gradientView - issue getting var out of BaseLayer w/o adding MLN getter that gets confused with local gradient vars
        Gradient actualGradient = net.gradient;
        assertNotEquals(expectedGradient.getGradientFor("0_W"), actualGradient.getGradientFor("0_W"));

        net.update(expectedGradient);
        actualGradient = net.gradient;
        assertEquals(expectedGradient.getGradientFor("0_W"), actualGradient.getGradientFor("0_W"));

        // Update params with set
        net.setParam("0_W", Nd4j.ones(4, 5));
        net.setParam("0_b", Nd4j.ones(1, 5));
        net.setParam("1_W", Nd4j.ones(5, 3));
        net.setParam("1_b", Nd4j.ones(1, 3));
        INDArray actualParams = net.params();

        // Confirm params
        assertEquals(expectedGradient.gradient(), actualParams);

        net.update(expectedGradient);
        actualParams = net.params();
        assertEquals(Nd4j.ones(1, 43).addi(1), actualParams);
    }


    @Test(expected = DL4JException.class)
    public void testCnnInvalidData() {

        int miniBatch = 3;
        int depth = 2;
        int width = 5;
        int height = 5;

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder().list()
                        .layer(0, new ConvolutionLayer.Builder().kernelSize(2, 2).stride(1, 1).padding(0, 0).nIn(2)
                                        .nOut(2).build())
                        .layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                                        .activation(Activation.SOFTMAX).nOut(2).build())
                        .setInputType(InputType.convolutional(height, width, depth)).pretrain(false).backprop(true)
                        .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        INDArray inputWrongDepth = Nd4j.rand(new int[] {miniBatch, 5, height, width}); //Order: examples, channels, height, width
        net.feedForward(inputWrongDepth);

    }

    @Test
    public void testApplyingPreTrainConfigAndParams() {
        int nIn = 10;
        int nOut = 10;

        // Test pretrain true
        MultiLayerNetwork rbmPre = getRBMModel(true, nIn, nOut);
        assertTrue(rbmPre.conf().isPretrain()); // check on the network
        assertTrue(rbmPre.getLayer(0).conf().isPretrain()); // check pretrain layer
        assertFalse(rbmPre.getLayer(1).conf().isPretrain()); // check none pretrain layer
        int actualNP = rbmPre.numParams();
        assertEquals(2 * (nIn * nOut + nOut) + nIn, actualNP);
        INDArray params = rbmPre.params();
        assertEquals(params.length(), actualNP); // check num params
        Map<String, INDArray> paramTable = rbmPre.paramTable();
        assertTrue(paramTable.containsKey("0_vb")); // check vb exists for pretrain layer
        rbmPre.setParam("0_vb", Nd4j.ones(10));
        params = rbmPre.getParam("0_vb");
        assertEquals(Nd4j.ones(10), params); // check set params for vb


        // Test pretrain false, expect same for true because its not changed when applying update
        MultiLayerNetwork rbmNoPre = getRBMModel(false, nIn, nOut);
        assertFalse(rbmNoPre.conf().isPretrain());
        assertFalse(rbmNoPre.getLayer(0).conf().isPretrain());
        assertFalse(rbmPre.getLayer(1).conf().isPretrain());
        actualNP = rbmNoPre.numParams();
        assertEquals(2 * (nIn * nOut + nOut) + nIn, actualNP);
        params = rbmNoPre.params();
        assertEquals(params.length(), actualNP);
        paramTable = rbmPre.paramTable();
        assertTrue(paramTable.containsKey("0_vb"));
    }

    @Test
    public void testLayerPreTrainSetFalseAfterPreTrain() {
        INDArray input = Nd4j.linspace(1, 10, 10);
        int nIn = 10;
        int nOut = 10;

        MultiLayerNetwork rbmPre = getRBMModel(true, nIn, nOut);
        rbmPre.fit(input);
        assertTrue(rbmPre.conf().isPretrain()); // check on the network
        assertFalse(rbmPre.getLayer(0).conf().isPretrain()); // check pretrain layer
        assertFalse(rbmPre.getLayer(1).conf().isPretrain()); // check none pretrain layer

    }

    public MultiLayerNetwork getRBMModel(boolean preTrain, int nIn, int nOut) {
        MultiLayerConfiguration rbm = new NeuralNetConfiguration.Builder()
                        .seed(42).iterations(1).updater(Updater.NONE).epsilon(
                                        1)
                        .weightInit(WeightInit.UNIFORM)
                        .list(new org.deeplearning4j.nn.conf.layers.RBM.Builder()
                                        .lossFunction(LossFunctions.LossFunction.COSINE_PROXIMITY)
                                        .activation(Activation.IDENTITY).nOut(nIn).build(),
                                        new org.deeplearning4j.nn.conf.layers.OutputLayer.Builder(
                                                        LossFunctions.LossFunction.COSINE_PROXIMITY)
                                                                        .activation(Activation.IDENTITY).nOut(nOut)
                                                                        .build())
                        .pretrain(preTrain).setInputType(InputType.feedForward(nOut)).build();
        MultiLayerNetwork network = new MultiLayerNetwork(rbm);
        network.init();
        return network;
    }


    @Test
    public void testIterationCountAndPersistence() throws IOException {
        Nd4j.getRandom().setSeed(123);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                        .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).iterations(1).seed(123)
                        .list()
                        .layer(0, new DenseLayer.Builder().nIn(4).nOut(3).weightInit(WeightInit.XAVIER)
                                        .activation(Activation.TANH).build())
                        .layer(1, new org.deeplearning4j.nn.conf.layers.OutputLayer.Builder(
                                        LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(3).nOut(3)
                                                        .build())
                        .backprop(true).pretrain(false).build();


        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.init();

        DataSetIterator iter = new IrisDataSetIterator(50, 150);

        assertEquals(0, network.getLayerWiseConfigurations().getIterationCount());
        network.fit(iter);
        assertEquals(3, network.getLayerWiseConfigurations().getIterationCount());
        iter.reset();
        network.fit(iter);
        assertEquals(6, network.getLayerWiseConfigurations().getIterationCount());
        iter.reset();
        network.fit(iter.next());
        assertEquals(7, network.getLayerWiseConfigurations().getIterationCount());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ModelSerializer.writeModel(network, baos, true);
        byte[] asBytes = baos.toByteArray();

        ByteArrayInputStream bais = new ByteArrayInputStream(asBytes);
        MultiLayerNetwork net = ModelSerializer.restoreMultiLayerNetwork(bais, true);
        assertEquals(7, net.getLayerWiseConfigurations().getIterationCount());
    }


    @Test
    public void testBiasL1L2() {


        Nd4j.getRandom().setSeed(123);
        MultiLayerConfiguration conf1 = new NeuralNetConfiguration.Builder()
                        .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).iterations(1)
                        .weightInit(WeightInit.XAVIER).activation(Activation.TANH).seed(123).list()
                        .layer(0, new DenseLayer.Builder().nIn(10).nOut(10).build())
                        .layer(1, new org.deeplearning4j.nn.conf.layers.OutputLayer.Builder(
                                        LossFunctions.LossFunction.MSE).activation(Activation.IDENTITY).nIn(10).nOut(10)
                                                        .build())
                        .backprop(true).pretrain(false).build();

        MultiLayerConfiguration conf2 = new NeuralNetConfiguration.Builder()
                        .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).regularization(true)
                        .l1Bias(0.1).l2Bias(0.2).iterations(1).weightInit(WeightInit.XAVIER).activation(Activation.TANH)
                        .seed(123).list().layer(0, new DenseLayer.Builder().nIn(10).nOut(10).build())
                        .layer(1, new org.deeplearning4j.nn.conf.layers.OutputLayer.Builder(
                                        LossFunctions.LossFunction.MSE).activation(Activation.IDENTITY).nIn(10).nOut(10)
                                                        .build())
                        .backprop(true).pretrain(false).build();

        MultiLayerNetwork net1 = new MultiLayerNetwork(conf1);
        net1.init();

        MultiLayerNetwork net2 = new MultiLayerNetwork(conf2);
        net2.init();

        assertEquals(0.1, net2.getLayer(0).conf().getLayer().getL1Bias(), 1e-6);
        assertEquals(0.2, net2.getLayer(0).conf().getLayer().getL2Bias(), 1e-6);

        INDArray features = Nd4j.rand(10, 10);
        INDArray labels = Nd4j.rand(10, 10);

        net2.setParams(net1.params().dup());

        net1.setInput(features);
        net1.setLabels(labels);
        net2.setInput(features);
        net2.setLabels(labels);

        net1.computeGradientAndScore();
        net2.computeGradientAndScore();

        double l1 = net1.calcL1(true);
        double l2 = net1.calcL2(true);
        assertEquals(0.0, l1, 0.0);
        assertEquals(0.0, l2, 0.0);

        l1 = net2.calcL1(true);
        l2 = net2.calcL2(true);
        assertEquals(0.0, l1, 0.0);
        assertEquals(0.0, l2, 0.0);


        double s1 = net1.score();
        double s2 = net2.score();
        assertEquals(s1, s2, 1e-8); //Biases initialized to 0 -> should initially have same score

        for (int i = 0; i < 10; i++) {
            net1.fit(features, labels);
        }

        net2.setParams(net1.params().dup());
        net1.computeGradientAndScore();
        net2.computeGradientAndScore();

        l1 = net1.calcL1(true);
        l2 = net1.calcL2(true);
        assertEquals(0.0, l1, 0.0);
        assertEquals(0.0, l2, 0.0);

        l1 = net2.calcL1(true);
        l2 = net2.calcL2(true);
        assertTrue(l1 > 0.0);
        assertTrue(l2 > 0.0);

        s1 = net1.score();
        s2 = net2.score();

        assertNotEquals(s1, s2, 1e-6); //Scores should differ due to bias l1/l2

        for (int i = 0; i < 2; i++) {
            assertEquals(0.0, net1.getLayer(i).calcL1(true), 0.0);
            assertEquals(0.0, net1.getLayer(i).calcL2(true), 0.0);
            assertTrue(net2.getLayer(i).calcL1(true) > 0.0);
            assertTrue(net2.getLayer(i).calcL2(true) > 0.0);
        }
    }

    @Test
    public void testSummary() {
        int V_WIDTH = 130;
        int V_HEIGHT = 130;
        int V_NFRAMES = 150;
        MultiLayerConfiguration confForArchitecture =
                        new NeuralNetConfiguration.Builder().seed(12345).regularization(true).l2(0.001) //l2 regularization on all layers
                                        .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                                        .iterations(1).learningRate(0.4).list()
                                        .layer(0, new ConvolutionLayer.Builder(10, 10).nIn(3) //3 channels: RGB
                                                        .nOut(30).stride(4, 4).activation(Activation.RELU).weightInit(
                                                                        WeightInit.RELU)
                                                        .updater(Updater.ADAGRAD).build()) //Output: (130-10+0)/4+1 = 31 -> 31*31*30
                                        .layer(1, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX)
                                                        .kernelSize(3, 3).stride(2, 2).build()) //(31-3+0)/2+1 = 15
                                        .layer(2, new ConvolutionLayer.Builder(3, 3).nIn(30).nOut(10).stride(2, 2)
                                                        .activation(Activation.RELU).weightInit(WeightInit.RELU)
                                                        .updater(Updater.ADAGRAD).build()) //Output: (15-3+0)/2+1 = 7 -> 7*7*10 = 490
                                        .layer(3, new DenseLayer.Builder().activation(Activation.RELU).nIn(490).nOut(50)
                                                        .weightInit(WeightInit.RELU).updater(Updater.ADAGRAD)
                                                        .gradientNormalization(
                                                                        GradientNormalization.ClipElementWiseAbsoluteValue)
                                                        .gradientNormalizationThreshold(10).learningRate(0.5).build())
                                        .layer(4, new GravesLSTM.Builder().activation(Activation.SOFTSIGN).nIn(50)
                                                        .nOut(50).weightInit(WeightInit.XAVIER).updater(Updater.ADAGRAD)
                                                        .gradientNormalization(
                                                                        GradientNormalization.ClipElementWiseAbsoluteValue)
                                                        .gradientNormalizationThreshold(10).learningRate(0.6)
                                                        .build())
                                        .layer(5, new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                                                        .activation(Activation.SOFTMAX).nIn(50).nOut(4) //4 possible shapes: circle, square, arc, line
                                                        .updater(Updater.ADAGRAD).weightInit(WeightInit.XAVIER)
                                                        .gradientNormalization(
                                                                        GradientNormalization.ClipElementWiseAbsoluteValue)
                                                        .gradientNormalizationThreshold(10).build())
                                        .inputPreProcessor(0, new RnnToCnnPreProcessor(V_HEIGHT, V_WIDTH, 3))
                                        .inputPreProcessor(3, new CnnToFeedForwardPreProcessor(7, 7, 10))
                                        .inputPreProcessor(4, new FeedForwardToRnnPreProcessor()).pretrain(false)
                                        .backprop(true).backpropType(BackpropType.TruncatedBPTT)
                                        .tBPTTForwardLength(V_NFRAMES / 5).tBPTTBackwardLength(V_NFRAMES / 5).build();
        MultiLayerNetwork modelExpectedArch = new MultiLayerNetwork(confForArchitecture);
        modelExpectedArch.init();
        MultiLayerNetwork modelMow = new TransferLearning.Builder(modelExpectedArch).setFeatureExtractor(2).build();
        System.out.println(modelExpectedArch.summary());
        System.out.println(modelMow.summary());
    }
}
