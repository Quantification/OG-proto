package ch.sc.opengamma.option;

import com.opengamma.analytics.financial.ExerciseDecisionType;
import com.opengamma.analytics.financial.commodity.definition.SettlementType;
import com.opengamma.analytics.financial.equity.StaticReplicationDataBundle;
import com.opengamma.analytics.financial.equity.option.EquityOption;
import com.opengamma.analytics.financial.equity.option.EquityOptionBlackMethod;
import com.opengamma.analytics.financial.model.interestrate.curve.DiscountCurve;
import com.opengamma.analytics.financial.model.interestrate.curve.ForwardCurve;
import com.opengamma.analytics.financial.model.interestrate.curve.YieldAndDiscountCurve;
import com.opengamma.analytics.financial.model.volatility.BlackFormulaRepository;
import com.opengamma.analytics.financial.model.volatility.surface.BlackVolatilitySurfaceStrike;
import com.opengamma.analytics.math.curve.ConstantDoublesCurve;
import com.opengamma.analytics.math.statistics.distribution.NormalDistribution;
import com.opengamma.analytics.math.statistics.distribution.ProbabilityDistribution;
import com.opengamma.analytics.math.surface.ConstantDoublesSurface;
import com.opengamma.analytics.math.surface.Surface;
import com.opengamma.lang.annotation.ExternalFunction;
import com.opengamma.util.money.Currency;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by Alexis on 02.05.15.
 */
public class EquityOptionTest {

    /**
     *
     * @param timeToExpiry time (in years as a double) until the date-time at which the reference index is fixed, not negative.
     * @param timeToSettlement time (in years as a double) until the date-time at which the contract is settled, not negative. Must be at or after the
     * expiry.
     * @param strike Strike price at trade time. Note that we may handle margin by resetting this at the end of each trading day, not negative or zero
     * @param isCall True if the option is a Call, false if it is a Put.
     * @param currency The reporting currency of the future, not null
     * @param unitAmount The unit value per tick, in given currency. A negative value may represent a short position. Not zero.
     * @param exerciseType The exercise type of this option, not null
     * @param settlementType The settlement type option this option, not null

    public EquityOption(final double timeToExpiry,
                        final double timeToSettlement,
                        final double strike,
                        final boolean isCall,
                        final Currency currency,
                        final double unitAmount,
                        final ExerciseDecisionType exerciseType,
                        final SettlementType settlementType)
     */

    private static final double TIME_TO_EXPIRY = 0.25;
    private static final double SETTLEMENT_DATE = 0.253;
    private static final double STRIKE = 100;
    private static final boolean IS_CALL = false;
    private static final Currency CCY = Currency.AUD;
    private static final double UNIT_AMOUNT = 10;
    private static final ExerciseDecisionType EXERCISE = ExerciseDecisionType.EUROPEAN;
    private static final SettlementType SETTLEMENT_TYPE = SettlementType.CASH;
    private static final EquityOption EUROPEAN_PUT = new EquityOption(TIME_TO_EXPIRY, SETTLEMENT_DATE, STRIKE, IS_CALL, CCY, UNIT_AMOUNT, EXERCISE, SETTLEMENT_TYPE);


    //LogNormal volatility surface
    final static double logNormalVol = 1;
    final static Surface<Double, Double, Double> v_surface = new ConstantDoublesSurface(logNormalVol);
    final static BlackVolatilitySurfaceStrike volSurface = new BlackVolatilitySurfaceStrike(v_surface);

    // Cash discount rate
    final static double discountFlatRate = 1;
    final static  YieldAndDiscountCurve DISCOUNT_CURVE = new DiscountCurve("Discount curve",new ConstantDoublesCurve(discountFlatRate));

    //!!! FORWARD PRICE OF THE UNDERLYING
    final static double forwardEquityPrice = 100;
    final static  ForwardCurve forwardCurve = new ForwardCurve(forwardEquityPrice);

    /**
     * Market data required to price instruments where the pricing models need a discounting curve, a forward (underlying) curve
     * and a Black implied volatility surface.
     *
     * marketData A StaticReplicationDataBundle, containing a BlackVolatilitySurface, forward equity and funding curves
     */
    /**
     * @param volSurf The volatility surface,
     * @param DISCOUNT_CURVE YieldAndDiscountCurve used to discount payments and in this case, also to compute the forward value of the underlying
     * @param final ForwardCurve forwardCurve the forward curve
     */
    final static StaticReplicationDataBundle marketData = new StaticReplicationDataBundle(volSurface, DISCOUNT_CURVE,  forwardCurve) ;

    final static double TOL = 1E-8;

    @Test
    public void testEquityOptionPresentValue()
    {
        //Cash discount factor
        final double discountFactor = marketData.getDiscountCurve().getDiscountFactor(EUROPEAN_PUT.getTimeToSettlement());
        assertEquals(discountFactor,discountFlatRate,TOL);

        //Expected
        final double expectedPV =  BlackFormulaRepository.price(forwardEquityPrice, STRIKE, TIME_TO_EXPIRY, logNormalVol, IS_CALL);

        //Act
        final EquityOptionBlackMethod Calc = EquityOptionBlackMethod.getInstance();
        final double pv =  Calc.presentValue(EUROPEAN_PUT, marketData)/UNIT_AMOUNT ;

        //Assert
        assertEquals(expectedPV,pv,TOL);
        //Expected explicit
        final double rootTime = Math.sqrt(TIME_TO_EXPIRY);
        final double d1 = Math.log(forwardEquityPrice/STRIKE)/(logNormalVol*rootTime)+ 0.5 * (logNormalVol*rootTime);
        final double d2 = Math.log(forwardEquityPrice/STRIKE)/(logNormalVol*rootTime)- 0.5 * (logNormalVol*rootTime);
        final ProbabilityDistribution<Double> NORMAL = new NormalDistribution(0, 1);
        final double calculatedPV = STRIKE * NORMAL.getCDF(-d2)-forwardEquityPrice *  NORMAL.getCDF(-d1);

        assertEquals(calculatedPV,pv,TOL);
    }

