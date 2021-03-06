package ch.sc.opengamma.option;

import com.opengamma.analytics.financial.model.interestrate.curve.YieldCurve;
import com.opengamma.analytics.financial.model.option.definition.Barrier;
import com.opengamma.analytics.financial.model.option.definition.EuropeanStandardBarrierOptionDefinition;
import com.opengamma.analytics.financial.model.option.definition.StandardOptionDataBundle;
import com.opengamma.analytics.financial.model.option.pricing.analytic.AnalyticOptionModel;
import com.opengamma.analytics.financial.model.option.pricing.analytic.EuropeanStandardBarrierOptionModel;
import com.opengamma.analytics.financial.model.option.pricing.analytic.formula.BlackBarrierPriceFunction;
import com.opengamma.analytics.financial.model.option.pricing.analytic.formula.BlackFunctionData;
import com.opengamma.analytics.financial.model.option.pricing.analytic.formula.BlackPriceFunction;
import com.opengamma.analytics.financial.model.option.pricing.analytic.formula.EuropeanVanillaOption;
import com.opengamma.analytics.financial.model.volatility.BlackFormulaRepository;
import com.opengamma.analytics.financial.model.volatility.surface.VolatilitySurface;
import com.opengamma.analytics.math.curve.ConstantDoublesCurve;
import com.opengamma.analytics.math.function.Function1D;
import com.opengamma.analytics.math.statistics.distribution.NormalDistribution;
import com.opengamma.analytics.math.statistics.distribution.ProbabilityDistribution;
import com.opengamma.analytics.math.surface.ConstantDoublesSurface;
import com.opengamma.util.time.DateUtils;
import com.opengamma.util.time.Expiry;
import org.junit.Test;
import org.threeten.bp.ZonedDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by Alexis on 03.05.15.
 * Copy of
 *  //   public class BlackBarrierPriceFunctionTest {
 */
public class BarrierOptionBlackModelPricingTest {

        private static final ZonedDateTime REFERENCE_DATE = DateUtils.getUTCDate(2011, 7, 1);
        private static final ZonedDateTime EXPIRY_DATE = DateUtils.getUTCDate(2015, 1, 2);
        private static final double EXPIRY_TIME_Interval = DateUtils.getDifferenceInYears(REFERENCE_DATE, EXPIRY_DATE);
        private static final double STRIKE_100 = 100;
        private static final double STRIKE_HIGH = 120;
        private static final boolean IS_CALL = true;
        private static final EuropeanVanillaOption VANILLA_CALL_K100 = new EuropeanVanillaOption(STRIKE_100, EXPIRY_TIME_Interval, IS_CALL);
        private static final EuropeanVanillaOption VANILLA_PUT_K100 = new EuropeanVanillaOption(STRIKE_100, EXPIRY_TIME_Interval, !IS_CALL);
        private static final EuropeanVanillaOption VANILLA_PUT_KHI = new EuropeanVanillaOption(STRIKE_HIGH, EXPIRY_TIME_Interval, !IS_CALL);

        private static final double BARRIER_90 = 90;
        private static final Barrier BARRIER_DOWN_IN = new Barrier(Barrier.KnockType.IN, Barrier.BarrierType.DOWN, Barrier.ObservationType.CONTINUOUS, BARRIER_90);
        private static final Barrier BARRIER_DOWN_OUT = new Barrier(Barrier.KnockType.OUT, Barrier.BarrierType.DOWN, Barrier.ObservationType.CONTINUOUS, BARRIER_90);
        private static final double BARRIER_110 = 110;
        private static final Barrier BARRIER_UP_IN = new Barrier(Barrier.KnockType.IN, Barrier.BarrierType.UP, Barrier.ObservationType.CONTINUOUS, BARRIER_110);
        private static final Barrier BARRIER_UP_OUT = new Barrier(Barrier.KnockType.OUT, Barrier.BarrierType.UP, Barrier.ObservationType.CONTINUOUS, BARRIER_110);
        private static final double REBATE = 2;
        private static final double SPOT = 105;
        private static final double RATE_DOM = 0.05; // Domestic rate
        private static final double RATE_FOR = 0.02; // Foreign rate
        private static final double COST_OF_CARRY = RATE_DOM - RATE_FOR; // Domestic - Foreign rate
        private static final double VOLATILITY = 0.20;
        private static final BlackBarrierPriceFunction BARRIER_FUNCTION = BlackBarrierPriceFunction.getInstance();

