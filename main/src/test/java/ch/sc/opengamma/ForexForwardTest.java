package ch.sc.opengamma;

import com.opengamma.analytics.financial.forex.definition.ForexDefinition;
import com.opengamma.analytics.financial.forex.derivative.Forex;
import com.opengamma.analytics.financial.forex.method.FXMatrix;
import com.opengamma.analytics.financial.forex.provider.ForexForwardPointsMethod;
import com.opengamma.analytics.financial.instrument.index.IborIndex;
import com.opengamma.analytics.financial.model.interestrate.curve.YieldAndDiscountCurve;
import com.opengamma.analytics.financial.model.interestrate.curve.YieldCurve;
import com.opengamma.analytics.financial.provider.calculator.forexpoints.PresentValueCurveSensitivityForexForwardPointsCalculator;
import com.opengamma.analytics.financial.provider.calculator.forexpoints.PresentValueForexForwardPointsCalculator;
import com.opengamma.analytics.financial.provider.description.interestrate.MulticurveForwardPointsProviderDiscount;
import com.opengamma.analytics.financial.provider.description.interestrate.MulticurveForwardPointsProviderInterface;
import com.opengamma.analytics.financial.provider.description.interestrate.MulticurveProviderDiscount;
import com.opengamma.analytics.financial.provider.sensitivity.forexpoints.ParameterSensitivityForexForwardPointsDiscountInterpolatedFDCalculator;
import com.opengamma.analytics.financial.provider.sensitivity.parameter.ParameterSensitivityParameterCalculator;
import com.opengamma.analytics.financial.schedule.ScheduleCalculator;
import com.opengamma.analytics.math.curve.InterpolatedDoublesCurve;
import com.opengamma.analytics.math.interpolation.CombinedInterpolatorExtrapolatorFactory;
import com.opengamma.analytics.math.interpolation.Interpolator1D;
import com.opengamma.analytics.math.interpolation.Interpolator1DFactory;
import com.opengamma.analytics.util.time.TimeCalculator;
import com.opengamma.financial.convention.businessday.BusinessDayConventionFactory;
import com.opengamma.financial.convention.calendar.Calendar;
import com.opengamma.financial.convention.calendar.MondayToFridayCalendar;
import com.opengamma.financial.convention.daycount.DayCountFactory;
import com.opengamma.util.money.Currency;
import com.opengamma.util.money.MultipleCurrencyAmount;
import com.opengamma.util.time.DateUtils;
import com.opengamma.util.tuple.ObjectsPair;
import com.opengamma.util.tuple.Pair;

import org.threeten.bp.Period;
import org.threeten.bp.ZonedDateTime;

import static com.opengamma.util.money.Currency.EUR;
import static com.opengamma.util.money.Currency.USD;
import static org.junit.Assert.*;
import org.junit.Test;
/**
 * Created by Alexis on 01.05.15.
 *
 * Methods in clas ForexForwardPointsMethod :
 * 1) presentValue
 * 2) currency exposure
 * 3) presentValueCurveSensitivity
 * 4) presentValueForwardPointsSensitivity
 */
public class ForexForwardTest {

    private static final Interpolator1D LINEAR_FLAT = CombinedInterpolatorExtrapolatorFactory.getInterpolator(Interpolator1DFactory.LINEAR, Interpolator1DFactory.FLAT_EXTRAPOLATOR,
            Interpolator1DFactory.FLAT_EXTRAPOLATOR);

    private static final double[] EUR_DSC_TIME = new double[] {0.0, 0.5, 1.0, 2.0, 5.0 };
    private static final double[] EUR_DSC_RATE = new double[] {0.0150, 0.0125, 0.0150, 0.0175, 0.0150 };
    private static final String EUR_DSC_NAME = "EUR Dsc";
    private static final YieldAndDiscountCurve EUR_DSC = new YieldCurve(EUR_DSC_NAME, new InterpolatedDoublesCurve(EUR_DSC_TIME, EUR_DSC_RATE, LINEAR_FLAT, true, EUR_DSC_NAME));

    private static final double[] USD_DSC_TIME = new double[] {0.0, 0.5, 1.0, 2.0, 5.0 };
    private static final double[] USD_DSC_RATE = new double[] {0.0100, 0.0120, 0.0120, 0.0140, 0.0140 };
    private static final String USD_DSC_NAME = "USD Dsc";
    private static final YieldAndDiscountCurve USD_DSC = new YieldCurve(USD_DSC_NAME, new InterpolatedDoublesCurve(USD_DSC_TIME, USD_DSC_RATE, LINEAR_FLAT, true, USD_DSC_NAME));


    private static final double EUR_USD = 1.40;

    public static MulticurveProviderDiscount createMulticurvesEURUSD() {
        FXMatrix fxMatrix = new FXMatrix(USD, EUR, 1.0d / EUR_USD);
        final MulticurveProviderDiscount multicurves = new MulticurveProviderDiscount(fxMatrix);
        multicurves.setCurve(EUR, EUR_DSC);
        multicurves.setCurve(USD, USD_DSC);
        return multicurves;
    }

    // FX forward definition
    private static final Currency CUR_1 = Currency.EUR;
    private static final Currency CUR_2 = Currency.USD;
    private static final ZonedDateTime PAYMENT_DATE = DateUtils.getUTCDate(2013, 6, 26);
    private static final double NOMINAL_1 = 100000000;
    // At payment date
    private static final double FX_RATE = 1.4177;
    private static final ForexDefinition FX_DEFINITION = new ForexDefinition(CUR_1, CUR_2, PAYMENT_DATE, NOMINAL_1, FX_RATE);

    private static final ZonedDateTime REFERENCE_DATE = DateUtils.getUTCDate(2013, 2, 12);
    private static final Forex FX = FX_DEFINITION.toDerivative(REFERENCE_DATE);

