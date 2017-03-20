package dburyak.pi.rxpi;


import static com.pi4j.io.gpio.PinEdge.FALLING;
import static com.pi4j.io.gpio.PinEdge.RISING;
import static com.pi4j.io.gpio.PinPullResistance.PULL_DOWN;
import static com.pi4j.io.gpio.PinState.LOW;
import static com.pi4j.wiringpi.Gpio.INT_EDGE_BOTH;
import static com.pi4j.wiringpi.Gpio.INT_EDGE_FALLING;
import static com.pi4j.wiringpi.Gpio.INT_EDGE_RISING;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinAnalogInput;
import com.pi4j.io.gpio.GpioPinAnalogOutput;
import com.pi4j.io.gpio.GpioPinDigital;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.event.GpioPinAnalogValueChangeEvent;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerAnalog;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;


/**
 * Project : rxpi<p>
 * Raspberry Pi GPIO access helper.
 * <p><b>Created on:</b> <i>4:07:16 AM Feb 28, 2017</i>
 * 
 * @author <i>Dmytro Buryak &lt;dmytro.buryak@gmail.com&gt;</i>
 * @version 0.1
 */
public final class RxGpio {

    /**
     * Default system logger.
     * <p><b>Created on:</b> <i>4:22:14 PM Mar 3, 2017</i>
     */
    private static final Logger LOG = LogManager.getFormatterLogger(RxGpio.class);


    /**
     * Project : rxpi<p>
     * Interrupt type user can subscribe to.
     * <p><b>Created on:</b> <i>12:23:03 AM Mar 6, 2017</i>
     * 
     * @author <i>Dmytro Buryak &lt;dmytro.buryak@gmail.com&gt;</i>
     * @version 0.1
     */
    public static enum InterruptType {

            /**
             * Logical level rising events.
             * <p><b>Created on:</b> <i>12:23:28 AM Mar 6, 2017</i>
             */
            EDGE_RISING(INT_EDGE_RISING),

            /**
             * Logical level falling events.
             * <p><b>Created on:</b> <i>12:24:00 AM Mar 6, 2017</i>
             */
            EDGE_FALLING(INT_EDGE_FALLING),

            /**
             * Both logical level rising and falling events.
             * <p><b>Created on:</b> <i>12:24:13 AM Mar 6, 2017</i>
             */
            EDGE_BOTH(INT_EDGE_BOTH);

        /**
         * Corresponding Pi4J (wiringPI actually) int constant.
         * <p><b>Created on:</b> <i>12:25:41 AM Mar 6, 2017</i>
         */
        private final int pi4jValue;


        /**
         * Constructor for class : [jitsi] net.java.sip.communicator.plugin.pickuper.rpi.InterruptType.<p>
         * <p><b>PRE-conditions:</b> NONE
         * <br><b>Side-effects:</b> NONE
         * <br><b>Created on:</b> <i>12:27:44 AM Mar 6, 2017</i>
         * 
         * @param pi4jValue
         *            corresponding underlying Pi4J interrupt type value
         */
        private InterruptType(final int pi4jValue) {
            this.pi4jValue = pi4jValue;
        }

        /**
         * Get corresponding underlying Pi4J interrupt type value.
         * <p><b>PRE-conditions:</b> NONE
         * <br><b>POST-conditions:</b> NONE
         * <br><b>Side-effects:</b> NONE
         * <br><b>Created on:</b> <i>12:28:10 AM Mar 6, 2017</i>
         * 
         * @return corresponding underlying Pi4J interrupt type value
         */
        public final int pi4jValue() {
            return pi4jValue;
        }
    }


    /**
     * Project : rxpi<p>
     * Instance holder for single {@link RxGpio} instance.
     * <p><b>Created on:</b> <i>4:22:19 PM Mar 3, 2017</i>
     * 
     * @author <i>Dmytro Buryak &lt;dmytro.buryak@gmail.com&gt;</i>
     * @version 0.1
     */
    private static final class InstanceHolder {

        /**
         * Single instance of {@link RxGpio} class.
         * <p><b>Created on:</b> <i>4:22:47 PM Mar 3, 2017</i>
         */
        private static final RxGpio INSTANCE = new RxGpio();
    }


