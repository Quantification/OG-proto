package ch.sc.opengamma;

import com.opengamma.analytics.math.statistics.distribution.NormalDistribution;
import com.opengamma.analytics.math.statistics.distribution.ProbabilityDistribution;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by Alexis on 06.05.15.
 */
public class NormalDistributionTest {

    private static final double TOL = 1E-12;

    @Test // For symmetric distributions inverse Cumulative Distribution Function
    // at 0.5 quantile must return mean value
    public void testInverseCumulativeDistribution_ShiftMean_FAILS()
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

    @Test
    public void testInverseCumulativeDistribution_NonUnitStdDeriv_FAILS()
    {
        final double standardMean = 0;
        final double standardStdDev = 1;
        final ProbabilityDistribution<Double> NORMAL = new NormalDistribution(standardMean, standardStdDev);
        final double quantileStdDev_Down = NORMAL.getCDF(standardMean-standardStdDev);
        final double quantileStdDev_Up = NORMAL.getCDF(standardMean+standardStdDev);

        assertEquals(standardMean-standardStdDev, NORMAL.getInverseCDF(quantileStdDev_Down),TOL);
        assertEquals(standardMean+standardStdDev, NORMAL.getInverseCDF(quantileStdDev_Up),TOL);

        //These tests pass OK
        final double stretchedStdDev = 10;
        final ProbabilityDistribution<Double> NORMAL_Stretched = new NormalDistribution(standardMean,stretchedStdDev);

        //FAILS
        assertEquals(standardMean-stretchedStdDev, NORMAL_Stretched.getInverseCDF(quantileStdDev_Down),TOL);
        assertEquals(standardMean+stretchedStdDev, NORMAL_Stretched.getInverseCDF(quantileStdDev_Up),TOL);
    }

    @Test
    public void  testCumulativeDistribution_ShiftMean_FAILS()
    {
        final double standardMean = 0;
        final ProbabilityDistribution<Double> NORMAL = new NormalDistribution(standardMean, 1);
        // Symmetric Cumulative distribution function (mean) = 1/2.
        final double cdfOfMean = 0.5;
        assertEquals(NORMAL.getCDF(standardMean),cdfOfMean,1E-9);

        final double shiftedMean = 100;
        final ProbabilityDistribution<Double> NORMAL_Shifted = new NormalDistribution(shiftedMean,1);
        assertEquals(NORMAL_Shifted.getCDF(shiftedMean),cdfOfMean,1E-9);
    }

    @Test
    public void testCumulativeDistribution_NonUnitStdDeriv_FAILS()
    {
        final double standardMean = 0;
        final double standardStdDev = 1;
        final ProbabilityDistribution<Double> NORMAL = new NormalDistribution(standardMean, standardStdDev);
        final double quantileStdDev_Down = NORMAL.getCDF(standardMean - standardStdDev);
        final double quantileStdDev_Up = NORMAL.getCDF(standardMean+standardStdDev);

        final double stretchedStdDev = 10;
        final ProbabilityDistribution<Double> NORMAL_Stretched = new NormalDistribution(standardMean,stretchedStdDev);

        //FAILS
        //assertEquals(quantileStdDev_Down, NORMAL_Stretched.getCDF(standardMean - stretchedStdDev),1E-6);
        assertEquals(quantileStdDev_Up, NORMAL_Stretched.getCDF(standardMean + stretchedStdDev),1E-6);
    }

}
