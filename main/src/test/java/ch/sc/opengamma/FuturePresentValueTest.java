package ch.sc.opengamma;

import com.opengamma.analytics.financial.commodity.definition.AgricultureFutureDefinition;
import com.opengamma.analytics.financial.commodity.definition.SettlementType;
import com.opengamma.analytics.financial.commodity.derivative.AgricultureFuture;
import com.opengamma.analytics.financial.equity.future.derivative.EquityFuture;
import com.opengamma.analytics.financial.equity.future.derivative.EquityIndexDividendFuture;
import com.opengamma.analytics.financial.future.MarkToMarketFuturesCalculator;
import com.opengamma.analytics.financial.simpleinstruments.pricing.SimpleFutureDataBundle;
import com.opengamma.id.ExternalId;
import com.opengamma.util.money.Currency;
import com.opengamma.util.time.DateUtils;
import org.junit.Test;
import org.threeten.bp.ZonedDateTime;

import static org.junit.Assert.*;

/**
 * Created by Alexis on 01.05.15.
 */
public class FuturePresentValueTest {
    private static final MarkToMarketFuturesCalculator PVC = MarkToMarketFuturesCalculator.PresentValueCalculator.getInstance();

    @Test
    public void testEquityIndexDividendFuture() {
        final double settlement = 1.45;
        final double fixing = 1.44;
        /**
         * A cash-settled futures contract on the index of the *dividends* of a given stock market index on the _timeToFixing
         */
        final EquityIndexDividendFuture eidf = new EquityIndexDividendFuture(fixing, settlement, 95., Currency.JPY, 10);
        final double currentPrice = 100.0;
        /**
         * Market data requirements for pricing the SimpleFuture and EquityFuture.<p>
         * NOTE: Each EquityFuturesPricingMethod requires different data.
         * Some members of the data bundle may be null!
         */
        final SimpleFutureDataBundle dataBundle = new SimpleFutureDataBundle(null, currentPrice, null, null, null);
        //Act
        // FIXME Case - presentValue needs discounting..
        final double pv = eidf.accept(PVC, dataBundle);
        // same result
        //final double pv1 = PVC.visitEquityFuture(eidf,dataBundle);
        //Expected?
        final double expectedPV = eidf.getUnitAmount()*(dataBundle.getMarketPrice()-eidf.getReferencePrice());
        assertEquals(expectedPV, pv, 1e-12);
    }

    @Test
    public void testEquityFuture(){
        final double fixing = 1.44;
        final double timeToDelivery = 1.45;
        final double strike = 95;
        Currency cur = Currency.EUR;
        final double unitValue = 10;// Price per tick
    /**
     *
     * @param timeToExpiry    time (in years as a double) until the date-time at which the reference index is fixed
     * @param timeToSettlement  time (in years as a double) until the date-time at which the contract is settled
     * @param strike         Set strike price at trade time. Note that we may handle margin by resetting this at the end of each trading day
     * @param currency       The reporting currency of the future
     *  @param unitAmount    The unit value per tick, in given currency
     */
        EquityFuture equityFuture = new EquityFuture(fixing,timeToDelivery,strike,cur,unitValue);

        final double currentPrice = 100.0;
        /**
         * Market data requirements for pricing the SimpleFuture and EquityFuture.<p>
         * NOTE: Each EquityFuturesPricingMethod requires different data.
         * Some members of the data bundle may be null!
         */
        final SimpleFutureDataBundle dataBundle = new SimpleFutureDataBundle(null, currentPrice, null, null, null);

        //Act
        final double  pv = PVC.visitEquityFuture(equityFuture,dataBundle);

        // Expected
        final double expectedPV = equityFuture.getUnitAmount()*(dataBundle.getMarketPrice()-equityFuture.getReferencePrice());
        assertEquals(expectedPV, pv, 1e-12);
    }



    private final static ExternalId AN_UNDERLYING= ExternalId.of("Scheme", "value");
    private final static ZonedDateTime FIRST_DELIVERY_DATE = DateUtils.getUTCDate(2011, 9, 21);
    private final static ZonedDateTime LAST_DELIVERY_DATE = DateUtils.getUTCDate(2012, 9, 21);
    private final static ZonedDateTime SETTLEMENT_DATE = LAST_DELIVERY_DATE;
    private final static ZonedDateTime EXPIRY_DATE = DateUtils.getUTCDate(2011, 9, 21);
    private final static ZonedDateTime A_DATE = DateUtils.getUTCDate(2011, 9, 20);
    private final static ZonedDateTime REF_DATE = DateUtils.getUTCDate(2011, 6, 15);

    @Test
    public void testAgricultureFuture()
    {
        /**
         * Constructor for futures
         *
         * @param expiryDate  the time and the day that a particular delivery month of a forwards contract stops trading, as well as the final settlement price for that contract
         * @param underlying  identifier of the underlying commodity
         * @param unitAmount  size of a unit
         * @param firstDeliveryDate  date of first delivery - PHYSICAL settlement
         * @param lastDeliveryDate  date of last delivery - PHYSICAL settlement
         * @param amount  number of units
         * @param unitName  description of unit size
         * @param settlementType  settlement type - PHYSICAL or CASH
         * @param referencePrice reference price
         * @param currency currency
         * @param settlementDate settlement date
         */
        final double unitAmount = 101;
        final double amount = 500;
        final double refPrice = 100;
        AgricultureFutureDefinition futureDefinition = new AgricultureFutureDefinition(EXPIRY_DATE, AN_UNDERLYING, unitAmount, FIRST_DELIVERY_DATE, LAST_DELIVERY_DATE, amount, "tonnes", SettlementType.PHYSICAL, refPrice,
                Currency.GBP, SETTLEMENT_DATE);
        AgricultureFuture future = futureDefinition.toDerivative(REF_DATE);

        final double currentPrice = 70.0;
        /**
         * Market data requirements for pricing the SimpleFuture and EquityFuture.<p>
         * NOTE: Each EquityFuturesPricingMethod requires different data.
         * Some members of the data bundle may be null!
         */
        final SimpleFutureDataBundle dataBundle = new SimpleFutureDataBundle(null, currentPrice, null, null, null);
        // Act
        final double pv =PVC.visitAgricultureFuture(future,dataBundle);
        //Expected
        final double expectedPV = future.getUnitAmount()*(dataBundle.getMarketPrice()-future.getReferencePrice());
        //Assert
        assertEquals(expectedPV,pv,1E-12);
    }
}