    /**
     * Get single instance of {@link RxGpio} class.
     * <p><b>PRE-conditions:</b> NONE
     * <br><b>POST-conditions:</b> non-null result
     * <br><b>Side-effects:</b> {@link RxGpio} instance is instantiated on the first call
     * <br><b>Created on:</b> <i>4:23:21 PM Mar 3, 2017</i>
     * 
     * @return single {@link RxGpio} instance
     */
    public static final RxGpio instance() {
        return InstanceHolder.INSTANCE;
    }


    /**
     * Provisioned and registered digital output pins.
     * <p><b>Created on:</b> <i>4:16:36 PM Mar 3, 2017</i>
     */
    private final ConcurrentMap<Pin, GpioPinDigitalInput> digitalInputPins = new ConcurrentHashMap<>();

    /**
     * Provisioned and registered analog input pins.
     * <p><b>Created on:</b> <i>4:17:42 PM Mar 3, 2017</i>
     */
    private final ConcurrentMap<Pin, GpioPinAnalogInput> analogInputPins = new ConcurrentHashMap<>();

    /**
     * Provisioned digital output pins.
     * <p><b>Created on:</b> <i>5:18:48 PM Mar 3, 2017</i>
     */
    private final ConcurrentMap<Pin, GpioPinDigitalOutput> digitalOutPins = new ConcurrentHashMap<>();

    /**
     * Provisioned analog output pins.
     * <p><b>Created on:</b> <i>5:21:40 PM Mar 3, 2017</i>
     */
    private final ConcurrentMap<Pin, GpioPinAnalogOutput> analogOutPins = new ConcurrentHashMap<>();

    /**
     * Composite disposable tracking all managed subscriptions.
     * <p><b>Created on:</b> <i>3:10:14 AM Mar 16, 2017</i>
     */
    private final CompositeDisposable disposables = new CompositeDisposable();

    /**
     * GPIO controller service single instance.
     * <p><b>Created on:</b> <i>2:39:28 AM Feb 28, 2017</i>
     */
    private final GpioController gpio;

    /**
     * Active indicator for this class. Used for internal resource allocation/disposal.
     * <p><b>Created on:</b> <i>4:18:35 PM Mar 3, 2017</i>
     */
    private final AtomicBoolean isActive = new AtomicBoolean(true);


    /**
     * Constructor for class : [jitsi] net.java.sip.communicator.plugin.pickuper.rpi.RxGpio.<p>
     * <p><b>PRE-conditions:</b> NONE
     * <br><b>Side-effects:</b> {@link GpioFactory} call
     * <br><b>Created on:</b> <i>4:19:36 PM Mar 3, 2017</i>
     */
    private RxGpio() {
        gpio = GpioFactory.getInstance();
    }

    /**
     * Test whether this {@link RxGpio} instance is in active state.
     * <p><b>PRE-conditions:</b> NONE
     * <br><b>POST-conditions:</b> NONE
     * <br><b>Side-effects:</b> NONE
     * <br><b>Created on:</b> <i>4:19:39 PM Mar 3, 2017</i>
     */
    @SuppressWarnings("nls")
    private final void assertInActiveState() {
        if (!isActive.get()) {
            throw new IllegalStateException("is not active");
        }
    }

    /**
     * Assert that {@code pin} is not already provisioned nor used as digital input.
     * <p><b>PRE-conditions:</b> non-null {@code pin}
     * <br><b>POST-conditions:</b> NONE
     * <br><b>Side-effects:</b> NONE
     * <br><b>Created on:</b> <i>2:55:35 PM Mar 4, 2017</i>
     * 
     * @param pin
     *            pin to test
     */
    @SuppressWarnings("nls")
    private final void assertNotDigitalInput(final Pin pin) {
        if (digitalInputPins.containsKey(pin)) {
            throw new IllegalArgumentException("pin is already used as digital input : pin = [" + pin + "]");
        }
    }

    /**
     * Assert that {@code pin} is not already provisioned nor used as analog input.
     * <p><b>PRE-conditions:</b> non-null {@code pin}
     * <br><b>POST-conditions:</b> NONE
     * <br><b>Side-effects:</b> NONE
     * <br><b>Created on:</b> <i>5:48:54 PM Mar 4, 2017</i>
     * 
     * @param pin
     *            pin to test
     */
    @SuppressWarnings("nls")
    private final void assertNotAnalogInput(final Pin pin) {
        if (analogInputPins.containsKey(pin)) {
            throw new IllegalArgumentException("pin is already used as analog input : pin = [" + pin + "]");
        }
    }