        private static final double DF_FOR = Math.exp(-RATE_FOR * EXPIRY_TIME_Interval); // 'Base Ccy
        private static final double DF_DOM = Math.exp(-RATE_DOM * EXPIRY_TIME_Interval); // 'Quote Ccy
        private static final double FWD_FX = SPOT * DF_FOR / DF_DOM;
        private static final BlackFunctionData BLACK_FUNCTION_DATA = new BlackFunctionData(FWD_FX, DF_DOM, VOLATILITY);
        private static final BlackPriceFunction BLACK_FUNCTION = new BlackPriceFunction();

        @Test
        /** Tests the 'In-Out Parity' condition: Without rebates,
         * the price of a Knock-In plus a Knock-Out of arbitrary barrier level must equal
         * that of the underlying vanilla option */
        public void DownCallInOutPrice_AsInTextbook() {

            // Vanilla
            final Function1D<BlackFunctionData, Double> fcnVanillaCall = BLACK_FUNCTION.getPriceFunction(VANILLA_CALL_K100);
            final double pxVanillaCall = fcnVanillaCall.evaluate(BLACK_FUNCTION_DATA);

            // Barriers without rebate
            final double noRebate = 0.0;
            final double priceCallDownIn =
                    BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_IN, noRebate, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            final double priceCallDownOut =
                    BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_OUT, noRebate, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            assertEquals("Knock In-Out Parity fails", pxVanillaCall ,(priceCallDownIn + priceCallDownOut), 1.e-9);

            //Expected Call Down and In  CallStrike > Barrier
            // Hull/19 Exotic options / 6 Barrier options
            final double rootTime = Math.sqrt(EXPIRY_TIME_Interval);
            final double lambda =  0.5 + (RATE_DOM-RATE_FOR)/(VOLATILITY*VOLATILITY) ;
            final double y = Math.log((BARRIER_90*BARRIER_90)/(SPOT*STRIKE_100))/(VOLATILITY*rootTime)
                    +lambda * (VOLATILITY*rootTime);
            final ProbabilityDistribution<Double> NORMAL = new NormalDistribution(0, 1);

            final double priceCallDownAndIn_Expected  =
                    SPOT* DF_FOR*Math.pow(BARRIER_90/SPOT,2*lambda)*NORMAL.getCDF(y)
                    -STRIKE_100*DF_DOM*Math.pow(BARRIER_90/SPOT,2*lambda - 2)* NORMAL.getCDF(y - VOLATILITY * rootTime);
            assertEquals("Down-And-In Call price as in textbook",
                    priceCallDownAndIn_Expected, priceCallDownIn, 1.e-9);

            //How the calculator knows about RATE_FOR? OK, via CostOfCarry.

            //Expected Call Down and Out  CallStrike > Barrier
            final double vanillaCallPrice_Expected =
                    DF_DOM * BlackFormulaRepository.price(FWD_FX, STRIKE_100, EXPIRY_TIME_Interval, VOLATILITY, IS_CALL);
            assertEquals("Vanilla call price is OK", vanillaCallPrice_Expected, pxVanillaCall, 1E-9);

            final double priceCallDownAndOut_Expected  = vanillaCallPrice_Expected - priceCallDownAndIn_Expected;
            assertEquals("Down-And-Out call price as in textbook", priceCallDownAndOut_Expected, priceCallDownOut, 1.e-9);
        }