    private static final Calendar CALENDAR = new MondayToFridayCalendar("CAL");
    private static final ZonedDateTime SPOT_DATE = ScheduleCalculator.getAdjustedDate(REFERENCE_DATE, 2, CALENDAR);
    private static final IborIndex USDLIBOR3M = new IborIndex(Currency.USD, Period.ofMonths(3), 2, DayCountFactory.INSTANCE.getDayCount("Actual/360"), BusinessDayConventionFactory.INSTANCE
        .getBusinessDayConvention("Modified Following"), true, "USDLIBOR3M");

    private static final Pair<Currency, Currency> PAIR = new ObjectsPair<>(CUR_1, CUR_2);

    private static final ForexDefinition FX_INDIRECT_DEFINITION = new ForexDefinition(CUR_2, CUR_1, PAYMENT_DATE, -NOMINAL_1 * FX_RATE, 1.0d / FX_RATE);
    private static final Forex FX_INDIRECT = FX_INDIRECT_DEFINITION.toDerivative(REFERENCE_DATE);

    private static final double[] MARKET_QUOTES_PTS = new double[] {0.000001, 0.000002, 0.0004, 0.0009, 0.0015, 0.0020, 0.0036, 0.0050 };
    private static final int NB_MARKET_QUOTES = MARKET_QUOTES_PTS.length;
    private static final double[] MARKET_QUOTES_FWD = new double[NB_MARKET_QUOTES];
    static {
        for (int loopt = 0; loopt < NB_MARKET_QUOTES; loopt++) {
            MARKET_QUOTES_FWD[loopt] = MARKET_QUOTES_PTS[loopt] + FX_RATE;
        }
    }
    private static final Period[] MARKET_QUOTES_TENOR = new Period[] {Period.ofDays(-2), Period.ofDays(-1), Period.ofMonths(1), Period.ofMonths(2), Period.ofMonths(3),
            Period.ofMonths(6), Period.ofMonths(9), Period.ofMonths(12) };
    private static final ZonedDateTime[] MARKET_QUOTES_DATE = new ZonedDateTime[NB_MARKET_QUOTES];
    private static final double[] MARKET_QUOTES_TIME = new double[NB_MARKET_QUOTES];
    static {
        for (int loopt = 0; loopt < NB_MARKET_QUOTES; loopt++) {
            MARKET_QUOTES_DATE[loopt] = ScheduleCalculator.getAdjustedDate(SPOT_DATE, MARKET_QUOTES_TENOR[loopt], USDLIBOR3M, CALENDAR);
            MARKET_QUOTES_TIME[loopt] = TimeCalculator.getTimeBetween(REFERENCE_DATE, MARKET_QUOTES_DATE[loopt]);
        }
    }


    private static final MulticurveProviderDiscount MULTICURVES = createMulticurvesEURUSD();
    private static final InterpolatedDoublesCurve FWD_RATES = new InterpolatedDoublesCurve(MARKET_QUOTES_TIME, MARKET_QUOTES_FWD, LINEAR_FLAT, true);
    private static final MulticurveForwardPointsProviderDiscount MULTICURVES_FWD =
            new MulticurveForwardPointsProviderDiscount(MULTICURVES, FWD_RATES, PAIR);


    private static final PresentValueForexForwardPointsCalculator PVFFPC = PresentValueForexForwardPointsCalculator.getInstance();
    private static final PresentValueCurveSensitivityForexForwardPointsCalculator PVCSFFPC = PresentValueCurveSensitivityForexForwardPointsCalculator.getInstance();

    private static final double SHIFT = 1.0E-6;
    private static final ParameterSensitivityParameterCalculator<MulticurveForwardPointsProviderInterface> PS_FFP_C = new ParameterSensitivityParameterCalculator<>(
            PVCSFFPC);
    private static final ParameterSensitivityForexForwardPointsDiscountInterpolatedFDCalculator PS_FFP_FDC = new ParameterSensitivityForexForwardPointsDiscountInterpolatedFDCalculator(PVFFPC, SHIFT);

    private static final double TOLERANCE_PV = 1.0E-2; // one cent out of 100m
    //private static final double TOLERANCE_PV_DELTA = 1.0E-0;

    @Test
    /**
     * Tests the present value computation.
     */
    public void presentValueDirectOrder() {
        //    final double fxRate = MULTICURVES.getFxRate(CUR_1, CUR_2);
        final double payTime = FX.getPaymentTime(); // paymentTime Time (in years) up to the payment.
        final double fwdRate = FWD_RATES.getYValue(payTime);
        final double amount1 = NOMINAL_1;
        final double amount2 = -NOMINAL_1 * FX_RATE; //FX_RATE At payment date
        final double df2 = MULTICURVES.getDiscountFactor(CUR_2, payTime);
        final double pvExpected = df2 * (amount2 + amount1 * fwdRate);
        //Act
        ForexForwardPointsMethod METHOD_FX_PTS = ForexForwardPointsMethod.getInstance();
        final MultipleCurrencyAmount pvComputed = METHOD_FX_PTS.presentValue(FX, MULTICURVES, FWD_RATES, PAIR);

        assertEquals("ForexForwardPointsMethod: presentValue", 1, pvComputed.size());
        assertEquals("ForexForwardPointsMethod: presentValue", pvExpected, pvComputed.getAmount(CUR_2), TOLERANCE_PV);
        final MultipleCurrencyAmount pvComputed2 = METHOD_FX_PTS.presentValue(FX, MULTICURVES_FWD);
        assertEquals("ForexForwardPointsMethod: presentValue", pvComputed.getAmount(CUR_2), pvComputed2.getAmount(CUR_2), TOLERANCE_PV);
    }

}