    /**
     * Assert that {@code pin} is not already provisioned and used as digital output.
     * <p><b>PRE-conditions:</b> non-null {@code pin}
     * <br><b>POST-conditions:</b> NONE
     * <br><b>Side-effects:</b> NONE
     * <br><b>Created on:</b> <i>5:49:48 PM Mar 4, 2017</i>
     * 
     * @param pin
     *            pin to test
     */
    @SuppressWarnings("nls")
    private final void assertNotDigitalOut(final Pin pin) {
        if (digitalOutPins.containsKey(pin)) {
            throw new IllegalArgumentException("pin is already used as digital out : pin = [" + pin + "]");
        }
    }

    /**
     * Assert that {@code pin} is not already provisioned nor used as analog output.
     * <p><b>PRE-conditions:</b> non-null {@code pin}
     * <br><b>POST-conditions:</b> NONE
     * <br><b>Side-effects:</b> NONE
     * <br><b>Created on:</b> <i>5:50:30 PM Mar 4, 2017</i>
     * 
     * @param pin
     *            pin to test
     */
    @SuppressWarnings("nls")
    private final void assertNotAnalogOut(final Pin pin) {
        if (analogOutPins.containsKey(pin)) {
            throw new IllegalArgumentException("pin is already used as analog out : pin = [" + pin + "]");
        }
    }


    /**
     * Project : rxpi<p>
     * Pin types this manager works with.
     * <p><b>Created on:</b> <i>6:57:02 PM Mar 4, 2017</i>
     * 
     * @author <i>Dmytro Buryak &lt;dmytro.buryak@gmail.com&gt;</i>
     * @version 0.1
     */
    private static enum PinType {
            /**
             * Digital input.
             * <p><b>Created on:</b> <i>7:04:00 PM Mar 4, 2017</i>
             */
            INPUT_DIGITAL,

            /**
             * Analog input.
             * <p><b>Created on:</b> <i>7:04:06 PM Mar 4, 2017</i>
             */
            INPUT_ANALOG,

            /**
             * Digital output.
             * <p><b>Created on:</b> <i>7:04:14 PM Mar 4, 2017</i>
             */
            OUT_DIGITAL,

            /**
             * Analog output.
             * <p><b>Created on:</b> <i>7:04:23 PM Mar 4, 2017</i>
             */
            OUT_ANALOG;
    }


    /**
     * Check that {@code pin} is not provisioned and used as any other pin type.
     * <p><b>PRE-conditions:</b> non-null {@pin}, non-null {@code type}
     * <br><b>POST-conditions:</b> NONE
     * <br><b>Side-effects:</b> NONE
     * <br><b>Created on:</b> <i>6:57:19 PM Mar 4, 2017</i>
     * 
     * @param pin
     *            pin to test
     * @param type
     *            pin type
     */
    private final void assertNotOccupiedByOthers(final Pin pin, final PinType type) {
        if (type != PinType.INPUT_DIGITAL) {
            assertNotDigitalInput(pin);
        }
        if (type != PinType.INPUT_ANALOG) {
            assertNotAnalogInput(pin);
        }
        if (type != PinType.OUT_DIGITAL) {
            assertNotDigitalOut(pin);
        }
        if (type != PinType.OUT_ANALOG) {
            assertNotAnalogOut(pin);
        }
    }

    /**
     * Provision pin as digital input and register it. If pin is already provisioned and registered, then it is
     * returned.
     * <p><b>PRE-conditions:</b> non-null {@code pin}
     * <br><b>POST-conditions:</b> non-null result
     * <br><b>Side-effects:</b> Pi4J GPIO call
     * <br><b>Created on:</b> <i>12:08:29 AM Mar 6, 2017</i>
     * 
     * @param pin
     *            pin to get as digital input
     * @return provisioned digital input pin
     */
    @SuppressWarnings("nls")
    private final GpioPinDigitalInput initOrGetPinDigitalInput(final Pin pin) {
        return digitalInputPins.computeIfAbsent(pin, pinKey -> {
            assertNotOccupiedByOthers(pinKey, PinType.INPUT_DIGITAL);
            try {
                return Stream.of(gpio.getProvisionedPin(pinKey))
                    .filter(p -> p != null)
                    .map(gpioPin -> (GpioPinDigitalInput) gpioPin) // ClassCastE if pin configured another way
                    .findAny()
                    .orElseGet(() -> {
                        LOG.debug("configuring digital input pin : pin = [%s]", pinKey);
                        // TODO : this should be set in separate config file, or passed as parameter
                        return gpio.provisionDigitalInputPin(pinKey, PULL_DOWN);
                    });
            } catch (final ClassCastException e) {
                throw new RuntimeException(
                    "trying to use already configured (other way) pin as analog intput : pin = [" + pin + "]", e);
            }
        });
    }

