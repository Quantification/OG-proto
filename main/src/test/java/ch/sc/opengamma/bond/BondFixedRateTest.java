package ch.sc.opengamma.bond;

import com.opengamma.analytics.financial.instrument.annuity.AnnuityCouponFixedDefinition;
import com.opengamma.analytics.financial.instrument.annuity.AnnuityDefinition;
import com.opengamma.analytics.financial.instrument.annuity.AnnuityPaymentFixedDefinition;
import com.opengamma.analytics.financial.instrument.bond.BondFixedSecurityDefinition;
import com.opengamma.analytics.financial.instrument.payment.CouponFixedDefinition;
import com.opengamma.analytics.financial.instrument.payment.PaymentFixedDefinition;
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
    private static final DayCount DAY_COUNT = DayCountFactory.INSTANCE.getDayCount("30E/360");

    private static final BusinessDayConvention BUSINESS_DAY = BusinessDayConventionFactory.INSTANCE.getBusinessDayConvention("Following");
    private static final YieldConvention YIELD_CONVENTION = YieldConventionFactory.INSTANCE.getYieldConvention("STREET CONVENTION");
    private static final boolean IS_EOM = false;
    private static final String ISSUER_NAME = "Issuer";

    private static final ZonedDateTime REFERENCE_DATE = DateUtils.getUTCDate(2005, 3, 20);

    private static final BondFixedSecurityDefinition bondDefinition = BondFixedSecurityDefinition.from(
            CURRENCY, MATURITY_DATE, FIRST_ACCRUAL_DATE, PAYMENT_PERIOD, RATE,
            SETTLEMENT_DAYS, NOTIONAL, 0, CALENDAR, DAY_COUNT, BUSINESS_DAY, YIELD_CONVENTION, IS_EOM, ISSUER_NAME, "Some repo type");

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
        final double daysSinceCouponYearFraction = DAY_COUNT.getDayCountFraction(FIRST_ACCRUAL_DATE, REFERENCE_DATE);
        final double expectedAccruedInterest = daysSinceCouponYearFraction * RATE * NOTIONAL;
        assertEquals(expectedAccruedInterest, accruedInterest, TOL);
    }

    @Test
    public void testPresentValue() {
        final MultipleCurrencyAmount presentValue = bondCalculator.presentValue(bondConverted, ISSUER_PROVIDER_DISCOUNT);
        final double pvAmountEUR = presentValue.getAmount(CURRENCY);
        //Expected
        AnnuityCouponFixedDefinition coupons_def = bondDefinition.getCoupons();
        AnnuityDefinition<PaymentFixedDefinition> nominal_def = bondDefinition.getNominal();

        Annuity<CouponFixed> coupon = bondConverted.getCoupon();
        // BUG: Payment date differs from naive calc
        Annuity<PaymentFixed> nominal = bondConverted.getNominal();

        final int couponsCount = coupons_def.getNumberOfPayments();
        // Uses astronomic time intervals method
        double pvCoupons = 0;
        double pvCoupons_alt = 0;
        for (int couponNo = 0; couponNo < couponsCount; couponNo++) {
            CouponFixedDefinition payment = coupons_def.getNthPayment(couponNo);
            double timeToPayment_alt = TimeCalculator.getTimeBetween(REFERENCE_DATE, payment.getPaymentDate());
            double timeToPayment = DAY_COUNT.getDayCountFraction(REFERENCE_DATE, payment.getPaymentDate());
            pvCoupons = pvCoupons + payment.getAmount() * YIELD_CURVE.getDiscountFactor(timeToPayment);
            pvCoupons_alt = pvCoupons_alt + payment.getAmount() * YIELD_CURVE.getDiscountFactor(timeToPayment_alt);
        }

        // To see in debugger
        // Naive: Is different from the nominal annuity payment date
        double timeToMaturity_Naive = 23d / 12d;
        // Our DayCount is "30E/360" THis worked OK in the test for accrued interest. Here does not work
        double timeToMaturity_withOurDayCount = TimeCalculator.getTimeBetween(REFERENCE_DATE, MATURITY_DATE, DAY_COUNT);
        // "Astronomic" TTM
        final double timeToMaturity_astro = DateUtils.getDifferenceInYears(REFERENCE_DATE, MATURITY_DATE);
        final double timeToMaturity = DAY_COUNT.getDayCountFraction(REFERENCE_DATE, MATURITY_DATE);
        //THis method uses day count "Actual/Actual ISDA" - gives coincidence with regular PV
        double timeToMaturity_alt = TimeCalculator.getTimeBetween(REFERENCE_DATE, MATURITY_DATE);
        double timeToMaturity_fromNominal = nominal.getNthPayment(0).getPaymentTime();

        // All methods of discount factor calculation give the same result for the same arg.
        final double notionalDiscountFactor = YIELD_CURVE.getDiscountFactor(timeToMaturity);
        double ndf_ISSUER = ISSUER_PROVIDER_DISCOUNT.getDiscountFactor(ISSUER_CCY, timeToMaturity);
        double ndf_MULTICURVE = MULTICURVE.getDiscountFactor(CURRENCY, timeToMaturity);

        final double pvNotional = NOTIONAL * notionalDiscountFactor;
        double pvNotional_alt = NOTIONAL * YIELD_CURVE.getDiscountFactor(timeToMaturity_alt);

        final double expectedPV = pvNotional + pvCoupons;
        final double expectedPV_alt = pvNotional_alt + pvCoupons_alt;

        assertEquals(expectedPV_alt, pvAmountEUR, TOL);
        assertNotEquals(expectedPV, pvAmountEUR, 1E-6);
    }

}
