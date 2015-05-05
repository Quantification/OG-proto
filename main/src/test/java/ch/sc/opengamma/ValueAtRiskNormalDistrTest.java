package ch.sc.opengamma;

import com.opengamma.analytics.financial.var.NormalLinearVaRCalculator;
import com.opengamma.analytics.financial.var.NormalVaRParameters;
import com.opengamma.analytics.financial.var.VaRCalculationResult;
import com.opengamma.analytics.math.function.Function1D;
import com.opengamma.analytics.math.statistics.distribution.NormalDistribution;
import com.opengamma.analytics.math.statistics.distribution.ProbabilityDistribution;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by Alexis on 05.05.15.
 */
public class ValueAtRiskNormalDistrTest {

    private static final double HORIZON = 10;
    private static final double PERIODS = 250;
    //Three sigmas = 99%
    private static final double QUANTILE = new NormalDistribution(0, 1).getCDF(3.);
    private static final NormalVaRParameters PARAMETERS = new NormalVaRParameters(HORIZON, PERIODS, QUANTILE);
    private static final double STD_DEV =0.4; // per year
    private static final Function1D<Double, Double> MEAN_PROVIDER = new Function1D<Double, Double>() {

        @Override
        public Double evaluate(final Double x) {
            return MEAN;
        }

    };
    private static final double MEAN = 1; // per year
    private static final Function1D<Double, Double> STD_PROVIDER = new Function1D<Double, Double>() {

        @Override
        public Double evaluate(final Double x) {
            return STD_DEV;
        }

    };
    private static final NormalLinearVaRCalculator<Double> CALCULATOR = new NormalLinearVaRCalculator<>(MEAN_PROVIDER, STD_PROVIDER);

    private static final double TOL = 1E-12;

    @Test
    public void testValueAtRisk() {
        final VaRCalculationResult calcResult = CALCULATOR.evaluate(PARAMETERS, 0.);

        //Expected
        double time = HORIZON/PERIODS;
        double timeRoot = Math.sqrt(HORIZON/PERIODS);
        final ProbabilityDistribution<Double> NORMAL = new NormalDistribution(0, 1);
        final ProbabilityDistribution<Double> NORMAL_Scaled = new NormalDistribution(MEAN, STD_DEV);

        //Strangely the shifted and scaled distribution gives the same result as the standard one.
        final double ZScore = NORMAL.getInverseCDF(1-QUANTILE);
        final double ZScore_Scaled = NORMAL_Scaled.getInverseCDF(1-QUANTILE);


        final double resultExpected = ZScore*STD_DEV*timeRoot + MEAN*timeRoot*timeRoot;
        final double resultExpected_wrongSign = ZScore*STD_DEV*timeRoot- MEAN*timeRoot*timeRoot;
        assertEquals(calcResult.getVaRValue(), 3 * 0.2 - 0.4*0.4*0.1, 1e-9);
    }

    @Test
    public void testValueAtRiskMedian_FAILS() {
        final double medianQuantile = 0.5;
        final NormalVaRParameters PARAM_Median = new NormalVaRParameters(HORIZON, PERIODS, medianQuantile);
        final VaRCalculationResult calcResult = CALCULATOR.evaluate(PARAM_Median, 0.);

        //Expected
        double time = HORIZON/PERIODS;
        double expectedVaR = MEAN*time;
        assertEquals(expectedVaR,calcResult.getVaRValue(), 1e-9);
    }

    @Test
    public void testNormalInverseCumulativeDistribution_FAILS()
    {
        double timeRoot = Math.sqrt(HORIZON/PERIODS);
        final double standardMean = 0;
        final ProbabilityDistribution<Double> NORMAL = new NormalDistribution(standardMean, 1);
        final double shiftedMean = 100;
        final ProbabilityDistribution<Double> NORMAL_Shifted = new NormalDistribution(shiftedMean,1);

        // Quantile corresponds to the mean = median
        final double quantile = 0.5;
        final double ZScore = NORMAL.getInverseCDF(quantile);
        assertEquals(standardMean,ZScore,TOL);
        final double ZScore_Shifted = NORMAL_Shifted.getInverseCDF(quantile);
        //Must be true
        assertEquals(shiftedMean,ZScore_Shifted,TOL);
    }


}