    /**
     * Get observable that emits events of provided {@link Pin}. Pin is initialized (provisioned, configured) only if it
     * was not configured yet.
     * <p><b>PRE-conditions:</b> non-null {@code pin}
     * <br><b>POST-conditions:</b> non-null result
     * <br><b>Side-effects:</b> GPIO access, {@link GpioController} state is modified
     * <br><b>Created on:</b> <i>3:21:59 AM Mar 3, 2017</i>
     * 
     * @param pin
     *            ping to listen digital events from
     * @return source of digital events for specified pin
     */
    @SuppressWarnings("nls")
    public final Observable<GpioPinDigitalStateChangeEvent> pinDigitalChangeSource(final Pin pin) {
        assertInActiveState();
        return Observable.create(new ObservableOnSubscribe<GpioPinDigitalStateChangeEvent>() {

            @Override
            public final void subscribe(final ObservableEmitter<GpioPinDigitalStateChangeEvent> emitter)
                throws Exception {

                final GpioPinDigitalInput pinInput = initOrGetPinDigitalInput(pin);
                pinInput.addListener(new GpioPinListenerDigital() {

                    @Override
                    public final void handleGpioPinDigitalStateChangeEvent(
                        final GpioPinDigitalStateChangeEvent event) {

                        if (!emitter.isDisposed()) {
                            emitter.onNext(event);
                        } else {
                            pinInput.removeListener(this);
                            emitter.onComplete();
                        }
                    }
                });
            }
        }).doOnNext(evnt -> LOG.trace("pin change event : pin = [%s] ; state = [%s]", pin, evnt.getState()));
    }

    /**
     * Get source of interrupt events for specified pin and interrupt type. This is convenient version of
     * {@link #pinDigitalChangeSource(Pin)} with filtered output.
     * <p><b>PRE-conditions:</b> non-null {@code pin}, non-null {@code intType}
     * <br><b>POST-conditions:</b> non-null result
     * <br><b>Side-effects:</b> Pi4J GPIO call, this object state is modified
     * <br><b>Created on:</b> <i>12:30:39 AM Mar 6, 2017</i>
     * 
     * @param pin
     *            pin to use as interrupts source
     * @param intType
     *            interrupt events type
     * @return observable source of state changes
     */
    @SuppressWarnings({ "nls" })
    public final Observable<GpioPinDigitalStateChangeEvent> pinInterruptsSource(
        final Pin pin,
        final InterruptType intType) {

        assertInActiveState();
        switch (intType) {
            case EDGE_BOTH:
                return pinDigitalChangeSource(pin);
            case EDGE_RISING:
                return pinDigitalChangeSource(pin)
                    .filter(state -> state.getEdge() == RISING);
            case EDGE_FALLING:
                return pinDigitalChangeSource(pin)
                    .filter(state -> state.getEdge() == FALLING);
            default:
                LOG.error("unexpected interrupt type received : intType = [%s]", intType);
                throw new IllegalArgumentException("unexpected interrupt type received : " + intType);
        }
    }