        @Test
        /**
         * Tests the 'In-Out Parity' condition: Knock-In's pay rebate at maturity if barrier isn't hit. Knock-Out pays at moment barrier is hit.
         * The discounting issue can be sidestepped by setting rates to 0.
         */
        public void inOutParityWithRebate() {

            // Vanilla
            final Function1D<BlackFunctionData, Double> fcnVanillaCall = BLACK_FUNCTION.getPriceFunction(VANILLA_CALL_K100);
            final BlackFunctionData zeroRatesMarket = new BlackFunctionData(SPOT, 1.0, VOLATILITY);
            final double pxVanillaCall = fcnVanillaCall.evaluate(zeroRatesMarket);

            // Barriers with rebate
            final double priceDownInRebate = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_IN, REBATE, SPOT, 0.0, 0.0, VOLATILITY);
            final double priceDownOutRebate = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_OUT, REBATE, SPOT, 0.0, 0.0, VOLATILITY);
            assertEquals("Knock In-Out Parity fails", 1.0, (pxVanillaCall + REBATE) / (priceDownInRebate + priceDownOutRebate), 1.e-6);
        }

        @Test
        /** Tests the 'In-Out Parity' condition: The price of a Knock-In plus a Knock-Out of arbitrary barrier level must equal that of the underlying vanilla option + value of the rebate */
        public void inOutParityMorePathsWithRebate() {

            // Market with zero rates, domestic and foreign
            final BlackFunctionData zeroRatesMarket = new BlackFunctionData(SPOT, 1.0, VOLATILITY);
            final double rateDomestic = 0.0;
            final double rateForeign = 0.0;
            final double costOfCarry = rateDomestic - rateForeign;

            // Rebate
            final double pxRebate = REBATE;
            // 2 - Vanillas - Call and Put
            final Function1D<BlackFunctionData, Double> fcnVanillaCall = BLACK_FUNCTION.getPriceFunction(VANILLA_CALL_K100);
            final double pxVanillaCall = fcnVanillaCall.evaluate(zeroRatesMarket);
            final Function1D<BlackFunctionData, Double> fcnVanillaPut = BLACK_FUNCTION.getPriceFunction(VANILLA_PUT_K100);
            final double pxVanillaPut = fcnVanillaPut.evaluate(zeroRatesMarket);
            // Barriers: Up and Down, Call and Put, In and Out
            final double pxDownInCall = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_IN, REBATE, SPOT, costOfCarry, rateDomestic, VOLATILITY);
            final double pxDownOutCall = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_OUT, REBATE, SPOT, costOfCarry, rateDomestic, VOLATILITY);
            assertEquals("Knock In-Out Parity fails", 1.0, (pxVanillaCall + pxRebate) / (pxDownInCall + pxDownOutCall), 1.e-6);
            //assertTrue("Knock In-Out Parity fails", Math.abs((pxVanillaCall + pxRebate) / (pxDownInCall + pxDownOutCall) - 1) < 1.e-6);

            final double pxDownInPut = BARRIER_FUNCTION.getPrice(VANILLA_PUT_K100, BARRIER_DOWN_IN, REBATE, SPOT, costOfCarry, rateDomestic, VOLATILITY);
            final double pxDownOutPut = BARRIER_FUNCTION.getPrice(VANILLA_PUT_K100, BARRIER_DOWN_OUT, REBATE, SPOT, costOfCarry, rateDomestic, VOLATILITY);
            assertTrue("Knock In-Out Parity fails", Math.abs((pxVanillaPut + pxRebate) / (pxDownInPut + pxDownOutPut) - 1) < 1.e-6);

            final double pxUpInCall = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_UP_IN, REBATE, SPOT, costOfCarry, rateDomestic, VOLATILITY);
            final double pxUpOutCall = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_UP_OUT, REBATE, SPOT, costOfCarry, rateDomestic, VOLATILITY);
            assertTrue("Knock In-Out Parity fails", Math.abs((pxVanillaCall + pxRebate) / (pxUpInCall + pxUpOutCall) - 1) < 1.e-6);

            final double pxUpInPut = BARRIER_FUNCTION.getPrice(VANILLA_PUT_K100, BARRIER_UP_IN, REBATE, SPOT, costOfCarry, rateDomestic, VOLATILITY);
            final double pxUpOutPut = BARRIER_FUNCTION.getPrice(VANILLA_PUT_K100, BARRIER_UP_OUT, REBATE, SPOT, costOfCarry, rateDomestic, VOLATILITY);
            assertTrue("Knock In-Out Parity fails", Math.abs((pxVanillaPut + pxRebate) / (pxUpInPut + pxUpOutPut) - 1) < 1.e-6);

            // Let's try the Up case with Barrier < Strike. To do this, I create a new vanilla with K120 (> Barrier110)
            final Function1D<BlackFunctionData, Double> fcnVanillaPutHiK = BLACK_FUNCTION.getPriceFunction(VANILLA_PUT_KHI);
            final double pxVanillaPutHiK = fcnVanillaPutHiK.evaluate(zeroRatesMarket);

            final double pxUpInPutHiK = BARRIER_FUNCTION.getPrice(VANILLA_PUT_KHI, BARRIER_UP_IN, REBATE, SPOT, costOfCarry, rateDomestic, VOLATILITY);
            final double pxUpOutPutHiK = BARRIER_FUNCTION.getPrice(VANILLA_PUT_KHI, BARRIER_UP_OUT, REBATE, SPOT, costOfCarry, rateDomestic, VOLATILITY);
            assertTrue("Knock In-Out Parity fails", Math.abs((pxVanillaPutHiK + pxRebate) / (pxUpInPutHiK + pxUpOutPutHiK) - 1) < 1.e-6);
        }

        @Test
        /** Tests the 'In-Out Parity' condition: The price of a Knock-In plus a Knock-Out of arbitrary barrier level must equal that of the underlying vanilla option + value of the rebate */
        public void impossibleToHitBarrierIsVanilla() {

            final Barrier veryLowKnockIn = new Barrier(Barrier.KnockType.IN, Barrier.BarrierType.DOWN, Barrier.ObservationType.CONTINUOUS, 1e-6);
            final Barrier veryLowKnockOut = new Barrier(Barrier.KnockType.OUT, Barrier.BarrierType.DOWN, Barrier.ObservationType.CONTINUOUS, 1e-6);
            final Barrier veryHighKnockIn = new Barrier(Barrier.KnockType.IN, Barrier.BarrierType.UP, Barrier.ObservationType.CONTINUOUS, 1e6);
            final Barrier veryHighKnockOut = new Barrier(Barrier.KnockType.OUT, Barrier.BarrierType.UP, Barrier.ObservationType.CONTINUOUS, 1e6);

            final double pxRebate = DF_DOM * REBATE;
            final Function1D<BlackFunctionData, Double> fcnVanillaCall = BLACK_FUNCTION.getPriceFunction(VANILLA_CALL_K100);
            final double pxVanillaCall = fcnVanillaCall.evaluate(BLACK_FUNCTION_DATA);

            // KnockIn's with impossible to reach barrier's are guaranteed to pay the rebate at maturity
            final double pxDownInPut = BARRIER_FUNCTION.getPrice(VANILLA_PUT_K100, veryLowKnockIn, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            assertTrue("VeryLowKnockInBarrier doesn't match rebate", pxDownInPut / pxRebate - 1 < 1e-6);
            final double pxDownInCall = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, veryLowKnockIn, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            assertTrue("VeryLowKnockInBarrier doesn't match rebate", pxDownInCall / pxRebate - 1 < 1e-6);
            final double pxUpInCall = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, veryHighKnockIn, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            assertTrue("VeryHighKnockInBarrier doesn't match rebate", pxUpInCall / pxRebate - 1 < 1e-6);

            // KnockOut's with impossible to reach barrier's are guaranteed to pay the value of the underlying vanilla
            final double pxDownOutCall = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, veryLowKnockOut, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            assertTrue("VeryLowKnockInBarrier doesn't match rebate", Math.abs(pxDownOutCall / pxVanillaCall - 1) < 1e-6);

            // Derivatives
            final double[] derivs = new double[5];
            BARRIER_FUNCTION.getPriceAdjoint(VANILLA_CALL_K100, veryLowKnockIn, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY, derivs);
            assertTrue("Impossible KnockIn: rate sens is incorrect", derivs[2] / Math.abs((-1 * EXPIRY_TIME_Interval * DF_DOM * REBATE) - 1) < 1e-6);
            assertEquals("Impossible KnockIn: Encountered derivative, other than d/dr, != 0", 0.0, derivs[0] + derivs[1] + derivs[3] + derivs[4], 1.0e-6);

            BARRIER_FUNCTION.getPriceAdjoint(VANILLA_CALL_K100, veryHighKnockIn, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY, derivs);
            assertTrue("Impossible KnockIn: rate sens is incorrect", derivs[2] / Math.abs((-1 * EXPIRY_TIME_Interval * DF_DOM * REBATE) - 1) < 1e-6);
            assertEquals("Impossible KnockIn: Encountered derivative, other than d/dr, != 0", 0.0, derivs[0] + derivs[1] + derivs[3] + derivs[4], 1.0e-6);

            // Barrier: [0] spot, [1] strike, [2] rate, [3] cost-of-carry, [4] volatility.
            BARRIER_FUNCTION.getPriceAdjoint(VANILLA_CALL_K100, veryLowKnockOut, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY, derivs);
            // Vanilla: [0] the price, [1] the derivative with respect to the forward, [2] the derivative with respect to the volatility and [3] the derivative with respect to the strike.
            final double[] vanillaDerivs = BLACK_FUNCTION.getPriceAdjoint(VANILLA_CALL_K100, BLACK_FUNCTION_DATA);
            assertEquals("Impossible KnockOut: Vega doesn't match vanilla", vanillaDerivs[2], derivs[4], 1e-6);
            assertEquals("Impossible KnockOut: Dual Delta (d/dK) doesn't match vanilla", vanillaDerivs[3], derivs[1], 1e-6);
            assertEquals("Impossible KnockOut: Delta doesn't match vanilla", vanillaDerivs[1] * DF_FOR / DF_DOM, derivs[0], 1e-6);

            BARRIER_FUNCTION.getPriceAdjoint(VANILLA_CALL_K100, veryHighKnockOut, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY, derivs);
            assertEquals("Impossible KnockOut: Vega doesn't match vanilla", vanillaDerivs[2], derivs[4], 1e-6);
            assertEquals("Impossible KnockOut: Dual Delta (d/dK) doesn't match vanilla", vanillaDerivs[3], derivs[1], 1e-6);
            assertEquals("Impossible KnockOut: Delta doesn't match vanilla", vanillaDerivs[1] * DF_FOR / DF_DOM, derivs[0], 1e-6);
        }

        @Test
        /**
         * Tests the comparison with the other implementation. This test may be removed when only one version remains.
         */
        public void comparison() {
            final AnalyticOptionModel<EuropeanStandardBarrierOptionDefinition, StandardOptionDataBundle> model = new EuropeanStandardBarrierOptionModel();
            final StandardOptionDataBundle data = new StandardOptionDataBundle(YieldCurve.from(ConstantDoublesCurve.from(RATE_DOM)), COST_OF_CARRY, new VolatilitySurface(
                    ConstantDoublesSurface.from(VOLATILITY)),
                    SPOT, REFERENCE_DATE);
            final Expiry expiry = new Expiry(EXPIRY_DATE);

            final double priceDI1 = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_IN, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            final EuropeanStandardBarrierOptionDefinition optionBarrierDI = new EuropeanStandardBarrierOptionDefinition(STRIKE_100, expiry, IS_CALL, BARRIER_DOWN_IN, REBATE);
            final double priceDI2 = model.getPricingFunction(optionBarrierDI).evaluate(data);
            assertEquals("Comparison Down In", priceDI2, priceDI1, 1.0E-10);

            final double priceDO1 = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_OUT, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            final EuropeanStandardBarrierOptionDefinition optionBarrierDO = new EuropeanStandardBarrierOptionDefinition(STRIKE_100, expiry, IS_CALL, BARRIER_DOWN_OUT, REBATE);
            final double priceDO2 = model.getPricingFunction(optionBarrierDO).evaluate(data);
            assertEquals("Comparison Down Out", priceDO2, priceDO1, 1.0E-10);

            final double priceUI1 = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_UP_IN, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            final EuropeanStandardBarrierOptionDefinition optionBarrierUI = new EuropeanStandardBarrierOptionDefinition(STRIKE_100, expiry, IS_CALL, BARRIER_UP_IN, REBATE);
            final double priceUI2 = model.getPricingFunction(optionBarrierUI).evaluate(data);
            assertEquals("Comparison Up In", priceUI2, priceUI1, 1.0E-10);

            final double priceUO1 = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_UP_OUT, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            final EuropeanStandardBarrierOptionDefinition optionBarrierUO = new EuropeanStandardBarrierOptionDefinition(STRIKE_100, expiry, IS_CALL, BARRIER_UP_OUT, REBATE);
            final double priceUO2 = model.getPricingFunction(optionBarrierUO).evaluate(data);
            assertEquals("Comparison Up Out", priceUO2, priceUO1, 1.0E-10);

            final double vol0 = 0.0;
            final double priceVol01 = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_IN, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, vol0);
            final StandardOptionDataBundle data0 = new StandardOptionDataBundle(YieldCurve.from(ConstantDoublesCurve.from(RATE_DOM)), COST_OF_CARRY, new VolatilitySurface(ConstantDoublesSurface.from(vol0)),
                    SPOT,
                    REFERENCE_DATE);
            final double priceVol02 = model.getPricingFunction(optionBarrierDI).evaluate(data0);
            assertEquals(priceVol02, priceVol01, 1.0E-10);
        }
