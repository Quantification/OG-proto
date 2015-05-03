package ch.sc.opengamma;

import com.opengamma.analytics.financial.instrument.annuity.AnnuityCouponFixedDefinition;
import com.opengamma.analytics.financial.instrument.annuity.AnnuityPaymentFixedDefinition;
import com.opengamma.analytics.financial.instrument.bond.BondFixedSecurityDefinition;

import com.opengamma.analytics.financial.interestrate.bond.calculator.YieldFromPriceCalculator;
import com.opengamma.analytics.financial.interestrate.bond.definition.BondFixedSecurity;
import com.opengamma.analytics.financial.interestrate.bond.provider.BondSecurityDiscountingMethod;
import com.opengamma.analytics.financial.model.interestrate.curve.DiscountCurve;
import com.opengamma.analytics.financial.model.interestrate.curve.YieldAndDiscountCurve;
import com.opengamma.analytics.financial.model.interestrate.curve.YieldCurve;
import com.opengamma.analytics.financial.provider.description.interestrate.IssuerProviderDiscount;
import com.opengamma.analytics.financial.provider.description.interestrate.MulticurveProviderDiscount;
import com.opengamma.analytics.math.curve.ConstantDoublesCurve;
import com.opengamma.financial.convention.businessday.BusinessDayConvention;
import com.opengamma.financial.convention.businessday.BusinessDayConventionFactory;
import com.opengamma.financial.convention.calendar.Calendar;
import com.opengamma.financial.convention.calendar.CalendarNoHoliday;
import com.opengamma.financial.convention.daycount.DayCount;
import com.opengamma.financial.convention.daycount.DayCountFactory;
import com.opengamma.financial.convention.yield.YieldConvention;
import com.opengamma.financial.convention.yield.YieldConventionFactory;
import com.opengamma.util.money.*;
import com.opengamma.util.time.DateUtils;
import com.opengamma.util.tuple.Pair;
import org.threeten.bp.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User: evgeniy
 * Date: 16.04.2015
 * Time: 16:24
 */
public class FixedRateBondRunner {

    private static final Currency CURRENCY = Currency.EUR;
    private static final ZonedDateTime FIRST_ACCRUAL_DATE = DateUtils.getUTCDate(2005, 02, 20);
    private static final Period BOND_TERM = Period.ofYears(2);
    private static final Period PAYMENT_PERIOD = Period.ofMonths(6);
    private static final ZonedDateTime MATURITY_DATE = FIRST_ACCRUAL_DATE.plus(BOND_TERM);
    private static final double RATE = 0.1;
    private static final int SETTLEMENT_DAYS = 0;
    private static final double NOTIONAL = 1000d; // price of bond

    //private static final Calendar CALENDAR = new MondayToFridayCalendar("A");
    private static final Calendar CALENDAR = new CalendarNoHoliday("A");

    private static final DayCount DAY_COUNT = DayCountFactory.INSTANCE.getDayCount("30E/360");
    private static final BusinessDayConvention BUSINESS_DAY = BusinessDayConventionFactory.INSTANCE.getBusinessDayConvention("Following");
    private static final YieldConvention YIELD_CONVENTION = YieldConventionFactory.INSTANCE.getYieldConvention("STREET CONVENTION");
    private static final boolean IS_EOM = false;
    private static final String ISSUER_NAME = "Issuer";

    private static final ZonedDateTime REFERENCE_DATE = DateUtils.getUTCDate(2005, 3, 20);

    private static final BondFixedSecurityDefinition bondDefinition = BondFixedSecurityDefinition.from(
            CURRENCY, MATURITY_DATE, FIRST_ACCRUAL_DATE, PAYMENT_PERIOD, RATE,
            SETTLEMENT_DAYS, NOTIONAL, 0, CALENDAR, DAY_COUNT, BUSINESS_DAY, YIELD_CONVENTION, IS_EOM, ISSUER_NAME, "Some repo type");

    private static final YieldAndDiscountCurve DISCOUNT_CURVE = new DiscountCurve("EUR_curve", new ConstantDoublesCurve(0.96d));
    private static final YieldAndDiscountCurve YIELD_CURVE = new YieldCurve("EUR_curve", new ConstantDoublesCurve(0.04d));

    private static final MulticurveProviderDiscount MULTICURVE = new MulticurveProviderDiscount();
    static {
        MULTICURVE.setCurve(CURRENCY, YIELD_CURVE);
    }

    private static final Map<Pair<String, Currency>, YieldAndDiscountCurve> ISSUER = new LinkedHashMap<>();
    static {
        ISSUER.put(new Pair<String, Currency>() {
                       @Override
                       public String getFirst() {
                           return ISSUER_NAME;
                       }

                       @Override
                       public Currency getSecond() {
                           return CURRENCY;
                       }
                   },
                YIELD_CURVE);
    }

    private static final IssuerProviderDiscount ISSUER_MULTICURVE = new IssuerProviderDiscount(MULTICURVE, ISSUER);

    private static final BondFixedSecurity bondConverted = bondDefinition.toDerivative(REFERENCE_DATE);
    private static final AnnuityPaymentFixedDefinition nominalDefinition = (AnnuityPaymentFixedDefinition) bondDefinition.getNominal();
    private static final AnnuityCouponFixedDefinition couponDefinition = bondDefinition.getCoupons();

    public FixedRateBondRunner() {
    }

    public void run() {
        BondSecurityDiscountingMethod bondCalculator = BondSecurityDiscountingMethod.getInstance();
        YieldFromPriceCalculator yieldCalculator = YieldFromPriceCalculator.getInstance();

        System.out.println("Clean price: " + bondCalculator.cleanPriceFromYield(bondConverted, 0.12));
        System.out.println("Accrued interest:  " + bondDefinition.accruedInterest(REFERENCE_DATE));
        System.out.println("Yield to maturity:  " + yieldCalculator.visitBondFixedSecurity(bondConverted, 1.05d));
        System.out.println("Modified duration:  " + bondCalculator.modifiedDurationFromYield(bondConverted, 0.09));
        System.out.println("Macaulay duration:  " + bondCalculator.macaulayDurationFromYield(bondConverted, 0.09));
        System.out.println("Present value:  " + bondCalculator.presentValue(bondConverted, ISSUER_MULTICURVE));
    }

    public static void main(String[] arg) {
        FixedRateBondRunner runner = new FixedRateBondRunner();

        runner.run();
    }
}