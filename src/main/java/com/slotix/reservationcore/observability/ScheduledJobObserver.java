package com.slotix.reservationcore.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class ScheduledJobObserver {
    private final ObservationRegistry registry;

    public ScheduledJobObserver(ObservationRegistry registry) {
        this.registry = registry;
    }

    public void observe(String jobName, Runnable job) {
        Observation.createNotStarted("slotix.scheduled.job", registry)
            .lowCardinalityKeyValue("job.name", jobName)
            .observe(job);
    }

    public <T> T observe(String jobName, Supplier<T> job) {
        return Observation.createNotStarted("slotix.scheduled.job", registry)
            .lowCardinalityKeyValue("job.name", jobName)
            .observe(job);
    }
}
