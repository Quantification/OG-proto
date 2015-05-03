package ch.sc.opengamma;

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
import com.opengamma.analytics.math.surface.ConstantDoublesSurface;
import com.opengamma.analytics.math.surface.Surface;
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

    private static final double EXPIRY_DATE = 0.25;
    private static final double SETTLEMENT_DATE = 0.253;
    private static final double STRIKE = 100;
    private static final boolean IS_CALL = false;
    private static final Currency CCY = Currency.AUD;
    private static final double UNIT_AMOUNT = 9;
    private static final ExerciseDecisionType EXERCISE = ExerciseDecisionType.EUROPEAN;
    private static final SettlementType SETTLEMENT_TYPE = SettlementType.CASH;
    private static final EquityOption EUROPEAN_PUT = new EquityOption(EXPIRY_DATE, SETTLEMENT_DATE, STRIKE, IS_CALL, CCY, UNIT_AMOUNT, EXERCISE, SETTLEMENT_TYPE);


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
        final double expectedPV = UNIT_AMOUNT * BlackFormulaRepository.price(forwardEquityPrice, STRIKE, EXPIRY_DATE, logNormalVol, IS_CALL);

        //Act
       final EquityOptionBlackMethod Calc = EquityOptionBlackMethod.getInstance();
        final double pv =  Calc.presentValue(EUROPEAN_PUT, marketData);

        //Assert
        assertEquals(expectedPV,pv,TOL);

    }
}
