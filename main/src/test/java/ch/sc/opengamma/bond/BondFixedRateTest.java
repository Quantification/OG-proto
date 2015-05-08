package ch.sc.opengamma.bond;

import com.opengamma.analytics.financial.instrument.annuity.AnnuityCouponFixedDefinition;
import com.opengamma.analytics.financial.instrument.bond.BondFixedSecurityDefinition;
import com.opengamma.analytics.financial.instrument.payment.CouponFixedDefinition;
import com.opengamma.analytics.financial.interestrate.annuity.derivative.Annuity;
import com.opengamma.analytics.financial.interestrate.bond.definition.BondFixedSecurity;
import com.opengamma.analytics.financial.interestrate.bond.provider.BondSecurityDiscountingMethod;
import com.opengamma.analytics.financial.interestrate.payments.derivative.CouponFixed;
import com.opengamma.analytics.financial.interestrate.payments.derivative.PaymentFixed;
import com.opengamma.analytics.financial.model.interestrate.curve.YieldAndDiscountCurve;
import com.opengamma.analytics.financial.model.interestrate.curve.YieldCurve;
import com.opengamma.analytics.financial.provider.description.interestrate.IssuerProviderDiscount;
import com.opengamma.analytics.financial.provider.description.interestrate.MulticurveProviderDiscount;
import com.opengamma.analytics.math.curve.ConstantDoublesCurve;
import com.opengamma.analytics.util.time.TimeCalculator;
import com.opengamma.financial.convention.businessday.BusinessDayConvention;
import com.opengamma.financial.convention.businessday.BusinessDayConventionFactory;
import com.opengamma.financial.convention.calendar.Calendar;
import com.opengamma.financial.convention.calendar.CalendarNoHoliday;
import com.opengamma.financial.convention.daycount.DayCount;
import com.opengamma.financial.convention.daycount.DayCountFactory;
import com.opengamma.financial.convention.yield.YieldConvention;
import com.opengamma.financial.convention.yield.YieldConventionFactory;
import com.opengamma.util.money.Currency;
import com.opengamma.util.money.MultipleCurrencyAmount;
import com.opengamma.util.time.DateUtils;
import com.opengamma.util.tuple.Pair;
import org.junit.Test;
import org.threeten.bp.Period;
import org.threeten.bp.ZonedDateTime;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Created by Alexis on 04.05.15.
 */
public class BondFixedRateTest {
    private static final Currency CURRENCY = Currency.EUR;
    private static final ZonedDateTime FIRST_ACCRUAL_DATE = DateUtils.getUTCDate(2005, 2, 20);
    private static final Period BOND_TERM = Period.ofYears(2);
    private static final Period PAYMENT_PERIOD = Period.ofMonths(6);
    private static final ZonedDateTime MATURITY_DATE = FIRST_ACCRUAL_DATE.plus(BOND_TERM);
    private static final double RATE = 0.1;
    private static final int SETTLEMENT_DAYS = 0;
    private static final double NOTIONAL = 2000d; // price of bond

    //private static final Calendar CALENDAR = new MondayToFridayCalendar("A");
    private static final Calendar CALENDAR = new CalendarNoHoliday("A");

    //THis dayCount is ignored in present value calculations
    private static final DayCount DAY_COUNT_30E360 = DayCountFactory.INSTANCE.getDayCount("30E/360");

    private static final BusinessDayConvention BUSINESS_DAY = BusinessDayConventionFactory.INSTANCE.getBusinessDayConvention("Following");
    // Cannot influence anything since the interface has only the name
    private static final YieldConvention YIELD_CONVENTION = YieldConventionFactory.INSTANCE.getYieldConvention("STREET CONVENTION");
    private static final boolean IS_EOM = false;
    private static final String ISSUER_NAME = "Issuer";

    private static final ZonedDateTime REFERENCE_DATE = DateUtils.getUTCDate(2005, 3, 20);

    private static final BondFixedSecurityDefinition bondDefinition = BondFixedSecurityDefinition.from(
            CURRENCY, MATURITY_DATE, FIRST_ACCRUAL_DATE, PAYMENT_PERIOD, RATE,
            SETTLEMENT_DAYS, NOTIONAL, 0, CALENDAR, DAY_COUNT_30E360, BUSINESS_DAY, YIELD_CONVENTION, IS_EOM, ISSUER_NAME, "Some repo type");

    private static final BondFixedSecurity bondConverted = bondDefinition.toDerivative(REFERENCE_DATE);