/*
        @Test(expectedExceptions = IllegalArgumentException.class)
        public void exceptionDown() {
            BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_IN, REBATE, 85.0, COST_OF_CARRY, RATE_DOM, VOLATILITY);
        }

        @Test(expectedExceptions = IllegalArgumentException.class)
        public void exceptionUp() {
            final Barrier barrierUp = new Barrier(Barrier.KnockType.IN, Barrier.BarrierType.UP, Barrier.ObservationType.CONTINUOUS, 90);
            BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, barrierUp, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY);
        }
*/
        @Test
        /**
         * Tests the adjoint implementation (with computation of the derivatives).
         */
        public void adjointPrice() {
            final double[] derivatives = new double[5];
            final double priceDI = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_IN, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            final double priceDIAdjoint = BARRIER_FUNCTION.getPriceAdjoint(VANILLA_CALL_K100, BARRIER_DOWN_IN, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY, derivatives);
            assertEquals("Black single barrier: Adjoint price Down In", priceDI, priceDIAdjoint, 1.0E-10);
            final double priceDO = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_OUT, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            final double priceDOAdjoint = BARRIER_FUNCTION.getPriceAdjoint(VANILLA_CALL_K100, BARRIER_DOWN_OUT, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY, derivatives);
            assertEquals("Black single barrier: Adjoint price Down Out", priceDO, priceDOAdjoint, 1.0E-10);
            final double priceUI = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_UP_IN, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            final double priceUIAdjoint = BARRIER_FUNCTION.getPriceAdjoint(VANILLA_CALL_K100, BARRIER_UP_IN, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY, derivatives);
            assertEquals("Black single barrier: Adjoint price Up In", priceUI, priceUIAdjoint, 1.0E-10);
            final double priceUO = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_UP_OUT, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            final double priceUOAdjoint = BARRIER_FUNCTION.getPriceAdjoint(VANILLA_CALL_K100, BARRIER_UP_OUT, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY, derivatives);
            assertEquals("Black single barrier: Adjoint price Up Out", priceUO, priceUOAdjoint, 1.0E-10);
        }

        @Test
        /**
         * Tests the adjoint implementation (with computation of the derivatives).
         */
        public void adjointDerivatives() {
            final double shiftSpot = 0.001;
            final double shiftRate = 1.0E-8;
            final double shiftCoC = 1.0E-8;
            final double shiftVol = 1.0E-8;
            final double[] derivatives = new double[5];
            // DOWN-IN
            final double priceDI = BARRIER_FUNCTION.getPriceAdjoint(VANILLA_CALL_K100, BARRIER_DOWN_IN, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY, derivatives);
            final double priceDISpot = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_IN, REBATE, SPOT + shiftSpot, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            assertEquals("Black single barrier: Adjoint spot derivative - Down In", (priceDISpot - priceDI) / shiftSpot, derivatives[0], 1.0E-5);
            final double priceDIRate = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_IN, REBATE, SPOT, COST_OF_CARRY, RATE_DOM + shiftRate, VOLATILITY);
            assertEquals("Black single barrier: Adjoint rate derivative - Down In", (priceDIRate - priceDI) / shiftRate, derivatives[2], 1.0E-5);
            final double priceDICoC = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_IN, REBATE, SPOT, COST_OF_CARRY + shiftCoC, RATE_DOM, VOLATILITY);
            assertEquals("Black single barrier: Adjoint cost-of-carry derivative - Down In", (priceDICoC - priceDI) / shiftCoC, derivatives[3], 1.0E-5);
            final double priceDIVol = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_IN, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY + shiftVol);
            assertEquals("Black single barrier: Adjoint cost-of-carry derivative - Down In", (priceDIVol - priceDI) / shiftVol, derivatives[4], 1.0E-4);
            // DOWN-OUT
            final double priceDO = BARRIER_FUNCTION.getPriceAdjoint(VANILLA_CALL_K100, BARRIER_DOWN_OUT, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY, derivatives);
            final double priceDOSpot = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_OUT, REBATE, SPOT + shiftSpot, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            assertEquals("Black single barrier: Adjoint spot derivative - Down Out", (priceDOSpot - priceDO) / shiftSpot, derivatives[0], 2.0E-4);
            final double priceDORate = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_OUT, REBATE, SPOT, COST_OF_CARRY, RATE_DOM + shiftRate, VOLATILITY);
            assertEquals("Black single barrier: Adjoint rate derivative - Down Out", (priceDORate - priceDO) / shiftRate, derivatives[2], 1.0E-5);
            final double priceDOCoC = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_OUT, REBATE, SPOT, COST_OF_CARRY + shiftCoC, RATE_DOM, VOLATILITY);
            assertEquals("Black single barrier: Adjoint cost-of-carry derivative - Down Out", (priceDOCoC - priceDO) / shiftCoC, derivatives[3], 1.0E-4);
            final double priceDOVol = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_DOWN_OUT, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY + shiftVol);
            assertEquals("Black single barrier: Adjoint cost-of-carry derivative - Down Out", (priceDOVol - priceDO) / shiftVol, derivatives[4], 1.0E-4);
            // UP-IN
            final double priceUI = BARRIER_FUNCTION.getPriceAdjoint(VANILLA_CALL_K100, BARRIER_UP_IN, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY, derivatives);
            final double priceUISpot = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_UP_IN, REBATE, SPOT + shiftSpot, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            assertEquals("Black single barrier: Adjoint spot derivative - Up In", (priceUISpot - priceUI) / shiftSpot, derivatives[0], 2.0E-4);
            final double priceUIRate = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_UP_IN, REBATE, SPOT, COST_OF_CARRY, RATE_DOM + shiftRate, VOLATILITY);
            assertEquals("Black single barrier: Adjoint rate derivative - Up In", (priceUIRate - priceUI) / shiftRate, derivatives[2], 1.0E-5);
            final double priceUICoC = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_UP_IN, REBATE, SPOT, COST_OF_CARRY + shiftCoC, RATE_DOM, VOLATILITY);
            assertEquals("Black single barrier: Adjoint cost-of-carry derivative - Up In", (priceUICoC - priceUI) / shiftCoC, derivatives[3], 1.0E-4);
            final double priceUIVol = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_UP_IN, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY + shiftVol);
            assertEquals("Black single barrier: Adjoint cost-of-carry derivative - Up In", (priceUIVol - priceUI) / shiftVol, derivatives[4], 1.0E-5);
            // UP-OUT
            final double priceUO = BARRIER_FUNCTION.getPriceAdjoint(VANILLA_CALL_K100, BARRIER_UP_OUT, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY, derivatives);
            final double priceUOSpot = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_UP_OUT, REBATE, SPOT + shiftSpot, COST_OF_CARRY, RATE_DOM, VOLATILITY);
            assertEquals("Black single barrier: Adjoint spot derivative - Up Out", (priceUOSpot - priceUO) / shiftSpot, derivatives[0], 1.0E-4);
            final double priceUORate = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_UP_OUT, REBATE, SPOT, COST_OF_CARRY, RATE_DOM + shiftRate, VOLATILITY);
            assertEquals("Black single barrier: Adjoint rate derivative - Up Out", (priceUORate - priceUO) / shiftRate, derivatives[2], 1.0E-5);
            final double priceUOCoC = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_UP_OUT, REBATE, SPOT, COST_OF_CARRY + shiftCoC, RATE_DOM, VOLATILITY);
            assertEquals("Black single barrier: Adjoint cost-of-carry derivative - Up Out", (priceUOCoC - priceUO) / shiftCoC, derivatives[3], 1.0E-5);
            final double priceUOVol = BARRIER_FUNCTION.getPrice(VANILLA_CALL_K100, BARRIER_UP_OUT, REBATE, SPOT, COST_OF_CARRY, RATE_DOM, VOLATILITY + shiftVol);
            assertEquals("Black single barrier: Adjoint cost-of-carry derivative - Up Out", (priceUOVol - priceUO) / shiftVol, derivatives[4], 2.0E-5);
        }

    }

