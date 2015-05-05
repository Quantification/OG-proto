package ch.sc.opengamma;

import com.opengamma.analytics.financial.var.NormalLinearVaRCalculator;
import com.opengamma.analytics.financial.var.NormalVaRParameters;
import com.opengamma.analytics.financial.var.VaRCalculationResult;
import com.opengamma.analytics.math.function.Function1D;
import com.opengamma.analytics.math.statistics.distribution.NormalDistribution;
import com.opengamma.analytics.math.statistics.distribution.ProbabilityDistribution;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by Alexis on 05.05.15.
 */
public class ValueAtRiskNormalDistrTest {

    private static final double HORIZON = 10;
    private static final double PERIODS = 250;
    //Three sigmas = 99% THis confidence interval is close to 1 in textbooks.
    private static final double QUANTILE_99 = new NormalDistribution(0, 1).getCDF(3.);
    private static final NormalVaRParameters PARAMETERS = new NormalVaRParameters(HORIZON, PERIODS, QUANTILE_99);

    private static final double STD_DEV = 0.5; // per year
    private static final Function1D<Double, Double> STD_PROVIDER = new Function1D<Double, Double>() {

        @Override
        public Double evaluate(final Double x) {
            return STD_DEV;
        }

    };

    private static final double TOL = 1E-12;

    @Test
    public void normalDistribution_WithZeroMean_ReturnsNegativeVaR_FAILS() {

        final double MEAN_ZERO = 0.0; // per year
        final Function1D<Double, Double> MEAN_PROVIDER = new Function1D<Double, Double>() {

            @Override
            public Double evaluate(final Double x) {
                return MEAN_ZERO;
            }

        };
        final NormalLinearVaRCalculator<Double> CALC = new NormalLinearVaRCalculator<>(MEAN_PROVIDER, STD_PROVIDER);

        final VaRCalculationResult calcResult = CALC.evaluate(PARAMETERS, 0.);
        final double VaR = calcResult.getVaRValue();

        //Expected
        double time = HORIZON/PERIODS;
        double timeRoot = Math.sqrt(HORIZON/PERIODS);
        final ProbabilityDistribution<Double> NORMAL = new NormalDistribution(0, 1);

        // Must be negative, otherwise senseless
        final double ZScore = NORMAL.getInverseCDF(1-QUANTILE_99);

        final double resultExpected = ZScore * STD_DEV * timeRoot + MEAN_ZERO * time;
        final double resultExpected_wrongSign = ZScore*STD_DEV*timeRoot- MEAN_ZERO*time;
        //Magic number is from the original test
        //assertEquals(resultExpected, VaR, 1e-9);
        assertTrue("VaR must be negative for confidence interval of 0.99",VaR < 0);
    }

    @Test // VaR at 0.5 quantile must reproduce mean value
    public void normalDistribution_Quantile05_ReturnsVaREqualToMedian_FAILS() {
        final double medianQuantile = 0.5;
        final NormalVaRParameters PARAM_Median = new NormalVaRParameters(HORIZON, PERIODS, medianQuantile);

        final double MEAN_NONZERO = 0.4; // per year
        final Function1D<Double, Double> MEAN_PROVIDER = new Function1D<Double, Double>() {

            @Override
            public Double evaluate(final Double x) {
                return MEAN_NONZERO;
            }

        };
        final NormalLinearVaRCalculator<Double> CALC = new NormalLinearVaRCalculator<>(MEAN_PROVIDER, STD_PROVIDER);

        // Act
        final VaRCalculationResult calcResult = CALC.evaluate(PARAM_Median, 0.);
        final double VaR = calcResult.getVaRValue();
        //Expected
        double time = HORIZON/PERIODS;
        double median = MEAN_NONZERO*time;
        assertEquals(median,VaR, 1e-6);
    }

    @Test // For symmetric distributions inverse Cumulative Distribution Function
    // at 0.5 quantile must return mean value
    public void testNormalInverseCumulativeDistribution_FAILS()
    {
        final double standardMean = 0;
        final ProbabilityDistribution<Double> NORMAL = new NormalDistribution(standardMean, 1);

        final double shiftedMean = 100;
        final ProbabilityDistribution<Double> NORMAL_Shifted = new NormalDistribution(shiftedMean,1);

        // Quantile corresponds to the mean = median
        final double quantile = 0.5;
        final double ZScore = NORMAL.getInverseCDF(quantile);// =0
        assertEquals(standardMean,ZScore,TOL);
        final double ZScore_Shifted = NORMAL_Shifted.getInverseCDF(quantile);//=100
        //Must be true
        assertEquals(shiftedMean,ZScore_Shifted,TOL);
    }


}