    /**
     * Provision and register pin as analog input pin. If it is already provisioned, then it is returned.
     * <p><b>PRE-conditions:</b> non-null {@code pin}
     * <br><b>POST-conditions:</b> non-null result
     * <br><b>Side-effects:</b> Pi4J GPIO call
     * <br><b>Created on:</b> <i>12:13:16 AM Mar 6, 2017</i>
     * 
     * @param pin
     *            pin to init and get
     * @return provisioned analog input pin
     */
    @SuppressWarnings("nls")
    private final GpioPinAnalogInput initOrGetPinAnalogInput(final Pin pin) {
        return analogInputPins.computeIfAbsent(pin, pinKey -> {
            assertNotOccupiedByOthers(pinKey, PinType.INPUT_ANALOG);
            try {
                return Stream.of(gpio.getProvisionedPin(pinKey))
                    .filter(p -> p != null)
                    .map(gpioPin -> (GpioPinAnalogInput) gpioPin) // ClassCastE if pin configured another way
                    .findAny()
                    .orElseGet(() -> {
                        LOG.debug("configuring analog input pin : pin = [%s]");
                        return gpio.provisionAnalogInputPin(pinKey);
                    });

            } catch (final ClassCastException e) {
                throw new RuntimeException(
                    "trying to use already configured (other way) pin as analog intput : pin = [" + pin + "]", e);
            }
        });
    }

    /**
     * Get observable that emits events of provided {@link Pin}. Pin is initialized (provisioned, configured) only if it
     * was not configured yet.
     * <p><b>PRE-conditions:</b> non-null {@code pin}
     * <br><b>POST-conditions:</b> non-null result
     * <br><b>Side-effects:</b> GPIO access, {@link GpioController} state is modified
     * <br><b>Created on:</b> <i>2:36:18 AM Mar 3, 2017</i>
     * 
     * @param pin
     *            pin to listen analog events from
     * @return source of analog events for specified pin
     */
    public final Observable<GpioPinAnalogValueChangeEvent> pinAnalogChangeSource(final Pin pin) {
        assertInActiveState();
        return Observable.create(new ObservableOnSubscribe<GpioPinAnalogValueChangeEvent>() {

            @Override
            public final void subscribe(final ObservableEmitter<GpioPinAnalogValueChangeEvent> emitter)
                throws Exception {

                final GpioPinAnalogInput pinInput = initOrGetPinAnalogInput(pin);
                pinInput.addListener(new GpioPinListenerAnalog() {

                    @Override
                    public final void handleGpioPinAnalogValueChangeEvent(final GpioPinAnalogValueChangeEvent event) {
                        if (!emitter.isDisposed()) {
                            emitter.onNext(event);
                        } else {
                            pinInput.removeListener(this);
                            emitter.onComplete();
                        }
                    }
                });
            }
        });
    }

    /**
     * Subscribe to provided pin state source : set state of provided digital output pin upon receiving pin state
     * from given observable.
     * <p>Subscription is not timely limited, so returned result subscription object should be used
     * if explicit unsubscription by calling code is needed.
     * <p><b>PRE-conditions:</b> non-null {@code pinDigitalStateSource}, non-null {@code pin}
     * <br><b>POST-conditions:</b> non-null result
     * <br><b>Side-effects:</b> {@code pinDigitalStateSource} is subscribed to, internal state of this object is
     * modified
     * <br><b>Created on:</b> <i>8:03:04 PM Mar 4, 2017</i>
     * 
     * @param pinDigitalStateSource
     *            source of digital pin state to handle
     * @param pin
     *            GPIO pin to set state
     * @param initialState
     *            initial digital pin state
     * @return result disposable for controlling subscription life cycle
     */
    @SuppressWarnings({ "nls", "boxing" })
    public final Disposable pinDigitalSubscribe(
        final Pin pin,
        final boolean initialState,
        final Observable<Boolean> pinDigitalStateSource) {

        final Disposable disp = pinDigitalStateSource
            .doOnNext(state -> LOG.trace("set digital out pin state : pin = [%s] ; state = [%s]", pin, state))
            .subscribe(state -> {
                final GpioPinDigitalOutput pinOut = digitalOutPins.computeIfAbsent(pin, pinKey -> {
                    LOG.debug("configuring digital output pin : pin = [%s]", pinKey);
                    final GpioPinDigitalOutput provisioned = gpio.provisionDigitalOutputPin(
                        pin, PinState.getState(initialState));
                    provisioned.setShutdownOptions(true, PinState.LOW, PinPullResistance.OFF);
                    return provisioned;
                });
                pinOut.setState(state);
            });
        disposables.add(disp);
        return disp;
    }

