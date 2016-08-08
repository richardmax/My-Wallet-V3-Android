package piuk.blockchain.android.di;

/**
 * Created by adambennett on 08/08/2016.
 *
 * A utils class for injecting mock modules during testing
 */
public class InjectorTestUtils {

    public static void initApplicationComponent(Injector injector, ApplicationModule applicationModule) {
        injector.initAppComponent(applicationModule);
    }

    public static void initApiComponent(Injector injector, ApiModule apiModule) {
        injector.initApiComponent(apiModule);
    }

}