    @Test
    public void  testImpliedVolatilityBlackModel(){
    /**
     * Get the log-normal (Black) implied volatility of an  European option
     * @param price The <b>forward</b> price - i.e. the market price divided by the numeraire (i.e. the zero bond p(0,T) for the T-forward measure)
     * @param forward The forward value of the underlying
     * @param strike The Strike
     * @param timeToExpiry The time-to-expiry
     * @param isCall true for call
     * @return log-normal (Black) implied volatility
     */
        //Specific value
    final double forwardOptionPrice = 19.74126513658475;
    final double implVol =  BlackFormulaRepository.impliedVolatility(forwardOptionPrice, forwardEquityPrice,STRIKE, TIME_TO_EXPIRY, IS_CALL);

        //Assert
        assertEquals(logNormalVol,implVol,TOL);
    }

    @Test
    public void testDeltaBlackModel()
    {
        /**
         * The forward (i.e. driftless) delta
         * @param forward The forward value of the underlying
         * @param strike The Strike
         * @param timeToExpiry The time-to-expiry
         * @param lognormalVol The log-normal volatility
         * @param isCall true for call
         * @return The forward delta
         */
        final double delta =  BlackFormulaRepository.delta(forwardEquityPrice,STRIKE, TIME_TO_EXPIRY, logNormalVol, IS_CALL);

        //ExpectedValue
        double dUnderlyingPrice = 0.01;
        final double presentValueUpper =  BlackFormulaRepository.price(forwardEquityPrice+dUnderlyingPrice, STRIKE, TIME_TO_EXPIRY, logNormalVol, IS_CALL);
        final double presentValueLower =  BlackFormulaRepository.price(forwardEquityPrice-dUnderlyingPrice, STRIKE, TIME_TO_EXPIRY, logNormalVol, IS_CALL);
        final double dPresentValue = presentValueUpper -presentValueLower;
        final double expectedDelta =dPresentValue/(2*dUnderlyingPrice);

        //Assert
        assertEquals(expectedDelta,delta,1E-6);
    }

    @Test
    public void testGammaBlackModel()
    {
        /**
         * @param derivative An EquityOption, the OG-Analytics form of the derivative
         * @param marketData A StaticReplicationDataBundle, containing a BlackVolatilitySurface, forward equity and funding curves
         * @return The spot gamma wrt the spot underlying, ie the 2nd order sensitivity of the present value to the spot value of the underlying,
         *          $\frac{\partial^2 (PV)}{\partial S^2}$
         */
        double gamma =  EquityOptionBlackMethod.getInstance().gammaWrtSpot(EUROPEAN_PUT, marketData);

        //ExpectedValue
        double dUnderlyingPrice = 0.01;
        final double presentValue1 =  BlackFormulaRepository.price(forwardEquityPrice+dUnderlyingPrice, STRIKE, TIME_TO_EXPIRY, logNormalVol, IS_CALL);
        final double presentValue0 =  BlackFormulaRepository.price(forwardEquityPrice, STRIKE, TIME_TO_EXPIRY, logNormalVol, IS_CALL);
        final double presentValue_1 =  BlackFormulaRepository.price(forwardEquityPrice-dUnderlyingPrice, STRIKE, TIME_TO_EXPIRY, logNormalVol, IS_CALL);
        final double ddPresentValue = presentValue1 -2*presentValue0 + presentValue_1;
        final double expectedGamma =ddPresentValue/(dUnderlyingPrice*dUnderlyingPrice);

        //Assert
        assertEquals(expectedGamma,gamma,1E-6);
    }

    @Test
    public void testThetaBlackModel()
    {
        /**
         * The forward (i.e. driftless) delta
         * @param forward The forward value of the underlying
         * @param strike The Strike
         * @param timeToExpiry The time-to-expiry
         * @param lognormalVol The log-normal volatility
         * @param isCall true for call
         * @return The forward delta
         */
        final double theta =  EquityOptionBlackMethod.getInstance().spotTheta(EUROPEAN_PUT, marketData);

        //ExpectedValue
        double dTime = 0.0001;
        final double presentValueUpper =  BlackFormulaRepository.price(forwardEquityPrice, STRIKE, TIME_TO_EXPIRY-dTime, logNormalVol, IS_CALL);
        final double presentValueLower =  BlackFormulaRepository.price(forwardEquityPrice, STRIKE, TIME_TO_EXPIRY+dTime, logNormalVol, IS_CALL);
        final double dPresentValue = presentValueUpper - presentValueLower;
        final double expectedTheta =dPresentValue/(2*dTime);

        //Assert
        assertEquals(expectedTheta,theta,1E-6);
    }
}