    //private static final YieldAndDiscountCurve DISCOUNT_CURVE = new DiscountCurve("EUR_curve", new ConstantDoublesCurve(0.96d));
    private static final YieldAndDiscountCurve YIELD_CURVE = new YieldCurve("EUR_curve", new ConstantDoublesCurve(0.04d));

    private static final MulticurveProviderDiscount MULTICURVE = new MulticurveProviderDiscount();

    static {
        MULTICURVE.setCurve(CURRENCY, YIELD_CURVE);
    }

    private static final Map<Pair<String, Currency>, YieldAndDiscountCurve> ISSUER = new LinkedHashMap<>();
    private static final Pair<String, Currency> ISSUER_CCY = new Pair<String, Currency>() {
        @Override
        public String getFirst() {
            return ISSUER_NAME;
        }

        @Override
        public Currency getSecond() {
            return CURRENCY;
        }
    };

    static {
        ISSUER.put(ISSUER_CCY, YIELD_CURVE);
    }

    private static final IssuerProviderDiscount ISSUER_PROVIDER_DISCOUNT = new IssuerProviderDiscount(MULTICURVE, ISSUER);

    BondSecurityDiscountingMethod bondCalculator = BondSecurityDiscountingMethod.getInstance();

    final static double TOL = 1E-8;

    @Test
    public void testAccruedInterest() {
        final double accruedInterest = bondConverted.getAccruedInterest();

        //Expected
        final double daysSinceCouponYearFraction = DAY_COUNT_30E360.getDayCountFraction(FIRST_ACCRUAL_DATE, REFERENCE_DATE);
        final double expectedAccruedInterest = daysSinceCouponYearFraction * RATE * NOTIONAL;
        assertEquals(expectedAccruedInterest, accruedInterest, TOL);

        //Unexpected
        final double daysSinceCouponYearFractionAct = DAY_COUNT_ActAct.getDayCountFraction(FIRST_ACCRUAL_DATE, REFERENCE_DATE);
        final double expectedAccruedInterestActAct = daysSinceCouponYearFractionAct * RATE * NOTIONAL;
        assertNotEquals(expectedAccruedInterestActAct, accruedInterest, TOL);
    }

    // Is never passed to tested methods; is used only for expected value calculation.
    private static final DayCount DAY_COUNT_ActAct = DayCountFactory.INSTANCE.getDayCount("Actual/Actual ISDA");

    @Test
    // Let's try to recover calculation of time interval to maturity - there is a zillion of subtleties
    public void detectCalculationMethodOfTimeIntervalToPrincipalPaymentDate()
    {
        // Fixed rate bond is essentially a wrapper of coupons annuity and principal payoff.
        // Here payments' dates are doubles = time intervals (Time To Maturity, TTM) measured in years
        Annuity<CouponFixed> coupon = bondConverted.getCoupon();
        Annuity<PaymentFixed> nominal = bondConverted.getNominal();
        final double timeToMaturity_fromNominal = nominal.getNthPayment(0).getPaymentTime();

        // Compare variants of Time interval calculations
        // Naive: Is different from the nominal annuity payment date
        double timeToMaturity_Naive = 23d / 12d;
        // Our DayCount is "30E/360" THis worked OK in the test for accrued interest. Here does not work
        double timeToMaturity_withOurDayCount = TimeCalculator.getTimeBetween(REFERENCE_DATE, MATURITY_DATE, DAY_COUNT_30E360);
        // these two are equal:
        assertEquals(timeToMaturity_Naive,timeToMaturity_withOurDayCount, TOL);
        // But differ from the interval from nominal
        assertNotEquals(timeToMaturity_Naive, timeToMaturity_fromNominal, 1E-6);

        // "Astronomic" TTM
        final double timeToMaturity_astro = DateUtils.getDifferenceInYears(REFERENCE_DATE, MATURITY_DATE);
        //Differs from naive TTM
        assertNotEquals(timeToMaturity_Naive,timeToMaturity_astro, 1E-6);
        // Also differs from TTM from notional
        assertNotEquals(timeToMaturity_astro, timeToMaturity_fromNominal, 1E-6);

        // Try to use the Day Count Convention that we have specified for the bond:
        final double timeToMaturity_Convention = DAY_COUNT_30E360.getDayCountFraction(REFERENCE_DATE, MATURITY_DATE);
        // It still differs from the TTM from nominal
        assertNotEquals(timeToMaturity_Convention, timeToMaturity_fromNominal, 1E-6);

        // Some debugging reveals that the method used is
        double timeToMaturity_TimeCalc = TimeCalculator.getTimeBetween(REFERENCE_DATE, MATURITY_DATE);
        // And Bingo! It is the TTM from nominal
        assertEquals(timeToMaturity_TimeCalc,timeToMaturity_fromNominal, TOL);

        //THis method uses day count "Actual/Actual ISDA" - gives coincidence with regular PV
        final double timeToMaturity_ActAct = DAY_COUNT_ActAct.getDayCountFraction(REFERENCE_DATE, MATURITY_DATE);
        // And Bingo! It equals the TTM from nominal
        assertEquals(timeToMaturity_ActAct,timeToMaturity_fromNominal, TOL);
    }

