package com.microboxlabs.miot.calendar;

import com.microboxlabs.miot.calendar.service.SlotManagerExecutor;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Application entry point.
 *
 * Default (no args): starts the HTTP server normally.
 *
 * CLI mode (arg: "run-slots"): runs all active slot managers and exits.
 * Useful for Kubernetes CronJob deployments where the inner scheduler
 * is disabled:
 *
 *   # application.properties
 *   miot-calendar.slot-manager.cron=off
 *
 *   # K8s CronJob spec
 *   command: ["java", "-jar", "miot-calendar.jar", "run-slots"]
 *
 * Exit codes: 0 = success, 1 = one or more managers failed.
 */
@QuarkusMain
public class Main implements QuarkusApplication {

    private static final Logger LOG = Logger.getLogger(Main.class);

    @Inject
    SlotManagerExecutor slotManagerExecutor;

    @Override
    public int run(String... args) {
        if (args.length > 0 && "run-slots".equals(args[0])) {
            LOG.info("CLI mode: running all active slot managers");
            try {
                var results = slotManagerExecutor.runAll("CLI");
                long failed = results.stream()
                    .filter(r -> "FAILED".equals(r.status()))
                    .count();
                LOG.infof("CLI mode complete: %d manager(s) run, %d failed", results.size(), failed);
                return failed > 0 ? 1 : 0;
            } catch (Exception e) {
                LOG.errorf(e, "CLI mode failed with unexpected error");
                return 1;
            }
        }

        // Server mode — Quarkus has already started HTTP; just wait for shutdown signal
        Quarkus.waitForExit();
        return 0;
    }
}
