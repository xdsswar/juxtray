/**
 * End-to-end visual test for Juxtray: drives a real tray icon with real input
 * and saves screenshots. Run with {@code gradlew :visualtest:run}.
 *
 * <p>Besides the public API it reads a few internals by reflection; the
 * build opens those packages to this module at run time only.</p>
 */
module xss.it.juxtray.visualtest {
    requires xss.it.juxtray;
}