    /**
     * Subscribe to provided pin state source : set state of provided digital output pin upon receiving pin state
     * from given observable.
     * <p>Subscription is not timely limited, so returned result subscription object should be used
     * if explicit unsubscription by calling code is needed.
     * <p><b>PRE-conditions:</b> non-null {@code pinDigitalStateSource}, non-null {@code pin}
     * <br><b>POST-conditions:</b> non-null result
     * <br><b>Side-effects:</b>{@code pinDigitalStateSource} is subscribed to, internal state of this object is
     * modified
     * <br><b>Created on:</b> <i>2:13:01 PM Mar 5, 2017</i>
     * 
     * @param pinDigitalStateSource
     *            source of digital pin state to handle
     * @param pin
     *            GPIO pin to set state
     * @param initialState
     *            initial digital pin state
     * @return result disposable for controlling subscription life cycle
     */
    public final Disposable pinDigitalSubscribe(
        final Pin pin,
        final PinState initialState,
        final Observable<PinState> pinDigitalStateSource) {

        return pinDigitalSubscribe(
            pin,
            initialState.isHigh(),
            pinDigitalStateSource.map(PinState::isHigh));
    }

    /**
     * Subscribe to provided analog value source : set pin value of provided analog output pin upon receiving value from
     * given observable.
     * <p>Subscription is not timely limited, so returned result subscription object should be used
     * if explicit unsubscription by calling code is needed.
     * <p><b>PRE-conditions:</b> non-null {@code pinAnalogStateSource}, non-null {@code pin}
     * <br><b>POST-conditions:</b> non-null result
     * <br><b>Side-effects:</b> {@code pinAnalogStateSource} is subscribed to, internal state of this object is modified
     * <br><b>Created on:</b> <i>8:45:09 PM Mar 4, 2017</i>
     * 
     * @param pinAnalogStateSource
     *            source of analog pin values
     * @param pin
     *            GPIO pin to set state
     * @param initialValue
     *            initial analog pin value
     * @return result disposable for controlling subscription life cycle
     */
    @SuppressWarnings({ "nls", "boxing" })
    public final Disposable pinAnalogSubscribe(
        final Observable<Number> pinAnalogStateSource,
        final Pin pin,
        final double initialValue) {

        final Disposable disp = pinAnalogStateSource
            .doOnNext(value -> LOG.trace("set analog out pin state : pin = [%s] ; value = [%s]", pin, value))
            .subscribe(value -> {
                final GpioPinAnalogOutput pinOut = analogOutPins.computeIfAbsent(pin, pinKey -> {
                    LOG.debug("configuring analog output pin : pin = [%s]", pinKey);
                    final GpioPinAnalogOutput provisioned = gpio.provisionAnalogOutputPin(pin, initialValue);
                    provisioned.setShutdownOptions(true, LOW, PinPullResistance.OFF);
                    return provisioned;
                });
                pinOut.setValue(value);
            });
        disposables.add(disp);
        return disp;
    }

    /**
     * Clean up all associated resources and shut down GPIO, associated hardware is released.
     * <p><b>PRE-conditions:</b> NONE
     * <br><b>POST-conditions:</b> NONE
     * <br><b>Side-effects:</b> GPIO access
     * <br><b>Created on:</b> <i>2:35:14 AM Mar 3, 2017</i>
     */
    public final void shutdown() {
        assertInActiveState();
        disposables.dispose();
        gpio.removeAllListeners();
        gpio.removeAllTriggers();
        gpio.shutdown();
    }

    /**
     * Create {@link GpioPinDigitalStateChangeEvent} for specified pin and state. Just convenience factory method.
     * Throws exception if given pin is not provisioned.
     * <p><b>PRE-conditions:</b> non-null {@code pin}, non-null {@code state}
     * <br><b>POST-conditions:</b> non-null result
     * <br><b>Side-effects:</b> NONE
     * <br><b>Created on:</b> <i>5:11:43 AM Mar 7, 2017</i>
     * 
     * @param pin
     *            pin of the event
     * @param state
     *            state of the event
     * @return new digital pin state event
     */
    public final GpioPinDigitalStateChangeEvent digitalStateEvent(final Pin pin, final PinState state) {
        GpioPinDigital pinGpio = digitalOutPins.get(pin);
        if (pinGpio == null) {
            pinGpio = digitalInputPins.get(pin);
        }
        assert (pinGpio != null);
        return new GpioPinDigitalStateChangeEvent(RxGpio.instance(), pinGpio, state);
    }

}