    private double CouponsNPV(AnnuityCouponFixedDefinition coupons_def,DayCount dayCount)
    {
        final int couponsCount = coupons_def.getNumberOfPayments();
        // Uses astronomic time intervals method
        double couponsNPV = 0;
        for (int couponNo = 0; couponNo < couponsCount; couponNo++) {
            CouponFixedDefinition payment = coupons_def.getNthPayment(couponNo);
            double timeToPayment = dayCount.getDayCountFraction(REFERENCE_DATE, payment.getPaymentDate());
            couponsNPV = couponsNPV + payment.getAmount() * YIELD_CURVE.getDiscountFactor(timeToPayment);
        }
        return couponsNPV;
    }

    @Test
    public void PresentValue_UsingDayCount30E360_DifferentValue() {
        final MultipleCurrencyAmount presentValue = bondCalculator.presentValue(bondConverted, ISSUER_PROVIDER_DISCOUNT);
        final double pvAmountEUR = presentValue.getAmount(CURRENCY);

        //Expected
        // Fixed rate bond is essentially a wrapper of coupons annuity and principal payoff.
        // Here payments' dates are absolute astronomical dates
        AnnuityCouponFixedDefinition coupons_def = bondDefinition.getCoupons();
        // AnnuityDefinition<PaymentFixedDefinition> nominal_def = bondDefinition.getNominal();

        final int couponsCount = coupons_def.getNumberOfPayments();
        // Uses astronomic time intervals method
        double pvCoupons = CouponsNPV(coupons_def, DAY_COUNT_30E360);

        final double timeToMaturity = DAY_COUNT_30E360.getDayCountFraction(REFERENCE_DATE, MATURITY_DATE);
        final double pvNotional = NOTIONAL * YIELD_CURVE.getDiscountFactor(timeToMaturity);

        final double expectedPV = pvNotional + pvCoupons;
        assertNotEquals(expectedPV, pvAmountEUR, 1E-6);
    }

    @Test
    public void PresentValue_UsingDayCountActAct_SameValue() {
        final MultipleCurrencyAmount presentValue = bondCalculator.presentValue(bondConverted, ISSUER_PROVIDER_DISCOUNT);
        final double pvAmountEUR = presentValue.getAmount(CURRENCY);

        //Expected
        // Fixed rate bond is essentially a wrapper of coupons annuity and principal payoff.
        // Here payments' dates are absolute astronomical dates
        AnnuityCouponFixedDefinition coupons_def = bondDefinition.getCoupons();
       // AnnuityDefinition<PaymentFixedDefinition> nominal_def = bondDefinition.getNominal();

        final int couponsCount = coupons_def.getNumberOfPayments();
        // Uses astronomic time intervals method
        double pvCoupons = CouponsNPV(coupons_def, DAY_COUNT_ActAct);

        final double timeToMaturity = DAY_COUNT_ActAct.getDayCountFraction(REFERENCE_DATE, MATURITY_DATE);
        final double pvNotional = NOTIONAL * YIELD_CURVE.getDiscountFactor(timeToMaturity);

        final double expectedPV = pvNotional + pvCoupons;
        assertEquals(expectedPV, pvAmountEUR, 1E-9);
    }

    @Test
    public void DifferentDiscountFactorsCalculators_ProduceSameResult()
    {
        final double timeToMaturity = DAY_COUNT_ActAct.getDayCountFraction(REFERENCE_DATE, MATURITY_DATE);

        double ndf_ISSUER = ISSUER_PROVIDER_DISCOUNT.getDiscountFactor(ISSUER_CCY, timeToMaturity);
        double ndf_MULTICURVE = MULTICURVE.getDiscountFactor(CURRENCY, timeToMaturity);
        assertEquals(ndf_ISSUER, ndf_MULTICURVE, 1E-9);

        final double ndf_YC = YIELD_CURVE.getDiscountFactor(timeToMaturity);
        assertEquals(ndf_YC, ndf_MULTICURVE, 1E-9);
        assertEquals(ndf_YC, ndf_ISSUER, 1E-9);
    }




}
